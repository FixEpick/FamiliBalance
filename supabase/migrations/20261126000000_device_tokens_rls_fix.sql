-- Фикс IDOR в device_tokens.
--
-- Было: INSERT/UPDATE разрешены любому authenticated-пользователю
-- (with check (true) / using (true)). Любая сессия — включая анонимную
-- сессию чужого ребёнка — могла зарегистрировать СВОЙ fcm_token под
-- ЧУЖИМ user_id и role. Итог: пуши семьи (имена детей, детали квестов,
-- изменения баланса) доставлялись бы на устройство атакующего, а
-- UPDATE позволял подменить fcm_token существующей строки (перехват
-- уведомлений + DoS).
--
-- Стало: писать можно только за «себя» — то же правило, что уже
-- используется в DELETE-политике:
--   role='parent' -> user_id = auth.uid() (родительская сессия);
--   role='child'  -> user_id принадлежит family_profiles, у которого
--                    auth_user_id = auth.uid() (анонимная сессия
--                    устройства ребёнка).

drop policy if exists "device_tokens_insert_own" on public.device_tokens;
drop policy if exists "device_tokens_update_own" on public.device_tokens;

create policy "device_tokens_insert_own"
    on public.device_tokens for insert
    to authenticated
    with check (
        user_id = auth.uid()::text
        or user_id in (
            select user_id from public.family_profiles
            where auth_user_id = auth.uid()::text
        )
    );

create policy "device_tokens_update_own"
    on public.device_tokens for update
    to authenticated
    using (
        user_id = auth.uid()::text
        or user_id in (
            select user_id from public.family_profiles
            where auth_user_id = auth.uid()::text
        )
    )
    with check (
        user_id = auth.uid()::text
        or user_id in (
            select user_id from public.family_profiles
            where auth_user_id = auth.uid()::text
        )
    );
