-- Push notifications through Firebase Cloud Messaging.
--
-- Each device registers its FCM token for the signed-in account. Database events (a new message,
-- a status shared or removed) and a cron job (activities starting, statuses expiring) ask the
-- `send-push` Edge Function to notify the people who can see that change. The audience follows the
-- same rules as the app: the owner must share that kind of information with the connection, and the
-- viewer must not have silenced the owner.
--
-- The function URL and a shared secret live in Supabase Vault (names `near_push_url` and
-- `near_push_secret`); until both exist nothing is sent, so this migration is safe to apply first.
-- The pg_net/pg_cron part (extensions and the per-minute job) is in ..._programar_avisos_push.sql.
begin;
set local lock_timeout = '5s';

-- Device tokens. Only reachable through the two functions below and the service role.
create table public.push_tokens (
    token text primary key check (length(token) between 20 and 4096),
    user_id uuid not null references public.account_profiles(id) on delete cascade,
    updated_at timestamptz not null default now()
);
create index push_tokens_user on public.push_tokens(user_id);
alter table public.push_tokens enable row level security;
revoke all on public.push_tokens from anon, authenticated;

-- A token belongs to the last account that signed in on that device; each account keeps at most
-- ten devices (the oldest are dropped).
create function public.register_push_token(push_token text) returns void
language plpgsql security definer set search_path = '' as $$
declare
    caller uuid := auth.uid();
begin
    if caller is null then raise exception 'not signed in' using errcode = '42501'; end if;
    insert into public.push_tokens(token, user_id, updated_at) values (push_token, caller, now())
    on conflict (token) do update set user_id = excluded.user_id, updated_at = now();
    delete from public.push_tokens where user_id = caller and token in (
        select token from public.push_tokens where user_id = caller order by updated_at desc offset 10);
end;
$$;
create function public.unregister_push_token(push_token text) returns void
language sql security definer set search_path = '' as $$
    delete from public.push_tokens where token = push_token and user_id = auth.uid();
$$;
revoke all on function public.register_push_token(text), public.unregister_push_token(text) from public, anon;
grant execute on function public.register_push_token(text), public.unregister_push_token(text) to authenticated;

-- People who should hear about `owner`'s schedule or status: connected, shared with, not silenced.
create function private.push_audience(owner uuid, kind text) returns uuid[]
language sql stable security definer set search_path = '' as $$
    select coalesce(array_agg(viewer), '{}') from (
        select case when c.person_a = owner then c.person_b else c.person_a end as viewer, c.id
        from public.person_connections c
        left join public.connection_preferences mine on mine.connection_id = c.id and mine.owner_id = owner
        where owner in (c.person_a, c.person_b)
          and case kind when 'schedule' then coalesce(mine.share_schedule, true)
                        when 'status' then coalesce(mine.share_status, true)
                        else true end
    ) v
    where not exists (select 1 from public.connection_preferences theirs
                      where theirs.connection_id = v.id and theirs.owner_id = v.viewer and theirs.muted);
$$;

-- Hands one notification to the Edge Function (asynchronously, through pg_net).
create function private.queue_push(kind text, recipients uuid[], payload jsonb) returns void
language plpgsql security definer set search_path = '' as $$
declare
    url text;
    secret text;
begin
    if recipients is null or cardinality(recipients) = 0 then return; end if;
    -- Without Vault or pg_net (a local test database) there is nowhere to send to.
    if to_regclass('vault.decrypted_secrets') is null or to_regnamespace('net') is null then return; end if;
    select decrypted_secret into url from vault.decrypted_secrets where name = 'near_push_url';
    select decrypted_secret into secret from vault.decrypted_secrets where name = 'near_push_secret';
    if url is null or secret is null then return; end if;
    perform net.http_post(
        url := url,
        body := jsonb_build_object('type', kind, 'recipients', to_jsonb(recipients), 'data', payload),
        headers := jsonb_build_object('Content-Type', 'application/json', 'x-push-secret', secret),
        timeout_milliseconds := 5000);
end;
$$;
revoke all on function private.push_audience(uuid, text), private.queue_push(text, uuid[], jsonb) from public, anon, authenticated;

-- New message: only its recipient, unless they silenced the sender.
create function private.push_on_signal() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    if not exists (select 1 from public.person_connections c
                   join public.connection_preferences p on p.connection_id = c.id and p.owner_id = new.recipient_id
                   where c.person_a = least(new.sender_id, new.recipient_id)
                     and c.person_b = greatest(new.sender_id, new.recipient_id) and p.muted) then
        perform private.queue_push('message', array[new.recipient_id], jsonb_build_object(
            'id', new.id, 'sender_id', new.sender_id, 'message', new.message,
            'name', (select display_name from public.account_profiles where id = new.sender_id)));
    end if;
    return null;
end;
$$;
create trigger near_push_signal after insert on public.connection_signals
    for each row execute function private.push_on_signal();

-- A status shared or changed, and a status removed.
create function private.push_on_status() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
    owner uuid := coalesce(new.user_id, old.user_id);
    who text := (select display_name from public.account_profiles where id = owner);
begin
    if tg_op <> 'DELETE' and new.is_active and new.expires_at > now() then
        if tg_op = 'INSERT' or not old.is_active or old.custom_location is distinct from new.custom_location
           or old.custom_message is distinct from new.custom_message then
            perform private.queue_push('status', private.push_audience(owner, 'status'), jsonb_build_object(
                'user_id', owner, 'name', who, 'location', new.custom_location, 'message', new.custom_message));
        end if;
    elsif tg_op = 'DELETE' or (old.is_active and not new.is_active) then
        perform private.queue_push('status_end', private.push_audience(owner, 'status'),
            jsonb_build_object('user_id', owner, 'name', who));
    end if;
    return null;
end;
$$;
create trigger near_push_status after insert or update or delete on public.personal_status
    for each row execute function private.push_on_status();
revoke all on function private.push_on_signal(), private.push_on_status() from public, anon, authenticated;

-- Every minute: activities starting now (in each owner's time zone, unless a shared status takes
-- priority) and statuses that expired during the last minute.
create function private.push_due_events() returns void
language plpgsql security definer set search_path = '' as $$
declare
    r record;
begin
    for r in
        select s.id, s.user_id, s.title, s.location, p.display_name
        from public.personal_schedule s
        join public.account_profiles p on p.id = s.user_id
        join pg_catalog.pg_timezone_names z on z.name = p.timezone
        cross join lateral (select date_trunc('minute', now() at time zone p.timezone) as local_minute) l
        where s.day_of_week = extract(isodow from l.local_minute)::int
          and to_char(s.start_time, 'HH24:MI') = to_char(l.local_minute, 'HH24:MI')
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
$$;
revoke all on function private.push_due_events() from public, anon, authenticated;

commit;
