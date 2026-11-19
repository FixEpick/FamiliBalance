-- Таблица FCM-токенов устройств для пуш-уведомлений.
-- user_id: для ребёнка — family_profiles.user_id (id профиля),
--          для родителя — auth.uid() родительской сессии.
-- role:    "child" | "parent" — разделяет потоки уведомлений,
--          чтобы на одном устройстве роли не получали чужие пуши.

create table if not exists public.device_tokens (
    user_id     text        not null,
    role        text        not null check (role in ('child', 'parent')),
    fcm_token   text        not null,
    platform    text        not null default 'android',
    updated_at  timestamptz not null default now(),
    primary key (user_id, role, fcm_token)
);

create index if not exists device_tokens_lookup_idx
    on public.device_tokens (user_id, role);

alter table public.device_tokens enable row level security;

-- Клиент может регистрировать и обновлять свои токены.
-- Upsert (ON CONFLICT DO UPDATE) требует политик И для INSERT, И для UPDATE.
create policy "device_tokens_insert_own"
    on public.device_tokens for insert
    to authenticated
    with check (true);

create policy "device_tokens_update_own"
    on public.device_tokens for update
    to authenticated
    using (true)
    with check (true);

-- Удалять может владелец: родитель по auth.uid(), ребёнок — через
-- family_profiles.auth_user_id (анонимная сессия устройства ребёнка).
create policy "device_tokens_delete_own"
    on public.device_tokens for delete
    to authenticated
    using (
        user_id = auth.uid()::text
        or user_id in (
            select user_id from public.family_profiles
            where auth_user_id = auth.uid()::text
        )
    );

-- SELECT только для service_role (Edge Function send-push).
-- Политики для authenticated на select нет — токены не утекают клиентам.
