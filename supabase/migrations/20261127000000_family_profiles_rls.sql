-- ============================================================
-- FamiliBalance: RLS для family_profiles + защита клейма ребёнка
-- parent_user_id — uuid, поэтому сравнение БЕЗ ::text.
-- Один скрипт: выполнить целиком.
-- ============================================================

create extension if not exists pgcrypto;

-- ---------- 1. Таблица попыток клейма (клиентам недоступна) ----------
create table if not exists public.claim_attempts (
    uid          text        not null,
    child_id     text        not null,
    attempts     int         not null default 0,
    locked_until timestamptz,
    primary key (uid, child_id)
);

alter table public.claim_attempts enable row level security;
-- Политик нет → authenticated/anon не видят и не меняют таблицу.
-- Пишет только security-definer функция ниже.

-- ---------- 2. Включаем RLS на family_profiles ----------
alter table public.family_profiles enable row level security;

-- ---------- 3. Сносим ВСЕ существующие политики ----------
do $$
declare
    pol record;
begin
    for pol in
        select policyname
        from pg_policies
        where schemaname = 'public'
          and tablename = 'family_profiles'
    loop
        execute format(
            'drop policy if exists %I on public.family_profiles',
            pol.policyname
        );
    end loop;
end $$;

-- ---------- 4. SELECT: зеркало isRowVisibleToUser ----------
--   user_id = auth.uid()            — собственный профиль
--   parent_user_id = auth.uid()     — родитель → свои дети (uuid = uuid)
--   auth_user_id = auth.uid()       — устройство ребёнка → свой профиль
--   parent_user_id is null + parent_email = email из JWT — pending-инвайт
create policy "family_profiles_select_own"
    on public.family_profiles for select
    to authenticated
    using (
        user_id = auth.uid()::text
        or parent_user_id = auth.uid()
        or auth_user_id::text = auth.uid()::text
        or (
            parent_user_id is null
            and parent_email is not null
            and auth.jwt() ->> 'email' is not null
            and lower(parent_email) = lower(auth.jwt() ->> 'email')
        )
    );

-- ---------- 5. INSERT ----------
create policy "family_profiles_insert_own"
    on public.family_profiles for insert
    to authenticated
    with check (
        user_id = auth.uid()::text
        or parent_user_id = auth.uid()
        or auth_user_id::text = auth.uid()::text
    );

-- ---------- 6. UPDATE — строго, без лазеек ----------
create policy "family_profiles_update_own"
    on public.family_profiles for update
    to authenticated
    using (
        user_id = auth.uid()::text
        or parent_user_id = auth.uid()
        or auth_user_id::text = auth.uid()::text
    )
    with check (
        user_id = auth.uid()::text
        or parent_user_id = auth.uid()
        or auth_user_id::text = auth.uid()::text
    );

-- ---------- 7. DELETE ----------
create policy "family_profiles_delete_own"
    on public.family_profiles for delete
    to authenticated
    using (
        user_id = auth.uid()::text
        or parent_user_id = auth.uid()
    );

-- ---------- 8. RPC клейма с защитой от перебора PIN ----------
-- Вызывается клиентом ПОСЛЕ signInAnonymously, ДО первого upsert.
-- Проверяет PIN (salted SHA-256 / legacy) + parent_email,
-- лимитирует попытки: 5 неудач → блокировка 15 минут + pg_sleep на каждой.
create or replace function public.claim_child_profile(
    p_child_id     text,
    p_parent_email text,
    p_pin          text
) returns boolean
language plpgsql
security definer
set search_path = public
as $$
declare
    v_uid          text := auth.uid()::text;
    v_pin          text;
    v_parent_email text;
    v_salt         text;
    v_hash         text;
    v_ok           boolean;
    v_claimed      int;
    v_locked_until timestamptz;
begin
    if v_uid is null or p_pin is null or p_pin = '' then
        return false;
    end if;

    -- Блокировка после серии неудач.
    select locked_until into v_locked_until
      from public.claim_attempts
     where uid = v_uid and child_id = p_child_id;

    if v_locked_until is not null and v_locked_until > now() then
        perform pg_sleep(1.5);
        return false;
    end if;

    -- Читаем профиль: pinCode и parentEmail из state_json/колонки.
    select state_json::jsonb ->> 'pinCode',
           lower(coalesce(parent_email, state_json::jsonb ->> 'parentEmail', ''))
      into v_pin, v_parent_email
      from public.family_profiles
     where user_id = p_child_id
       and (auth_user_id::text is null or auth_user_id::text = ''
            or auth_user_id::text = v_uid);

    -- PIN должен быть установлен; email родителя — совпасть.
    v_ok := v_pin is not null and v_pin <> ''
        and (p_parent_email is null or p_parent_email = ''
             or v_parent_email = lower(p_parent_email));

    -- Проверка PIN: "salt:sha256hex" | legacy sha256hex | legacy plaintext.
    if v_ok then
        if position(':' in v_pin) > 0 then
            v_salt := split_part(v_pin, ':', 1);
            v_hash := split_part(v_pin, ':', 2);
            v_ok := encode(digest(v_salt || ':' || p_pin, 'sha256'), 'hex') = v_hash;
        else
            v_ok := encode(digest(p_pin, 'sha256'), 'hex') = v_pin
                or p_pin = v_pin;
        end if;
    end if;

    if not v_ok then
        perform pg_sleep(1.5);
        insert into public.claim_attempts (uid, child_id, attempts)
        values (v_uid, p_child_id, 1)
        on conflict (uid, child_id) do update
          set attempts = claim_attempts.attempts + 1,
              locked_until = case
                  when claim_attempts.attempts + 1 >= 5
                  then now() + interval '15 minutes'
                  else claim_attempts.locked_until
              end;
        return false;
    end if;

    -- Успех: клеймим устройство и сбрасываем счётчик.
    update public.family_profiles
       set auth_user_id = v_uid
     where user_id = p_child_id
       and (auth_user_id::text is null or auth_user_id::text = ''
            or auth_user_id::text = v_uid);

    get diagnostics v_claimed = row_count;
    if v_claimed > 0 then
        delete from public.claim_attempts
         where uid = v_uid and child_id = p_child_id;
    end if;
    return v_claimed > 0;
end;
$$;

revoke all on function public.claim_child_profile(text, text, text) from public;
grant execute on function public.claim_child_profile(text, text, text) to authenticated;
