-- Серверный timestamp для last-writer-wins.
-- Клиентские часы (stateUpdatedAt в state_json) расходятся между устройствами
-- (эмуляторы, ручная установка времени) и ломали синхронизацию: «свежий по
-- времени, но старый по содержимому» стейт перезаписывал актуальный.
-- updated_at ставится триггером на сервере — единые часы для всех клиентов.

alter table public.family_profiles
    add column if not exists updated_at timestamptz not null default now();

create or replace function public.set_family_profiles_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

drop trigger if exists trg_family_profiles_updated_at on public.family_profiles;

create trigger trg_family_profiles_updated_at
    before insert or update on public.family_profiles
    for each row
    execute function public.set_family_profiles_updated_at();
