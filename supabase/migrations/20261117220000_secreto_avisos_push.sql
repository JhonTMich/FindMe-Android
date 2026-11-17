-- Shared secret between the database (pg_net) and the send-push Edge Function, generated inside the
-- database so it never appears in the repository, the migration history or a terminal. The function
-- reads it with the service role through push_webhook_secret(). Also stores the function URL.
-- Where Vault does not exist (local test databases) nothing is created and no push is ever sent.
do $$
begin
    if to_regclass('vault.secrets') is null then return; end if;
    if not exists (select 1 from vault.secrets where name = 'near_push_secret') then
        perform vault.create_secret(encode(extensions.gen_random_bytes(32), 'hex'), 'near_push_secret',
            'Shared secret: pg_net -> send-push');
    end if;
    if not exists (select 1 from vault.secrets where name = 'near_push_url') then
        perform vault.create_secret('https://ymqsgxmpqushzghkoncg.supabase.co/functions/v1/send-push', 'near_push_url',
            'send-push Edge Function URL');
    end if;
end;
$$;

create function public.push_webhook_secret() returns text
language plpgsql stable security definer set search_path = '' as $$
begin
    if to_regclass('vault.decrypted_secrets') is null then return null; end if;
    return (select decrypted_secret from vault.decrypted_secrets where name = 'near_push_secret');
end;
$$;
revoke all on function public.push_webhook_secret() from public, anon, authenticated;
grant execute on function public.push_webhook_secret() to service_role;
