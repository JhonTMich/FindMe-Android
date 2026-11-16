-- Deployment proposal. Run once in a development project before enabling accounts.
-- Intentionally leaves legacy profiles/schedule/status_override untouched.
begin;
create schema if not exists private;
revoke all on schema private from public, anon;
grant usage on schema private to authenticated;

create table public.account_profiles (
    id uuid primary key references auth.users(id) on delete cascade,
    display_name text not null check (length(trim(display_name)) between 1 and 80),
    timezone text not null default 'America/Lima'
);
create table public.person_connections (
    id uuid primary key default gen_random_uuid(),
    person_a uuid not null references public.account_profiles(id) on delete cascade,
    person_b uuid not null references public.account_profiles(id) on delete cascade,
    created_at timestamptz not null default now(),
    check (person_a < person_b), unique(person_a, person_b)
);
create index connections_person_b on public.person_connections(person_b);
create table public.connection_invitations (
    owner_id uuid primary key references public.account_profiles(id) on delete cascade,
    code text not null unique check (code ~ '^[A-HJ-NP-Z2-9]{8}$' or code ~ '^[0-9a-f]{32}$'),
    expires_at timestamptz not null,
    check (expires_at <= now() + interval '25 hours')
);
create table public.personal_schedule (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.account_profiles(id) on delete cascade,
    day_of_week integer not null check (day_of_week between 1 and 7),
    start_time time not null, end_time time not null,
    title text not null check (length(trim(title)) between 1 and 120),
    location text not null default '' check (length(location) <= 160),
    category text not null default 'university' check (category in ('university','work','free')),
    modality text not null default 'presencial' check (modality in ('presencial','virtual')),
    check (end_time > start_time)
);
create index personal_schedule_owner_day on public.personal_schedule(user_id, day_of_week, start_time);
create table public.personal_status (
    id uuid primary key,
    user_id uuid not null unique references public.account_profiles(id) on delete cascade,
    is_active boolean not null default true,
    custom_location text not null check (length(trim(custom_location)) between 1 and 120),
    custom_message text,
    expires_at timestamptz not null,
    check (id = user_id)
);
create table public.quick_messages (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references public.account_profiles(id) on delete cascade,
    label text not null check (length(trim(label)) between 1 and 30),
    message text not null check (length(trim(message)) between 1 and 240)
);
create index quick_messages_owner on public.quick_messages(user_id);
create table public.connection_signals (
    id uuid primary key default gen_random_uuid(),
    sender_id uuid not null references public.account_profiles(id) on delete cascade,
    recipient_id uuid not null references public.account_profiles(id) on delete cascade,
    message text not null check (length(trim(message)) between 1 and 240),
    created_at timestamptz not null default now(),
    check(sender_id <> recipient_id)
);
create index signals_recipient_time on public.connection_signals(recipient_id, created_at desc);
create index signals_sender on public.connection_signals(sender_id);

alter table public.account_profiles enable row level security;
alter table public.person_connections enable row level security;
alter table public.connection_invitations enable row level security;
alter table public.personal_schedule enable row level security;
alter table public.personal_status enable row level security;
alter table public.quick_messages enable row level security;
alter table public.connection_signals enable row level security;

-- Connections can only be created through consuming an invitation atomically.
revoke all on public.account_profiles, public.person_connections, public.connection_invitations,
    public.personal_schedule, public.personal_status, public.quick_messages, public.connection_signals from anon, authenticated;
grant select, insert, update on public.account_profiles to authenticated;
grant select, delete on public.person_connections to authenticated;
grant select, insert, update, delete on public.connection_invitations,
    public.personal_schedule, public.personal_status, public.quick_messages to authenticated;
grant select, insert on public.connection_signals to authenticated;

create policy connections_read on public.person_connections for select to authenticated
    using ((select auth.uid()) in (person_a, person_b));
create policy connections_delete on public.person_connections for delete to authenticated
    using ((select auth.uid()) in (person_a, person_b));

create policy profiles_read on public.account_profiles for select to authenticated using (
    id = (select auth.uid()) or exists (select 1 from public.person_connections c
    where (c.person_a = auth.uid() and c.person_b = account_profiles.id)
       or (c.person_b = auth.uid() and c.person_a = account_profiles.id))
);
create policy profiles_insert on public.account_profiles for insert to authenticated with check (id = (select auth.uid()));
create policy profiles_update on public.account_profiles for update to authenticated
    using (id = (select auth.uid())) with check (id = (select auth.uid()));

create policy invitation_owner on public.connection_invitations for all to authenticated
    using (owner_id = (select auth.uid())) with check (owner_id = (select auth.uid()));

create policy schedule_read on public.personal_schedule for select to authenticated using (
    user_id = (select auth.uid()) or exists (select 1 from public.person_connections c
    where (c.person_a = auth.uid() and c.person_b = personal_schedule.user_id)
       or (c.person_b = auth.uid() and c.person_a = personal_schedule.user_id))
);
create policy schedule_insert on public.personal_schedule for insert to authenticated with check (user_id = (select auth.uid()));
create policy schedule_update on public.personal_schedule for update to authenticated
    using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy schedule_delete on public.personal_schedule for delete to authenticated using (user_id = (select auth.uid()));

create policy status_read on public.personal_status for select to authenticated using (
    user_id = (select auth.uid()) or exists (select 1 from public.person_connections c
    where (c.person_a = auth.uid() and c.person_b = personal_status.user_id)
       or (c.person_b = auth.uid() and c.person_a = personal_status.user_id))
);
create policy status_insert on public.personal_status for insert to authenticated with check (user_id = (select auth.uid()));
create policy status_update on public.personal_status for update to authenticated
    using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy status_delete on public.personal_status for delete to authenticated using (user_id = (select auth.uid()));

create policy quick_messages_owner on public.quick_messages for all to authenticated
    using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy signals_read on public.connection_signals for select to authenticated using (
    (select auth.uid()) in (sender_id, recipient_id) and exists (
    select 1 from public.person_connections c where c.person_a = least(sender_id, recipient_id)
    and c.person_b = greatest(sender_id, recipient_id))
);
create policy signals_insert on public.connection_signals for insert to authenticated with check (
    sender_id = (select auth.uid()) and exists (
    select 1 from public.person_connections c where c.person_a = least(sender_id, recipient_id)
    and c.person_b = greatest(sender_id, recipient_id))
);
-- The client cannot forge message timestamps.
revoke insert on public.connection_signals from authenticated;
grant insert(id, sender_id, recipient_id, message) on public.connection_signals to authenticated;

-- Privileged implementation is hidden from the Data API, with a pinned search path.
-- Bypass is needed only to consume another person's invitation and create the link.
create function private.accept_connection_invitation(invitation_code text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare caller uuid := auth.uid(); invitation public.connection_invitations; result uuid;
    normalized_code text := upper(regexp_replace(invitation_code, '[[:space:]-]', '', 'g'));
begin
    if caller is null then raise exception 'Authentication required'; end if;
    if not exists (select 1 from public.account_profiles where id = caller) then
        raise exception 'Create your profile first';
    end if;
    select * into invitation from public.connection_invitations
        where code = case when length(normalized_code) = 32 then lower(normalized_code) else normalized_code end
        and expires_at > now() for update;
    if not found then raise exception 'Invitation invalid or expired'; end if;
    if invitation.owner_id = caller then raise exception 'Cannot connect to yourself'; end if;
    insert into public.person_connections(person_a, person_b)
        values (least(caller, invitation.owner_id), greatest(caller, invitation.owner_id))
        on conflict (person_a, person_b) do update set person_a = excluded.person_a
        returning id into result;
    delete from public.connection_invitations where owner_id = invitation.owner_id;
    return result;
end;
$$;
revoke all on function private.accept_connection_invitation(text) from public, anon;
grant execute on function private.accept_connection_invitation(text) to authenticated;
create function public.accept_connection_invitation(invitation_code text) returns uuid
language sql security invoker set search_path = '' as $$
    select private.accept_connection_invitation(invitation_code);
$$;
revoke all on function public.accept_connection_invitation(text) from public, anon;
grant execute on function public.accept_connection_invitation(text) to authenticated;
commit;
