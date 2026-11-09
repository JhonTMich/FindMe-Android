// npm.cmd install --prefix build/schema-check --no-save --ignore-scripts @electric-sql/pglite@0.3.14
// node scripts/test_account_schema.mjs
// Runs a real embedded PostgreSQL instance; never connects to the remote project.
import { PGlite } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/index.js';
import { btree_gist } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/contrib/btree_gist.js';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';

const db = new PGlite({ extensions: { btree_gist } });
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
    create role anon; create role authenticated; create role service_role bypassrls;
    create schema auth;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as
      $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
    grant usage on schema auth to authenticated, anon;
    insert into auth.users values ('${a}'), ('${b}'), ('${c}');
  `);
  await db.exec(await readFile(new URL('../supabase/account-model.sql', import.meta.url), 'utf8'));
  await db.exec('create schema extensions');
  await db.exec(await readFile(new URL('../supabase/migrations/20261105204109_validar_cruces_horarios.sql', import.meta.url), 'utf8'));
  await db.exec(`insert into public.account_profiles(id, display_name) values ('${a}','A'),('${b}','B'),('${c}','C');`);
  await asUser(a);
  await check(await count('account_profiles') === 1, 'Only own profile visible before linking');
  await denied(`insert into public.account_profiles(id,display_name) values ('${b}','Stolen')`, 'Cannot claim another profile');
  await db.exec(`insert into public.personal_schedule(user_id,day_of_week,start_time,end_time,title) values ('${a}',1,'09:00','10:00','Original');`);
  await denied(`insert into public.personal_schedule(user_id,day_of_week,start_time,end_time,title) values ('${a}',1,'10:00','09:00','Invalid');`, 'Invalid intervals rejected');
  await denied(`insert into public.person_connections(person_a,person_b) values ('${a}','${b}')`, 'Direct link creation forbidden');
  await db.exec(`insert into public.connection_invitations values ('${a}','${'1'.repeat(32)}',now()+interval '1 hour');`);
  await denied(`select public.accept_connection_invitation('${'1'.repeat(32)}')`, 'Cannot accept own invitation');
  await db.exec('reset role');
  await db.exec(await readFile(new URL('../supabase/upgrades/short-invitations.sql', import.meta.url), 'utf8'));
  await db.exec(await readFile(new URL('../supabase/upgrades/username-login.sql', import.meta.url), 'utf8'));
  await asUser(a);
  await db.exec(`update public.account_profiles set username='jhontmich' where id='${a}'`);
  await denied(`update public.account_profiles set username='Bad Name' where id='${a}'`, 'Username syntax is enforced');
  await asUser(b);
  await denied(`update public.account_profiles set username='jhontmich' where id='${b}'`, 'Duplicate usernames rejected');
  await denied(`select public.consume_username_login_attempt('${'a'.repeat(64)}')`, 'Clients cannot bypass login limiter');
  await denied(`select * from private.username_login_limits`, 'Counters are private');
  await db.exec('reset role; set role service_role');
  for (let i = 0; i < 8; i++) await check((await db.query(`select public.consume_username_login_attempt('${'a'.repeat(64)}') allowed`)).rows[0].allowed, 'Allows eight attempts');
  await check(!(await db.query(`select public.consume_username_login_attempt('${'a'.repeat(64)}') allowed`)).rows[0].allowed, 'Ninth attempt is blocked');
  await db.exec(`update private.username_login_limits set expires_at=now()-interval '1 minute'`);
  await check((await db.query(`select public.consume_username_login_attempt('${'a'.repeat(64)}') allowed`)).rows[0].allowed, 'Limit expires');
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
  await asUser(a);
  await db.exec(`update public.connection_invitations set code='ABCDEFGH', expires_at=now()+interval '1 hour'`);
  await asUser(b);
  await db.exec(`select public.accept_connection_invitation(' abcd-efgh ')`);
  await check(await count('person_connections') === 1, 'Short code accepts lowercase, spaces and separator');
  await denied(`select public.accept_connection_invitation('ABCD-EFGH')`, 'Short code is also single use');
  const activity = (owner, day, start, end, category, modality, title) =>
    `insert into public.personal_schedule(user_id,day_of_week,start_time,end_time,category,modality,title)
      values ('${owner}',${day},'${start}','${end}','${category}','${modality}','${title}')`;
  const work = activity(a, 3, '09:00', '12:00', 'work', 'presencial', 'Work');
  const virtual = activity(a, 3, '10:00', '11:00', 'university', 'virtual', 'Virtual class');
  await asUser(a);
  await db.exec(work);
  await db.exec(virtual);
  await check(Number((await db.query('select count(*) n from public.personal_schedule where day_of_week = 3')).rows[0].n) === 2,
    'Virtual class can overlap work');
  for (const [category, modality] of [['work', 'virtual'], ['university', 'virtual'], ['university', 'presencial'], ['free', 'virtual']]) {
    await assert.rejects(db.exec(activity(a, 3, '10:15', '10:45', category, modality, 'Conflict')), { code: '23P01' });
    checks++;
  }
  await assert.rejects(db.exec("update public.personal_schedule set modality='presencial' where title='Virtual class'"), { code: '23P01' });
  checks++;
  await assert.rejects(db.exec(`insert into public.personal_schedule(user_id,day_of_week,start_time,end_time,title)
    values ('${a}', 5, '10:15', '10:45', 'Batch'), ('${a}', 3, '10:15', '10:45', 'Batch')`), { code: '23P01' });
  await check((await db.query("select id from public.personal_schedule where title = 'Batch'")).rows.length === 0,
    'A conflict in one day rolls back the whole batch');
  await db.exec(activity(a, 3, '12:00', '13:00', 'free', 'presencial', 'Adjacent'));
  await db.exec(activity(a, 4, '10:15', '10:45', 'university', 'presencial', 'Different day'));
  await asUser(b);
  await db.exec(activity(b, 3, '10:15', '10:45', 'university', 'presencial', 'Different owner'));
  await db.exec('reset role');
  await db.exec(await readFile(new URL('../supabase/migrations/20261109181959_permitir_turnos_nocturnos.sql', import.meta.url), 'utf8'));
  await asUser(c);
  const overnight = (day, start, end, title, category = 'work', modality = 'presencial') =>
    `insert into public.personal_schedule(user_id,day_of_week,start_time,end_time,title,category,modality,ends_next_day)
      values ('${c}',${day},'${start}','${end}','${title}','${category}','${modality}',true)`;
  await db.exec(overnight(3, '19:00', '07:00', 'Night shift'));
  await assert.rejects(db.exec(activity(c, 3, '23:00', '23:30', 'free', 'presencial', 'Before midnight')), { code: '23P01' });
  await assert.rejects(db.exec(activity(c, 4, '06:00', '08:00', 'university', 'presencial', 'After midnight')), { code: '23P01' });
  await db.exec(activity(c, 4, '06:00', '07:00', 'university', 'virtual', 'Allowed virtual class'));
  await assert.rejects(db.exec(activity(c, 4, '06:30', '07:00', 'university', 'virtual', 'Second class')), { code: '23P01' });
  await db.exec(activity(c, 4, '07:00', '08:00', 'work', 'presencial', 'Next shift'));
  await db.exec(overnight(7, '19:00', '07:00', 'Sunday shift'));
  await assert.rejects(db.exec(activity(c, 1, '06:00', '07:00', 'work', 'presencial', 'Monday conflict')), { code: '23P01' });
  await db.exec(activity(c, 1, '07:00', '08:00', 'free', 'presencial', 'Monday adjacent'));
  await assert.rejects(db.exec("update public.personal_schedule set ends_next_day=false where title='Night shift'"), { code: '23514' });
  await assert.rejects(db.exec(overnight(5, '07:00', '19:00', 'Too long')), { code: '23514' });
  await assert.rejects(db.exec(activity(c, 5, '19:00', '07:00', 'work', 'presencial', 'Missing overnight flag')), { code: '23514' });
  await db.exec(overnight(5, '07:00', '07:00', 'Full day'));
  await check((await db.query("select count(*) n from public.personal_schedule where user_id=$1 and ends_next_day", [c])).rows[0].n === 3,
    'Overnight rows are stored once and enforce both days including week wrap');
  await asUser(a);
  await db.exec(`insert into public.personal_schedule(user_id,day_of_week,start_time,end_time,title,ends_next_day)
    values
      ('${a}',1,'22:00','04:00','Three night shifts',true),
      ('${a}',2,'22:00','04:00','Three night shifts',true),
      ('${a}',3,'22:00','04:00','Three night shifts',true)`);
  await check((await db.query("select count(*) n from public.personal_schedule where title='Three night shifts'")).rows[0].n === 3,
    'Monday to Wednesday 22:00-04:00 creates three independent shifts ending Tuesday to Thursday');
  await db.exec(`reset role; set role anon; select set_config('request.jwt.claim.sub','',false)`);
  for (const table of ['account_profiles','personal_schedule','personal_status','person_connections','connection_invitations','quick_messages','connection_signals']) {
    await denied(`select * from public.${table}`, `Anonymous access denied: ${table}`);
  }
  await denied(`select public.accept_connection_invitation('${'2'.repeat(32)}')`, 'Anonymous RPC denied');
  console.log(`Account schema: ${checks} checks passed.`);
} finally { await db.close(); }
