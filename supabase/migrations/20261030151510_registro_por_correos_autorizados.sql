-- Self-service registration. Administrators authorize only an email address.
begin;
create table public.signup_allowed_emails (
    email text primary key check (email = lower(trim(email)) and email ~ '^[^[:space:]@]+@[^[:space:]@]+\.[^[:space:]@]+$'),
    enabled boolean not null default true,
    expires_at timestamptz not null default now() + interval '7 days',
    used_at timestamptz,
    registered_user_id uuid references auth.users(id) on delete set null,
    created_at timestamptz not null default now()
);
alter table public.signup_allowed_emails enable row level security;
revoke all on public.signup_allowed_emails from public, anon, authenticated;
grant select on public.signup_allowed_emails to service_role, supabase_auth_admin;
grant update (used_at, registered_user_id) on public.signup_allowed_emails to supabase_auth_admin;
create policy signup_auth_read on public.signup_allowed_emails for select to supabase_auth_admin using (true);
create policy signup_auth_consume on public.signup_allowed_emails for update to supabase_auth_admin using (true) with check (true);

grant usage on schema public, private to supabase_auth_admin;
grant insert on public.account_profiles to supabase_auth_admin;
create policy profiles_auth_create on public.account_profiles for insert to supabase_auth_admin with check (true);

-- Enforce the gate at the actual Auth INSERT, including direct /auth/v1/signup calls.
-- INVOKER: only the Auth role receives the narrow table privileges above.
create function private.validate_allowed_signup() returns trigger
language plpgsql security invoker set search_path = '' as $$
declare authorized_email text;
    chosen_username text := lower(trim(new.raw_user_meta_data->>'username'));
    chosen_name text := trim(new.raw_user_meta_data->>'display_name');
    chosen_zone text := new.raw_user_meta_data->>'timezone';
begin
    select email into authorized_email from public.signup_allowed_emails
        where email = lower(trim(new.email)) and enabled and expires_at > now() and used_at is null
        for update;
    if authorized_email is null then raise exception 'Registration not authorized'; end if;
    if chosen_username is null or chosen_username !~ '^[a-z0-9_]{3,24}$'
       or chosen_name is null or length(chosen_name) not between 1 and 80 or lower(chosen_name) = 'mi cuenta'
       or jsonb_typeof(new.raw_user_meta_data->'username') <> 'string'
       or jsonb_typeof(new.raw_user_meta_data->'display_name') <> 'string'
       or chosen_zone is null or not exists (select 1 from pg_catalog.pg_timezone_names where name = chosen_zone)
    then raise exception 'Invalid registration profile'; end if;
    return new;
end;
$$;
create function private.create_registered_profile() returns trigger
language plpgsql security invoker set search_path = '' as $$
begin
    insert into public.account_profiles(id,display_name,username,timezone)
        values (new.id, trim(new.raw_user_meta_data->>'display_name'),
            lower(trim(new.raw_user_meta_data->>'username')), new.raw_user_meta_data->>'timezone');
    update public.signup_allowed_emails set used_at = now(), registered_user_id = new.id
        where email = lower(trim(new.email));
    return new;
end;
$$;
revoke all on function private.validate_allowed_signup(), private.create_registered_profile() from public, anon, authenticated;
grant execute on function private.validate_allowed_signup(), private.create_registered_profile() to supabase_auth_admin;
create trigger findme_validate_signup before insert on auth.users
    for each row execute function private.validate_allowed_signup();
create trigger findme_create_profile after insert on auth.users
    for each row execute function private.create_registered_profile();
commit;
