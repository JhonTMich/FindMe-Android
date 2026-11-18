begin;
set local lock_timeout = '5s';

-- One snapshot distinguishes an empty shared schedule from a schedule we may not read.
create function public.shared_schedules_for_availability(contact_id uuid)
returns table (viewer_zone text, contact_zone text, viewer jsonb, contact jsonb)
language sql stable security invoker set search_path = '' as $$
    select me.timezone, person.timezone,
        coalesce((select jsonb_agg(to_jsonb(s) order by s.id)
            from public.personal_schedule s where s.user_id = me.id), '[]'::jsonb),
        coalesce((select jsonb_agg(to_jsonb(s) order by s.id)
            from public.personal_schedule s where s.user_id = person.id), '[]'::jsonb)
    from public.account_profiles me
    join public.account_profiles person on person.id = contact_id and person.id <> me.id
    join public.person_connections c on c.person_a = least(me.id, person.id) and c.person_b = greatest(me.id, person.id)
    left join public.connection_preferences p on p.connection_id = c.id and p.owner_id = me.id
    where me.id = (select auth.uid())
      and coalesce(p.share_schedule, true)
      and private.connection_shares(person.id, 'schedule');
$$;
revoke all on function public.shared_schedules_for_availability(uuid) from public, anon;
grant execute on function public.shared_schedules_for_availability(uuid) to authenticated;
commit;
