-- Server-side bounds the app already respects, so a modified client cannot exceed them.
begin;
set local lock_timeout = '5s';

-- Shared status: the app allows a 240-character message and at most 24 hours.
-- NOT VALID: existing rows are left alone; every new write is checked.
alter table public.personal_status
    add constraint personal_status_message_length
        check (custom_message is null or length(custom_message) <= 240) not valid,
    add constraint personal_status_max_lifetime
        check (expires_at <= now() + interval '25 hours') not valid;

-- Row caps and message rate. Custom SQLSTATEs let the app explain the refusal:
-- FM001 = stored item limit reached, FM002 = too many requests in a short time.
create function private.enforce_row_limits() returns trigger
language plpgsql security definer set search_path = '' as $$
declare total integer;
begin
    if tg_table_name = 'personal_schedule' then
        -- Upserts of an existing activity are edits, not new rows.
        if exists (select 1 from public.personal_schedule where id = new.id) then return new; end if;
        perform pg_advisory_xact_lock(hashtext('findme:schedule:' || new.user_id::text));
        select count(*) into total from public.personal_schedule where user_id = new.user_id;
        if total >= 200 then raise exception 'Schedule limit reached' using errcode = 'FM001'; end if;
    elsif tg_table_name = 'quick_messages' then
        perform pg_advisory_xact_lock(hashtext('findme:quick:' || new.user_id::text));
        select count(*) into total from public.quick_messages where user_id = new.user_id;
        if total >= 30 then raise exception 'Quick message limit reached' using errcode = 'FM001'; end if;
    elsif tg_table_name = 'connection_signals' then
        perform pg_advisory_xact_lock(hashtext('findme:signal:' || new.sender_id::text));
        select count(*) into total from public.connection_signals
            where sender_id = new.sender_id and recipient_id = new.recipient_id
              and created_at > now() - interval '10 minutes';
        if total >= 30 then raise exception 'Too many messages to this person' using errcode = 'FM002'; end if;
        select count(*) into total from public.connection_signals
            where sender_id = new.sender_id and created_at > now() - interval '1 hour';
        if total >= 120 then raise exception 'Too many messages' using errcode = 'FM002'; end if;
    end if;
    return new;
end;
$$;
revoke all on function private.enforce_row_limits() from public, anon, authenticated;
create trigger findme_limit_schedule before insert on public.personal_schedule
    for each row execute function private.enforce_row_limits();
create trigger findme_limit_quick_messages before insert on public.quick_messages
    for each row execute function private.enforce_row_limits();
create trigger findme_limit_signals before insert on public.connection_signals
    for each row execute function private.enforce_row_limits();

-- Profiles could store any text as time zone; registration already validates it.
create function private.validate_profile_timezone() returns trigger
language plpgsql security definer set search_path = '' as $$
begin
    if not exists (select 1 from pg_catalog.pg_timezone_names where name = new.timezone) then
        raise exception 'Invalid time zone' using errcode = 'FM003';
    end if;
    return new;
end;
$$;
revoke all on function private.validate_profile_timezone() from public, anon, authenticated;
create trigger findme_validate_timezone before insert or update of timezone on public.account_profiles
    for each row execute function private.validate_profile_timezone();

-- Invitation codes: at most ten attempts per person every 15 minutes. An unknown or expired
-- code returns NULL instead of raising, because an exception would roll back the counter.
create or replace function private.accept_connection_invitation(invitation_code text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare caller uuid := auth.uid(); invitation public.connection_invitations; result uuid; tries integer;
    normalized_code text := upper(regexp_replace(coalesce(invitation_code, ''), '[[:space:]-]', '', 'g'));
begin
    if caller is null then raise exception 'Authentication required'; end if;
    if not exists (select 1 from public.account_profiles where id = caller) then
        raise exception 'Create your profile first';
    end if;
    delete from private.username_login_limits where expires_at < now();
    insert into private.username_login_limits as l values ('invite:' || caller::text, 1, now() + interval '15 minutes')
    on conflict (key) do update set attempts = l.attempts + 1 returning attempts into tries;
    if tries > 10 then raise exception 'Too many invitation attempts' using errcode = 'FM002'; end if;
    select * into invitation from public.connection_invitations
        where code = case when length(normalized_code) = 32 then lower(normalized_code) else normalized_code end
        and expires_at > now() for update;
    if not found then return null; end if;
    if invitation.owner_id = caller then raise exception 'Cannot connect to yourself'; end if;
    insert into public.person_connections(person_a, person_b)
        values (least(caller, invitation.owner_id), greatest(caller, invitation.owner_id))
        on conflict (person_a, person_b) do update set person_a = excluded.person_a
        returning id into result;
    delete from public.connection_invitations where owner_id = invitation.owner_id;
    return result;
end;
$$;
commit;
