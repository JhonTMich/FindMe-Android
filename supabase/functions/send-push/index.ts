import { createSendPushHandler } from './handler.mjs';

// Called by the database through pg_net with the shared secret (see the push notifications migrations).
// FCM_SERVICE_ACCOUNT is the Firebase service account JSON, an Edge Function secret never stored in the
// repository. The shared secret is generated in Vault by the database and read with the service role;
// PUSH_WEBHOOK_SECRET is optional and only overrides it.
Deno.serve(createSendPushHandler({
  url: Deno.env.get('SUPABASE_URL'),
  serviceKey: Deno.env.get('SUPABASE_SERVICE_ROLE_KEY'),
  secret: Deno.env.get('PUSH_WEBHOOK_SECRET'),
  serviceAccount: Deno.env.get('FCM_SERVICE_ACCOUNT'),
}));
