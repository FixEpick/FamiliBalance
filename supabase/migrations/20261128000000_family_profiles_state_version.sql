-- ============================================================
-- FamiliBalance: серверный versioned write для family_profiles
--
-- До этого LWW был только клиентским: select → сравнение → upsert.
-- Между проверкой и upsert другой клиент мог записать — upsert
-- безусловно затирал чужую правку.
--
-- Теперь: колонка state_version (bigint) + RPC push_profile_state,
-- который атомарно (FOR UPDATE) принимает запись только если
-- p_state_version строго больше текущей серверной версии.
-- Один скрипт: выполнить целиком.
-- ============================================================

-- ---------- 1. Колонка версии ----------
alter table public.family_profiles
    add column if not exists state_version bigint not null default 0;

-- Бэкфилл из state_json->>'stateVersion' (там уже лежит тот же счётчик).
-- Строки без валидного stateVersion остаются 0 — первый push их обновит.
update public.family_profiles
   set state_version = coalesce((state_json::jsonb ->> 'stateVersion')::bigint, 0)
 where state_version = 0
   and state_json::jsonb ? 'stateVersion';

-- ---------- 2. RPC атомарной записи ----------
-- Возвращает setof из одной строки { status }:
--   'inserted' — строки не было, вставили;
--   'updated'  — версия новее, обновили;
--   'conflict' — серверная версия >= присланной, запись отклонена
--                (клиент должен стянуть remote и применить LWW локально).
--
-- security invoker: действуют те же RLS-политики family_profiles,
-- что и для прямого upsert — отдельной дыры не создаём.
create or replace function public.push_profile_state(
    p_user_id         text,
    p_state_json      text,
    p_state_version   bigint,
    p_parent_user_id  uuid  default null,
    p_parent_email    text  default null,
    p_auth_user_id    text  default null,
    p_status          text  default null
) returns table (status text)
language plpgsql
security invoker
set search_path = public
as $$
declare
    v_current bigint;
begin
    -- Блокируем строку до конца транзакции: параллельные push'и
    -- сериализуются, гонки «прочитал старое → записал поверх» нет.
    select fp.state_version into v_current
      from public.family_profiles fp
     where fp.user_id = p_user_id
     for update;

    if not found then
        insert into public.family_profiles
            (user_id, state_json, parent_user_id, parent_email,
             auth_user_id, status, state_version)
        values
            (p_user_id, p_state_json, p_parent_user_id, p_parent_email,
             p_auth_user_id, coalesce(p_status, 'active'), p_state_version);
        return query select 'inserted'::text;
        return;
    end if;

    -- Монотонность: принимаем только строго более новую версию.
    -- Равная версия — либо retry после потерянного ответа (тогда
    -- контент идентичен и conflict безвреден: клиент сделает pull),
    -- либо коллизия двух устройств — побеждает уже записанная.
    if p_state_version <= v_current then
        return query select 'conflict'::text;
        return;
    end if;

    update public.family_profiles fp
       set state_json      = p_state_json,
           parent_user_id  = coalesce(p_parent_user_id, fp.parent_user_id),
           parent_email    = coalesce(p_parent_email,   fp.parent_email),
           auth_user_id    = coalesce(p_auth_user_id,   fp.auth_user_id),
           status          = coalesce(p_status,         fp.status),
           state_version   = p_state_version
     where fp.user_id = p_user_id;

    return query select 'updated'::text;
    return;
end;
$$;

revoke all on function public.push_profile_state(text, text, bigint, uuid, text, text, text) from public;
grant execute on function public.push_profile_state(text, text, bigint, uuid, text, text, text) to authenticated;
