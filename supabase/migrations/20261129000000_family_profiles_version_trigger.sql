-- ============================================================
-- FamiliBalance: обязательный version check для family_profiles
--
-- RPC push_profile_state уже проверяет state_version, но прямой
-- upsert/update в таблицу оставался разрешённым RLS — старые клиенты
-- и fallback-путь обходили проверку.
--
-- Триггер делает проверку обязательной для ЛЮБОЙ записи:
--  - state_version всегда синхронизируется с state_json->>'stateVersion';
--  - UPDATE с версией <= текущей отклоняется (кроме записей,
--    не меняющих state_json — служебные update'ы вроде claim_child_profile).
--
-- Один скрипт: выполнить целиком.
-- ============================================================

create or replace function public.enforce_family_profiles_version()
returns trigger
language plpgsql
as $$
declare
    v_new_version bigint;
    v_old_version bigint;
begin
    -- Служебные UPDATE без смены state_json (claim_child_profile и т.п.)
    -- не трогаем — версию проверять не у чего.
    if tg_op = 'UPDATE' and new.state_json is not distinct from old.state_json then
        return new;
    end if;

    -- Версия из state_json; у legacy-записей поля может не быть → 0.
    begin
        v_new_version := coalesce((new.state_json::jsonb ->> 'stateVersion')::bigint, 0);
    exception when others then
        v_new_version := 0;
    end;

    if tg_op = 'UPDATE' then
        v_old_version := old.state_version;
        -- Жёсткая проверка: обе версии известны и новая не больше —
        -- попытка перезаписать более свежие данные (гонка upsert).
        if v_new_version > 0 and v_old_version > 0 and v_new_version <= v_old_version then
            raise exception 'stale_state_version: % <= %', v_new_version, v_old_version;
        end if;
        new.state_version := greatest(v_new_version, v_old_version);
    else
        new.state_version := v_new_version;
    end if;

    return new;
end;
$$;

drop trigger if exists trg_family_profiles_version on public.family_profiles;

create trigger trg_family_profiles_version
    before insert or update on public.family_profiles
    for each row
    execute function public.enforce_family_profiles_version();
