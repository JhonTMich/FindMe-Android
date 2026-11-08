begin;

set local lock_timeout = '5s';
create extension if not exists btree_gist with schema extensions;
set local search_path = pg_catalog, extensions, public;

-- Work occupies slot 1, virtual classes slot 0, and all other activities both.
-- Half-open time ranges allow one activity to start exactly when another ends.
alter table public.personal_schedule
    add constraint personal_schedule_no_conflicting_overlap
    exclude using gist (
        user_id with =,
        day_of_week with =,
        tsrange(date '2000-01-01' + start_time, date '2000-01-01' + end_time, '[)') with &&,
        int4range(
            case when category = 'work' then 1 else 0 end,
            case when category = 'university' and modality = 'virtual' then 1 else 2 end,
            '[)'
        ) with &&
    );

commit;
