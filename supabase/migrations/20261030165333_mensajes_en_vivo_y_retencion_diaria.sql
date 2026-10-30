begin;
create index signals_expiry on public.connection_signals(created_at);
drop policy signals_read on public.connection_signals;
create policy signals_read on public.connection_signals for select to authenticated using (
    created_at > now() - interval '24 hours'
    and (select auth.uid()) in (sender_id, recipient_id)
    and exists (select 1 from public.person_connections c
        where c.person_a = least(sender_id, recipient_id) and c.person_b = greatest(sender_id, recipient_id))
);
create function private.purge_expired_signals() returns bigint
language plpgsql security invoker set search_path = '' as $$
declare removed bigint;
begin
    delete from public.connection_signals where created_at <= now() - interval '24 hours';
    get diagnostics removed = row_count;
    return removed;
end;
$$;
revoke all on function private.purge_expired_signals() from public, anon, authenticated;
grant execute on function private.purge_expired_signals() to service_role;
grant usage on schema private to service_role;
grant select, delete on public.connection_signals to service_role;
-- RLS still controls which rows each subscriber can see.
do $$ declare source text;
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime') then
        foreach source in array array['connection_signals','personal_schedule','personal_status','person_connections','account_profiles','quick_messages'] loop
            if not exists (select 1 from pg_publication_tables where pubname='supabase_realtime' and schemaname='public' and tablename=source) then
                execute format('alter publication supabase_realtime add table public.%I', source);
            end if;
        end loop;
    end if;
end;
$$;
commit;
