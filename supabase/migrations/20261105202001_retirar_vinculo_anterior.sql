begin;
set local lock_timeout = '5s';

-- The legacy family mode is retired. Never delete tables with existing data.
do $$
declare
    legacy_table regclass;
    has_rows boolean;
begin
    for legacy_table in
        select oid from pg_class
        where oid in (
            to_regclass('public.profiles'),
            to_regclass('public.schedule'),
            to_regclass('public.status_override')
        )
        order by oid
    loop
        execute format('lock table %s in access exclusive mode', legacy_table);
        execute format('select exists (select 1 from %s)', legacy_table) into has_rows;
        if has_rows then
            raise exception 'Legacy table % is not empty', legacy_table;
        end if;
    end loop;
end
$$;

drop table if exists public.schedule, public.status_override, public.profiles restrict;

create index if not exists signup_allowed_emails_registered_user_id_idx
    on public.signup_allowed_emails (registered_user_id);

commit;
