// npm.cmd install --prefix build/schema-check --no-save --ignore-scripts @electric-sql/pglite@0.3.14
// node scripts/test_push_notifications.mjs
// Checks who gets each push notification, with Vault and pg_net replaced by local stand-ins.
// Runs a real embedded PostgreSQL instance; never connects to the remote project.
import { PGlite } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/index.js';
import { btree_gist } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/contrib/btree_gist.js';
import { readFile, readdir } from 'node:fs/promises';
import assert from 'node:assert/strict';

const db = new PGlite({ extensions: { btree_gist } });
const a = '00000000-0000-0000-0000-000000000001';
const b = '00000000-0000-0000-0000-000000000002';
const c = '00000000-0000-0000-0000-000000000003';
const d = '00000000-0000-0000-0000-000000000004';
let checks = 0;
const check = (condition, name) => { assert.ok(condition, name); checks++; };
const rows = async (sql) => (await db.query(sql)).rows;
async function asUser(id) {
  await db.exec(`reset role; set role authenticated; select set_config('request.jwt.claim.sub', '${id}', false);`);
}
async function sent() {
  await db.exec('reset role;');
  const calls = await rows(`select url, body, headers from net_calls order by n`);
  await db.exec('truncate net_calls;');
  return calls.map(({ url, body, headers }) => ({ ...body, url, secret: headers['x-push-secret'], recipients: [...body.recipients].sort() }));
}

try {
  await db.exec(`create role anon; create role authenticated; create role service_role bypassrls; create role supabase_auth_admin;
    create schema auth; create schema extensions;
    create table auth.users(id uuid primary key, email text unique, raw_user_meta_data jsonb);
    create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    grant usage on schema auth to anon, authenticated, supabase_auth_admin;
    grant insert on auth.users to supabase_auth_admin;`);
  const files = (await readdir(new URL('../supabase/migrations/', import.meta.url))).filter((n) => n.endsWith('.sql')).sort();
  for (const name of files) {
    if (name.includes('_programar_')) continue; // pg_cron / pg_net schedules
    await db.exec(await readFile(new URL(`../supabase/migrations/${name}`, import.meta.url), 'utf8'));
  }

  // Without Vault and pg_net nothing is sent and nothing fails.
  // Sample users skip the "authorized registration" trigger, which is tested elsewhere.
  await db.exec(`set session_replication_role = replica;
    insert into auth.users(id) values ('${a}'),('${b}'),('${c}'),('${d}');
    set session_replication_role = origin;
    insert into public.account_profiles(id, display_name) values ('${a}','Ana'),('${b}','Beto'),('${c}','Caro'),('${d}','Dani');`);
  await db.exec(`insert into public.personal_status(id,user_id,custom_location,expires_at) values ('${d}','${d}','Casa',now()+interval '1 hour');`);
  check(true, 'Triggers are harmless without Vault and pg_net');

  // Local stand-ins: Vault with the two secrets, and pg_net recording each request.
  await db.exec(`create schema vault; create table vault.decrypted_secrets(name text, decrypted_secret text);
    insert into vault.decrypted_secrets values ('near_push_url','https://example.test/functions/v1/send-push'),('near_push_secret','s3cret');
    create schema net; create table public.net_calls(n serial, url text, body jsonb, headers jsonb);
    create function net.http_post(url text, body jsonb, headers jsonb, timeout_milliseconds int) returns bigint
      language sql as $$ insert into public.net_calls(url, body, headers) values (url, body, headers) returning n $$;`);

  // A is connected with B, C and D. C silenced A. A does not share status nor schedule with D.
  const ab = '10000000-0000-0000-0000-000000000001', ac = '10000000-0000-0000-0000-000000000002', ad = '10000000-0000-0000-0000-000000000003';
  await db.exec(`insert into public.person_connections(id, person_a, person_b) values ('${ab}','${a}','${b}'),('${ac}','${a}','${c}'),('${ad}','${a}','${d}');
    insert into public.connection_preferences(connection_id, owner_id, muted) values ('${ac}','${c}',true);
    insert into public.connection_preferences(connection_id, owner_id, share_status, share_schedule) values ('${ad}','${a}',false,false);`);

  // Status shared: B hears it; C silenced A; D is not shared with.
  await db.exec(`insert into public.personal_status(id,user_id,custom_location,custom_message,expires_at) values ('${a}','${a}','Biblioteca','Estudiando',now()+interval '1 hour');`);
  let calls = await sent();
  check(calls.length === 1 && calls[0].type === 'status', 'A shared status sends one status push');
  check(JSON.stringify(calls[0].recipients) === JSON.stringify([b]), 'Only connections that see the status and did not silence the owner');
  check(calls[0].data.location === 'Biblioteca' && calls[0].data.name === 'Ana', 'The push carries who and where');
  check(calls[0].secret === 's3cret' && calls[0].url.endsWith('/send-push'), 'The request carries the shared secret to the function');

  // Same status saved again does not repeat; a new place does.
  await db.exec(`update public.personal_status set expires_at = now()+interval '2 hours' where id='${a}';`);
  check((await sent()).length === 0, 'Extending a status does not notify again');
  await db.exec(`update public.personal_status set custom_location = 'Cafetería' where id='${a}';`);
  check((await sent())[0]?.data.location === 'Cafetería', 'Changing the place notifies');

  // Removing the status tells the audience it is back to the schedule.
  await db.exec(`delete from public.personal_status where id='${a}';`);
  calls = await sent();
  check(calls.length === 1 && calls[0].type === 'status_end' && calls[0].recipients.join() === b, 'Removing a status sends status_end');

  // Messages go to the recipient unless they silenced the sender.
  await db.exec(`insert into public.connection_signals(sender_id, recipient_id, message) values ('${b}','${a}','¿Todo bien?');`);
  calls = await sent();
  check(calls.length === 1 && calls[0].type === 'message' && calls[0].recipients.join() === a && calls[0].data.message === '¿Todo bien?', 'A message notifies its recipient');
  await db.exec(`insert into public.connection_signals(sender_id, recipient_id, message) values ('${a}','${c}','Hola');`);
  check((await sent()).length === 0, 'A silenced sender does not notify');

  // Activities starting this minute, in the owner's time zone; a shared status takes priority.
  await db.exec(`update public.account_profiles set timezone = 'America/Lima';
    insert into public.personal_schedule(user_id, day_of_week, start_time, end_time, title, location)
    select '${a}', extract(isodow from now() at time zone 'America/Lima')::int,
           date_trunc('minute', now() at time zone 'America/Lima')::time,
           (date_trunc('minute', now() at time zone 'America/Lima') + interval '1 minute')::time,
           'Prácticas', 'Lab 3'
    where (date_trunc('minute', now() at time zone 'America/Lima') + interval '1 minute')::time
        > date_trunc('minute', now() at time zone 'America/Lima')::time;`);
  const scheduled = Number((await rows(`select count(*) n from public.personal_schedule where user_id='${a}'`))[0].n);
  await db.exec(`select private.push_due_events();`);
  calls = (await sent()).filter((call) => call.type === 'activity');
  if (scheduled) {
    check(calls.length === 1 && calls[0].recipients.join() === b && calls[0].data.title === 'Prácticas', 'An activity starting now notifies who sees the schedule');
    await db.exec(`insert into public.personal_status(id,user_id,custom_location,expires_at) values ('${a}','${a}','Casa',now()+interval '1 hour');`);
    await sent();
    await db.exec(`select private.push_due_events();`);
    check((await sent()).filter((call) => call.type === 'activity').length === 0, 'A shared status hides the activity start');
  }

  // Tokens: each account registers its own; a device moves to the last account that signed in.
  await asUser(a);
  await db.exec(`select public.register_push_token('token-device-0000000000001');`);
  await asUser(b);
  await db.exec(`select public.register_push_token('token-device-0000000000001');`);
  let denied = false;
  try { await db.exec(`select * from public.push_tokens;`); } catch { denied = true; }
  check(denied, 'Tokens are not readable by apps');
  await db.exec('reset role;');
  check((await rows(`select user_id from public.push_tokens`))[0].user_id === b, 'The token belongs to the last account on the device');
  await asUser(a);
  await db.exec(`select public.unregister_push_token('token-device-0000000000001');`);
  await db.exec('reset role;');
  check((await rows(`select count(*) n from public.push_tokens`))[0].n == 1, 'Another account cannot remove the token');
  await asUser(b);
  await db.exec(`select public.unregister_push_token('token-device-0000000000001');`);
  await db.exec('reset role;');
  check((await rows(`select count(*) n from public.push_tokens`))[0].n == 0, 'The owner removes the token on sign-out');

  console.log(`Push notifications: ${checks} checks passed.`);
} finally { await db.close(); }
