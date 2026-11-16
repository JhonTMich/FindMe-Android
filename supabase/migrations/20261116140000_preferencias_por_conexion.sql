-- Per-connection choices and inbox actions.
-- Each person keeps their own row per connection: a private nickname, whether to share
-- schedule and status with that person, and whether to silence that person's alerts.
-- A missing row means the defaults (share both, no nickname, not muted), so existing
-- connections behave exactly as before.
begin;
set local lock_timeout = '5s';

create table public.connection_preferences (
    connection_id uuid not null references public.person_connections(id) on delete cascade,
    owner_id uuid not null references public.account_profiles(id) on delete cascade,
    nickname text check (nickname is null or length(trim(nickname)) between 1 and 40),
    share_schedule boolean not null default true,
    share_status boolean not null default true,
    muted boolean not null default false,
    updated_at timestamptz not null default now(),
    primary key (connection_id, owner_id)
);
create index connection_preferences_owner on public.connection_preferences(owner_id);
alter table public.connection_preferences enable row level security;
revoke all on public.connection_preferences from anon, authenticated;
grant select, insert, update, delete on public.connection_preferences to authenticated;

-- Only the owner sees or changes their side, and only for a connection they belong to.
create policy preferences_owner on public.connection_preferences for all to authenticated
    using (owner_id = (select auth.uid()))
    with check (owner_id = (select auth.uid()) and exists (
        select 1 from public.person_connections c
        where c.id = connection_id and (select auth.uid()) in (c.person_a, c.person_b)));

-- The viewer cannot read the owner's preference row, so the read policies ask a
-- definer function. It only answers for the caller and never exposes the row itself.
create function private.connection_shares(target uuid, kind text) returns boolean
language sql stable security definer set search_path = '' as $$
    select auth.uid() is not null and target <> auth.uid() and exists (
        select 1 from public.person_connections c
        left join public.connection_preferences p on p.connection_id = c.id and p.owner_id = target
        where c.person_a = least(target, auth.uid()) and c.person_b = greatest(target, auth.uid())
          and case kind when 'schedule' then coalesce(p.share_schedule, true)
                        when 'status' then coalesce(p.share_status, true)
                        else false end);
$$;
revoke all on function private.connection_shares(uuid, text) from public, anon;
grant execute on function private.connection_shares(uuid, text) to authenticated;

drop policy schedule_read on public.personal_schedule;
create policy schedule_read on public.personal_schedule for select to authenticated using (
    user_id = (select auth.uid()) or private.connection_shares(user_id, 'schedule'));
drop policy status_read on public.personal_status;
create policy status_read on public.personal_status for select to authenticated using (
    user_id = (select auth.uid()) or private.connection_shares(user_id, 'status'));

-- Received messages: the recipient may mark them as read or delete them. Nobody else can.
alter table public.connection_signals add column read_at timestamptz;
grant update (read_at), delete on public.connection_signals to authenticated;
create policy signals_mark_read on public.connection_signals for update to authenticated
    using (recipient_id = (select auth.uid())) with check (recipient_id = (select auth.uid()));
create policy signals_delete on public.connection_signals for delete to authenticated
    using (recipient_id = (select auth.uid()));

-- The server decides the read time; marking twice keeps the first time.
create function private.stamp_signal_read() returns trigger
language plpgsql security invoker set search_path = '' as $$
begin
    new.read_at := case when new.read_at is null then null else coalesce(old.read_at, now()) end;
    return new;
end;
$$;
revoke all on function private.stamp_signal_read() from public, anon, authenticated;
create trigger findme_stamp_signal_read before update of read_at on public.connection_signals
    for each row execute function private.stamp_signal_read();

do $$
begin
    if exists (select 1 from pg_publication where pubname = 'supabase_realtime')
       and not exists (select 1 from pg_publication_tables where pubname = 'supabase_realtime'
                       and schemaname = 'public' and tablename = 'connection_preferences') then
        alter publication supabase_realtime add table public.connection_preferences;
    end if;
end;
$$;
commit;
