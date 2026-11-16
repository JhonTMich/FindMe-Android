-- Replaces the shared global counter, which let anyone block every login and signup.
-- Limits now apply per client network address (as an HMAC computed by the Edge Function),
-- per username on that client, and per username across all clients.
begin;
set local lock_timeout = '5s';

drop function public.consume_username_login_attempt(text);

create function public.consume_username_login_attempt(username_hash text, client_hash text default null)
returns boolean
language plpgsql security invoker set search_path = '' as $$
declare per_client integer; per_pair integer; per_user integer;
begin
    if username_hash !~ '^[0-9a-f]{64}$' or (client_hash is not null and client_hash !~ '^[0-9a-f]{64}$') then
        raise exception 'Invalid key';
    end if;
    delete from private.username_login_limits where expires_at < now();

    if client_hash is null then
        -- Edge Functions deployed before this migration send only the username key.
        insert into private.username_login_limits as l values (username_hash, 1, now() + interval '5 minutes')
        on conflict (key) do update set attempts = l.attempts + 1 returning attempts into per_user;
        return per_user <= 8;
    end if;

    -- A blocked client does not consume the budget of the accounts it targets.
    insert into private.username_login_limits as l values ('client:' || client_hash, 1, now() + interval '15 minutes')
    on conflict (key) do update set attempts = l.attempts + 1 returning attempts into per_client;
    if per_client > 40 then return false; end if;

    -- One client cannot lock an account for everyone else.
    insert into private.username_login_limits as l
        values ('pair:' || username_hash || ':' || client_hash, 1, now() + interval '5 minutes')
    on conflict (key) do update set attempts = l.attempts + 1 returning attempts into per_pair;
    if per_pair > 8 then return false; end if;

    -- Upper bound for guesses against one account spread over many clients.
    insert into private.username_login_limits as l values ('user:' || username_hash, 1, now() + interval '1 hour')
    on conflict (key) do update set attempts = l.attempts + 1 returning attempts into per_user;
    return per_user <= 50;
end;
$$;
revoke all on function public.consume_username_login_attempt(text, text) from public, anon, authenticated;
grant execute on function public.consume_username_login_attempt(text, text) to service_role;

delete from private.username_login_limits where key = 'global';
commit;
