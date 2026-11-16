-- Short invitations, preserving all existing codes and connections.
begin;
alter table public.connection_invitations drop constraint connection_invitations_code_check;
alter table public.connection_invitations add constraint connection_invitations_code_check
    check (code ~ '^[A-HJ-NP-Z2-9]{8}$' or code ~ '^[0-9a-f]{32}$');
create or replace function private.accept_connection_invitation(invitation_code text) returns uuid
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

commit;
