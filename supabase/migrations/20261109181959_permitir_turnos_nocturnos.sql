begin;
set local lock_timeout = '5s';
set local search_path = pg_catalog, extensions, public;

alter table public.personal_schedule
    add column ends_next_day boolean not null default false,
    drop constraint personal_schedule_check,
    drop constraint personal_schedule_no_conflicting_overlap,
    add constraint personal_schedule_valid_duration check (
        start_time < time '24:00'
        and (
            (not ends_next_day and end_time > start_time)
            or (ends_next_day and end_time <= start_time)
        )
    );

-- Seconds since Monday 00:00. Split Sunday overnight shifts at the week boundary.
alter table public.personal_schedule add column weekly_span nummultirange
generated always as (
    nummultirange(
        numrange(
            (day_of_week - 1) * 86400 + extract(epoch from start_time),
            greatest(
                (day_of_week - 1) * 86400 + extract(epoch from start_time),
                least((day_of_week - 1) * 86400 + extract(epoch from end_time)
                    + case when ends_next_day then 86400 else 0 end, 604800)
            ),
            '[)'
        ),
        numrange(
            0,
            greatest((day_of_week - 1) * 86400 + extract(epoch from end_time)
                + case when ends_next_day then 86400 else 0 end - 604800, 0),
            '[)'
        )
    )
) stored;

alter table public.personal_schedule
    add constraint personal_schedule_no_conflicting_overlap
    exclude using gist (
        user_id with =,
        weekly_span with &&,
        int4range(
            case when category = 'work' then 1 else 0 end,
            case when category = 'university' and modality = 'virtual' then 1 else 2 end,
            '[)'
        ) with &&
    );
commit;
