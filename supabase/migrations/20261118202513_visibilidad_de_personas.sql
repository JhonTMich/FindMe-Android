begin;

-- Return the permissions the caller has, without exposing the contact's preference row.
create function public.incoming_connection_visibility()
returns table (person_id uuid, shares_schedule boolean, shares_status boolean)
language sql stable security invoker set search_path = '' as $$
    select person.id, private.connection_shares(person.id, 'schedule'),
        private.connection_shares(person.id, 'status')
    from public.person_connections c
    cross join lateral (values (
        case when c.person_a = (select auth.uid()) then c.person_b else c.person_a end
    )) person(id)
    where (select auth.uid()) in (c.person_a, c.person_b);
$$;
revoke all on function public.incoming_connection_visibility() from public, anon;
grant execute on function public.incoming_connection_visibility() to authenticated;

commit;
