import { PGlite } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/index.js';
import { readFile, readdir } from 'node:fs/promises';
import assert from 'node:assert/strict';
const db = new PGlite();
const a = '00000000-0000-0000-0000-000000000001';
const b = '00000000-0000-0000-0000-000000000002';
const c = '00000000-0000-0000-0000-000000000003';
let checks = 0;
const check = (v, name) => { assert.ok(v, name); checks++; };
async function asUser(id) { await db.exec(`reset role; set role authenticated; select set_config('request.jwt.claim.sub','${id}',false)`); }
async function denied(sql) { let failed = false; try { await db.exec(sql); } catch { failed = true; } check(failed, 'Forbidden operation'); }
try {
  await db.exec(`create role anon; create role authenticated; create role service_role bypassrls;
    create schema auth; create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    grant usage on schema auth to authenticated,anon;
    insert into auth.users values ('${a}'),('${b}'),('${c}');`);
  await db.exec(await readFile(new URL('../supabase/account-model.sql', import.meta.url), 'utf8'));
  const migration = (await readdir(new URL('../supabase/migrations/', import.meta.url))).find((n) => n.endsWith('_mensajes_en_vivo_y_retencion_diaria.sql'));
  await db.exec(await readFile(new URL(`../supabase/migrations/${migration}`, import.meta.url), 'utf8'));
  await db.exec(`insert into public.account_profiles(id,display_name) values ('${a}','A'),('${b}','B'),('${c}','C');
    insert into public.person_connections(person_a,person_b) values ('${a}','${b}');
    insert into public.connection_signals(sender_id,recipient_id,message,created_at) values
      ('${a}','${b}','Expired',now()-interval '25 hours'),('${a}','${b}','Recent',now()-interval '23 hours');`);
  await asUser(b);
  check((await db.query('select message from public.connection_signals')).rows.length === 1, 'Expired messages invisible to recipient');
  await asUser(a);
  check((await db.query('select message from public.connection_signals')).rows.length === 1, 'Expired messages invisible to sender');
  await denied(`insert into public.connection_signals(sender_id,recipient_id,message,created_at) values ('${a}','${b}','Forged',now())`);
  await denied('select private.purge_expired_signals()');
  // No daily or cooldown restriction was requested. Repeated messages remain allowed.
  for (let i=0; i<20; i++) await db.exec(`insert into public.connection_signals(sender_id,recipient_id,message) values ('${a}','${b}','Occasional ${i}')`);
  check((await db.query('select count(*) n from public.connection_signals')).rows[0].n === 21, 'No new count or cooldown limit');
  await asUser(c);
  check((await db.query('select count(*) n from public.connection_signals')).rows[0].n === 0, 'Unconnected account cannot read messages');
  await db.exec('reset role; set role service_role');
  check((await db.query('select private.purge_expired_signals() n')).rows[0].n === 1, 'Cleanup physically deletes only expired messages');
  check((await db.query('select count(*) n from public.connection_signals')).rows[0].n === 21, 'Recent messages retained');
  await asUser(a);
  await db.exec('delete from public.person_connections');
  await asUser(b);
  check((await db.query('select count(*) n from public.connection_signals')).rows[0].n === 0, 'Revocation still removes access');
  console.log(`Message retention: ${checks} checks passed.`);
} finally { await db.close(); }
