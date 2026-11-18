begin;
set local lock_timeout = '5s';

alter table public.personal_schedule
    add column occurrence_exceptions jsonb not null default '{}'::jsonb,
    add constraint schedule_exception_map check (jsonb_typeof(occurrence_exceptions) = 'object');

-- Keep the weekly upsert available to existing APKs. Only the validated RPC may write exceptions.
revoke insert, update on public.personal_schedule from authenticated;
grant insert (id, user_id, day_of_week, start_time, end_time, title, location, category, modality, ends_next_day),
      update (id, user_id, day_of_week, start_time, end_time, title, location, category, modality, ends_next_day)
    on public.personal_schedule to authenticated;

create function private.schedule_starts_on(s public.personal_schedule, local_date date)
returns table (original_date date, start_time time, end_time time, ends_next_day boolean)
language sql immutable security invoker set search_path = '' as $$
    select local_date, s.start_time, s.end_time, s.ends_next_day
    where s.day_of_week = extract(isodow from local_date)::int
      and not (s.occurrence_exceptions ? to_char(local_date, 'YYYY-MM-DD'))
    union all
    select e.key::date, (e.value->>'start_time')::time, (e.value->>'end_time')::time,
           (e.value->>'ends_next_day')::boolean
    from jsonb_each(s.occurrence_exceptions) e
    where e.value->>'date' = to_char(local_date, 'YYYY-MM-DD');
$$;
revoke all on function private.schedule_starts_on(public.personal_schedule, date) from public, anon, authenticated;

-- Obtain the same lock before weekly writes and before changing an individual occurrence.
create function private.lock_personal_schedule_write() returns trigger
language plpgsql security invoker set search_path = '' as $$
begin
    if auth.uid() is not null then
        perform pg_advisory_xact_lock(hashtext('findme:schedule:' || auth.uid()::text));
    end if;
    return null;
end;
$$;
revoke all on function private.lock_personal_schedule_write() from public, anon, authenticated;
create trigger findme_lock_personal_schedule before insert or update or delete on public.personal_schedule
    for each statement execute function private.lock_personal_schedule_write();

-- A later edit to the series must not create a clash with a previously moved occurrence.
create function private.check_weekly_against_moved_occurrences() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
    today date;
begin
    if tg_op = 'UPDATE' and
        row(old.user_id, old.day_of_week, old.start_time, old.end_time, old.ends_next_day, old.category, old.modality)
        is not distinct from
        row(new.user_id, new.day_of_week, new.start_time, new.end_time, new.ends_next_day, new.category, new.modality)
    then
        return new;
    end if;
    select (now() at time zone p.timezone)::date into today from public.account_profiles p where p.id = new.user_id;

    if exists (
        select 1 from public.personal_schedule other
        cross join lateral jsonb_each(other.occurrence_exceptions) e
        cross join lateral (
            select (e.value->>'date')::date as date,
                   (e.value->>'start_time')::time as starts,
                   (e.value->>'end_time')::time as ends,
                   (e.value->>'ends_next_day')::boolean as overnight
        ) moved
        cross join generate_series(-1, 1) offset_day
        cross join lateral private.schedule_starts_on(new, moved.date + offset_day) candidate
        where other.user_id = new.user_id and moved.date >= today - 1
          and (other.id <> new.id or e.key::date <> candidate.original_date)
          and not (
              (new.category = 'work' and other.category = 'university' and other.modality = 'virtual')
              or (other.category = 'work' and new.category = 'university' and new.modality = 'virtual')
          )
          and moved.date + moved.starts <
              (moved.date + offset_day + case when candidate.ends_next_day then 1 else 0 end) + candidate.end_time
          and (moved.date + offset_day) + candidate.start_time <
              (moved.date + case when moved.overnight then 1 else 0 end) + moved.ends
    ) then
        raise exception 'Weekly schedule overlaps a moved activity' using errcode = '23P01';
    end if;
    return new;
end;
$$;
revoke all on function private.check_weekly_against_moved_occurrences() from public, anon, authenticated;
create trigger findme_check_weekly_against_moved after insert or update on public.personal_schedule
    for each row execute function private.check_weekly_against_moved_occurrences();

create function private.change_schedule_occurrence(
    schedule_id uuid, original_date date, action text, new_date date,
    new_start time, new_end time, new_overnight boolean
) returns setof public.personal_schedule
language plpgsql security definer set search_path = '' as $$
declare
    owner uuid := auth.uid();
    activity public.personal_schedule;
    today date;
    original_key text := to_char(original_date, 'YYYY-MM-DD');
    exceptions jsonb;
    replacement jsonb;
    effective_date date;
    effective_start time;
    effective_end time;
    effective_overnight boolean;
begin
    if owner is null then raise insufficient_privilege; end if;
    perform pg_advisory_xact_lock(hashtext('findme:schedule:' || owner::text));
    select s.* into activity from public.personal_schedule s where s.id = schedule_id and s.user_id = owner for update;
    if not found then raise insufficient_privilege; end if;

    select (now() at time zone p.timezone)::date into today from public.account_profiles p where p.id = owner;
    if original_date is null or original_date > today + 366
       or (original_date < today and coalesce((activity.occurrence_exceptions->original_key->>'date')::date, original_date) < today)
       or (extract(isodow from original_date)::int <> activity.day_of_week
           and not (activity.occurrence_exceptions ? original_key))
       or action is null or action not in ('cancel', 'move', 'restore') then
        raise exception 'Invalid occurrence date or action' using errcode = 'FM004';
    end if;

    select coalesce(jsonb_object_agg(e.key, e.value), '{}'::jsonb) into exceptions
    from jsonb_each(activity.occurrence_exceptions) e
    where greatest(e.key::date, coalesce((e.value->>'date')::date, e.key::date)) >= today - 1;
    exceptions := exceptions - original_key;

    if action = 'move' then
        if new_date is null or new_date < today or new_date > today + 366
           or new_start is null or new_end is null or new_overnight is null
           or new_start >= time '24:00'
           or (case when new_overnight then new_end > new_start else new_end <= new_start end) then
            raise exception 'Invalid replacement date or hours' using errcode = 'FM004';
        end if;
        replacement := jsonb_build_object('date', to_char(new_date, 'YYYY-MM-DD'),
            'start_time', new_start::text, 'end_time', new_end::text, 'ends_next_day', new_overnight);
        effective_date := new_date;
        effective_start := new_start;
        effective_end := new_end;
        effective_overnight := new_overnight;
    elsif action = 'restore' and extract(isodow from original_date)::int = activity.day_of_week then
        effective_date := original_date;
        effective_start := activity.start_time;
        effective_end := activity.end_time;
        effective_overnight := activity.ends_next_day;
    elsif action = 'cancel' then
        replacement := jsonb_build_object('cancelled', true);
    end if;

    -- Check every effective occurrence that can overlap this one, including overnight shifts.
    if effective_date is not null and exists (
        select 1 from public.personal_schedule other
        cross join generate_series(-1, 1) offset_day
        cross join lateral private.schedule_starts_on(other, effective_date + offset_day) candidate
        where other.user_id = owner
          and (other.id <> activity.id or candidate.original_date <> change_schedule_occurrence.original_date)
          and not (
              (activity.category = 'work' and other.category = 'university' and other.modality = 'virtual')
              or (other.category = 'work' and activity.category = 'university' and activity.modality = 'virtual')
          )
          and effective_date + effective_start <
              (effective_date + offset_day + case when candidate.ends_next_day then 1 else 0 end) + candidate.end_time
          and (effective_date + offset_day) + candidate.start_time <
              (effective_date + case when effective_overnight then 1 else 0 end) + effective_end
    ) then
        raise exception 'Overlapping dated occurrence' using errcode = '23P01';
    end if;

    if replacement is not null then
        if (select count(*) from jsonb_object_keys(exceptions)) >= 32 then
            raise exception 'Occurrence exception limit reached' using errcode = 'FM001';
        end if;
        exceptions := exceptions || jsonb_build_object(original_key, replacement);
    end if;
    return query update public.personal_schedule s set occurrence_exceptions = exceptions
        where s.id = schedule_id and s.user_id = owner returning s.*;
end;
$$;
revoke all on function private.change_schedule_occurrence(uuid, date, text, date, time, time, boolean) from public, anon;
grant execute on function private.change_schedule_occurrence(uuid, date, text, date, time, time, boolean) to authenticated;

create function public.change_schedule_occurrence(
    schedule_id uuid, original_date date, action text, new_date date default null,
    new_start time default null, new_end time default null, new_overnight boolean default null
) returns setof public.personal_schedule
language sql security invoker set search_path = '' as $$
    select * from private.change_schedule_occurrence(schedule_id, original_date, action, new_date, new_start, new_end, new_overnight);
$$;
revoke all on function public.change_schedule_occurrence(uuid, date, text, date, time, time, boolean) from public, anon;
grant execute on function public.change_schedule_occurrence(uuid, date, text, date, time, time, boolean) to authenticated;

do $migration$
begin
    if to_regprocedure('private.push_due_events()') is null then return; end if;
    execute $function$
        create or replace function private.push_due_events() returns void
        language plpgsql security definer set search_path = '' as $body$
        declare
            r record;
        begin
            for r in
                select s.id, s.user_id, s.title, s.location, p.display_name
                from public.personal_schedule s
                join public.account_profiles p on p.id = s.user_id
                join pg_catalog.pg_timezone_names z on z.name = p.timezone
                cross join lateral (select date_trunc('minute', now() at time zone p.timezone) as local_minute) l
                cross join lateral private.schedule_starts_on(s, l.local_minute::date) o
                where l.local_minute::date + o.start_time >= l.local_minute
                  and l.local_minute::date + o.start_time < l.local_minute + interval '1 minute'
                  and not exists (select 1 from public.personal_status st
                                  where st.user_id = s.user_id and st.is_active and st.expires_at > now())
            loop
                perform private.queue_push('activity', private.push_audience(r.user_id, 'schedule'), jsonb_build_object(
                    'user_id', r.user_id, 'name', r.display_name, 'schedule_id', r.id,
                    'title', r.title, 'location', r.location));
            end loop;
            for r in
                select st.user_id, p.display_name from public.personal_status st
                join public.account_profiles p on p.id = st.user_id
                where st.is_active and st.expires_at <= now() and st.expires_at > now() - interval '1 minute'
            loop
                perform private.queue_push('status_end', private.push_audience(r.user_id, 'status'),
                    jsonb_build_object('user_id', r.user_id, 'name', r.display_name));
            end loop;
        end;
        $body$;
    $function$;
end;
$migration$;

commit;
