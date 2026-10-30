-- Additive upgrade: keeps existing accounts, sessions and schedules.
begin;
alter table public.account_profiles add column username text unique
    check (username ~ '^[a-z0-9_]{3,24}$');

-- Server-only counters. No emails, passwords or raw network addresses are stored.
create table private.username_login_limits (
    key text primary key,
    attempts integer not null,
    expires_at timestamptz not null
);
create index username_login_limits_expiry on private.username_login_limits(expires_at);
alter table private.username_login_limits enable row level security;
revoke all on private.username_login_limits from public, anon, authenticated;
grant usage on schema private to service_role;
grant select, insert, update, delete on private.username_login_limits to service_role;

-- SECURITY INVOKER and executable only by the Edge Function's service role.
create function public.consume_username_login_attempt(username_hash text) returns boolean
language plpgsql security invoker set search_path = '' as $$
declare total integer; per_user integer;
begin
    if username_hash !~ '^[0-9a-f]{64}$' then raise exception 'Invalid key'; end if;
    delete from private.username_login_limits where expires_at < now();
    insert into private.username_login_limits as l values ('global', 1, now() + interval '1 minute')
    on conflict (key) do update set attempts = l.attempts + 1 returning attempts into total;
    if total > 60 then return false; end if;
    insert into private.username_login_limits as l values (username_hash, 1, now() + interval '5 minutes')
    on conflict (key) do update set attempts = l.attempts + 1 returning attempts into per_user;
    return per_user <= 8;
end;
$$;
revoke all on function public.consume_username_login_attempt(text) from public, anon, authenticated;
grant execute on function public.consume_username_login_attempt(text) to service_role;
commit;
