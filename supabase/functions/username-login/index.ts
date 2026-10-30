import { createLoginHandler } from './handler.mjs';

// This endpoint authenticates the supplied username/password itself, before a JWT exists.
Deno.serve(createLoginHandler({
  url: Deno.env.get('SUPABASE_URL'),
  serviceKey: Deno.env.get('SUPABASE_SERVICE_ROLE_KEY'),
  anonKey: Deno.env.get('SUPABASE_ANON_KEY'),
}));
