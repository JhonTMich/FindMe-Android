import { createSendPushHandler } from './handler.mjs';

// Called by the database through pg_net with the shared secret (see the push notifications migration).
// FCM_SERVICE_ACCOUNT is the Firebase service account JSON; PUSH_WEBHOOK_SECRET must match the Vault
// secret `near_push_secret`. Both are Edge Function secrets, never stored in the repository.
Deno.serve(createSendPushHandler({
  url: Deno.env.get('SUPABASE_URL'),
  serviceKey: Deno.env.get('SUPABASE_SERVICE_ROLE_KEY'),
  secret: Deno.env.get('PUSH_WEBHOOK_SECRET'),
  serviceAccount: Deno.env.get('FCM_SERVICE_ACCOUNT'),
}));
