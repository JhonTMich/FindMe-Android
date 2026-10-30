// npm.cmd install --prefix build/schema-check --no-save --ignore-scripts @electric-sql/pglite@0.3.14
// node scripts/test_account_schema.mjs
// Runs a real embedded PostgreSQL instance; never connects to the remote project.
import { PGlite } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/index.js';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

const db = new PGlite();
const a = '00000000-0000-0000-0000-000000000001';
const b = '00000000-0000-0000-0000-000000000002';
const c = '00000000-0000-0000-0000-000000000003';
let checks = 0;
async function check(condition, name) { assert.ok(condition, name); checks++; }
async function denied(sql, name) {
  let rejected = false;
  try { await db.exec(sql); } catch { rejected = true; }
  await check(rejected, name);
}
async function asUser(id) {
  await db.exec(`reset role; set role authenticated; select set_config('request.jwt.claim.sub', '${id}', false);`);
}
async function count(table) { return Number((await db.query(`select count(*) n from public.${table}`)).rows[0].n); }
try {
  await db.exec(`
    create role anon; create role authenticated;
    create schema auth;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as
      $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
    grant usage on schema auth to authenticated, anon;
    insert into auth.users values ('${a}'), ('${b}'), ('${c}');
  `);
  await db.exec(await readFile(new URL('../supabase/account-model.sql', import.meta.url), 'utf8'));
  await db.exec(`insert into public.account_profiles(id, display_name) values ('${a}','A'),('${b}','B'),('${c}','C');`);
  await asUser(a);
  await check(await count('account_profiles') === 1, 'Only own profile visible before linking');
  await denied(`insert into public.account_profiles(id,display_name) values ('${b}','Stolen')`, 'Cannot claim another profile');
  await db.exec(`insert into public.personal_schedule(user_id,day_of_week,start_time,end_time,title) values ('${a}',1,'09:00','10:00','Original');`);
  await denied(`insert into public.personal_schedule(user_id,day_of_week,start_time,end_time,title) values ('${a}',1,'10:00','09:00','Invalid');`, 'Invalid intervals rejected');
  await denied(`insert into public.person_connections(person_a,person_b) values ('${a}','${b}')`, 'Direct link creation forbidden');
  await db.exec(`insert into public.connection_invitations values ('${a}','${'1'.repeat(32)}',now()+interval '1 hour');`);
  await denied(`select public.accept_connection_invitation('${'1'.repeat(32)}')`, 'Cannot accept own invitation');
  await asUser(b);
  await check(await count('connection_invitations') === 0, 'Other invitations are private');
  await check(await count('personal_schedule') === 0, 'Unconnected schedule hidden');
  await db.exec(`select public.accept_connection_invitation('${'1'.repeat(32)}')`);
  await check(await count('account_profiles') === 2, 'Linked profiles visible');
  await check(await count('personal_schedule') === 1, 'Linked schedule visible');
  await denied(`select public.accept_connection_invitation('${'1'.repeat(32)}')`, 'Invitation cannot be reused');
  const update = await db.query(`update public.personal_schedule set title='Stolen' where user_id='${a}' returning id`);
  await check(update.rows.length === 0, 'Linked person cannot edit schedule');
  await denied(`insert into public.personal_status(id,user_id,custom_location,expires_at) values ('${a}','${a}','Fake',now()+interval '1 hour')`, 'Cannot publish someone else status');
  await db.exec(`insert into public.connection_signals(sender_id,recipient_id,message) values ('${b}','${a}','Configurable message');`);
  await denied(`insert into public.connection_signals(sender_id,recipient_id,message) values ('${a}','${b}','Impersonated')`, 'Sender cannot be forged');
  await denied(`insert into public.connection_signals(sender_id,recipient_id,message,created_at) values ('${b}','${a}','Backdated',now())`, 'Timestamp cannot be forged');
  await asUser(c);
  await check(await count('personal_schedule') === 0, 'Third account still cannot read schedule');
  await check(await count('connection_signals') === 0, 'Third account cannot read signals');
  await denied(`insert into public.connection_signals(sender_id,recipient_id,message) values ('${c}','${a}','Unwanted')`, 'Unconnected messages rejected');
  await asUser(a);
  await db.exec(`insert into public.quick_messages(user_id,label,message) values ('${a}','Label','Custom text')`);
  await asUser(b);
  await check(await count('quick_messages') === 0, 'Quick actions private to owner');
  await db.exec(`delete from public.person_connections`);
  await check(await count('personal_schedule') === 0, 'Revocation removes schedule access immediately');
  await check(await count('connection_signals') === 0, 'Revocation removes signal history access');
  await denied(`insert into public.connection_signals(sender_id,recipient_id,message) values ('${b}','${a}','After unlink')`, 'Revoked connection cannot send');
  await asUser(a);
  await db.exec(`insert into public.connection_invitations values ('${a}','${'2'.repeat(32)}',now()-interval '1 hour')`);
  await asUser(b);
  await denied(`select public.accept_connection_invitation('${'2'.repeat(32)}')`, 'Expired invitation rejected');
  await db.exec(`reset role; set role anon; select set_config('request.jwt.claim.sub','',false)`);
  for (const table of ['account_profiles','personal_schedule','personal_status','person_connections','connection_invitations','quick_messages','connection_signals']) {
    await denied(`select * from public.${table}`, `Anonymous access denied: ${table}`);
  }
  await denied(`select public.accept_connection_invitation('${'2'.repeat(32)}')`, 'Anonymous RPC denied');
  console.log(`Account schema: ${checks} checks passed.`);
} finally { await db.close(); }
