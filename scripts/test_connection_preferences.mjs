// npm.cmd install --prefix build/schema-check --no-save --ignore-scripts @electric-sql/pglite@0.3.14
// node scripts/test_connection_preferences.mjs
// Runs a real embedded PostgreSQL instance; never connects to the remote project.
import { PGlite } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/index.js';
import { readFile, readdir } from 'node:fs/promises';
import assert from 'node:assert/strict';

const db = new PGlite();
const a = '00000000-0000-0000-0000-000000000001';
const b = '00000000-0000-0000-0000-000000000002';
const c = '00000000-0000-0000-0000-000000000003';
let checks = 0;
const check = (condition, name) => { assert.ok(condition, name); checks++; };
async function denied(sql, name) {
  let rejected = false;
  try { await db.exec(sql); } catch { rejected = true; }
  check(rejected, name);
}
async function asUser(id) {
  await db.exec(`reset role; set role authenticated; select set_config('request.jwt.claim.sub', '${id}', false);`);
}
const rows = async (sql) => (await db.query(sql)).rows;
const count = async (table, where = 'true') => Number((await rows(`select count(*) n from public.${table} where ${where}`))[0].n);
const migration = async (suffix) => {
  const name = (await readdir(new URL('../supabase/migrations/', import.meta.url))).find((n) => n.endsWith(suffix));
  assert.ok(name, `Missing migration ${suffix}`);
  await db.exec(await readFile(new URL(`../supabase/migrations/${name}`, import.meta.url), 'utf8'));
};
// The base model may still live outside migrations/ or already be moved there.
const baseModel = async () => {
  try { await db.exec(await readFile(new URL('../supabase/account-model.sql', import.meta.url), 'utf8')); }
  catch (error) { if (error.code !== 'ENOENT') throw error; await migration('_crear_cuentas_horarios_y_conexiones.sql'); }
};

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
  await baseModel();
  await migration('_mensajes_en_vivo_y_retencion_diaria.sql');
  await migration('_preferencias_por_conexion.sql');

  const ab = '10000000-0000-0000-0000-000000000001';
  const bc = '10000000-0000-0000-0000-000000000002';
  await db.exec(`
    insert into public.account_profiles(id, display_name) values ('${a}','A'),('${b}','B'),('${c}','C');
    insert into public.person_connections(id, person_a, person_b) values ('${ab}','${a}','${b}'), ('${bc}','${b}','${c}');
    insert into public.personal_schedule(user_id,day_of_week,start_time,end_time,title) values ('${a}',1,'09:00','10:00','Class A');
    insert into public.personal_status(id,user_id,custom_location,expires_at) values ('${a}','${a}','Library',now()+interval '1 hour');
  `);

  // Defaults keep today's behaviour: a connection sees schedule and status.
  await asUser(b);
  check(await count('personal_schedule', `user_id='${a}'`) === 1, 'Without preferences the schedule is shared');
  check(await count('personal_status', `user_id='${a}'`) === 1, 'Without preferences the status is shared');

  // A stops sharing the schedule with B only.
  await asUser(a);
  await db.exec(`insert into public.connection_preferences(connection_id, owner_id, nickname, share_schedule)
    values ('${ab}','${a}','Bea', false)`);
  await asUser(b);
  check(await count('personal_schedule', `user_id='${a}'`) === 0, 'Without permission the schedule is hidden');
  check(await count('personal_status', `user_id='${a}'`) === 1, 'Status is still shared independently');
  check(await count('connection_preferences') === 0, "The other person's choices and nickname are private");

  // A now hides the status too and shares the schedule again.
  await asUser(a);
  await db.exec(`update public.connection_preferences set share_schedule=true, share_status=false where connection_id='${ab}'`);
  await asUser(b);
  check(await count('personal_schedule', `user_id='${a}'`) === 1, 'Schedule visible again once re-enabled');
  check(await count('personal_status', `user_id='${a}'`) === 0, 'Without permission the status is hidden');

  // B's own preferences never change what B shares or what A decided.
  await db.exec(`insert into public.connection_preferences(connection_id, owner_id, share_status, muted) values ('${ab}','${b}', true, true)`);
  check(await count('personal_status', `user_id='${a}'`) === 0, "The viewer cannot grant themselves the owner's status");
  check((await rows(`update public.connection_preferences set share_status=true where owner_id='${a}' returning owner_id`)).length === 0,
    "The viewer's update of the owner's row matches nothing");
  await asUser(a);
  check((await rows(`select share_status from public.connection_preferences where owner_id='${a}'`))[0].share_status === false,
    "Nobody can change another person's preferences");
  check(await count('connection_preferences') === 1, 'Each person sees only their own side');

  // Writing preferences for someone else or for a connection you are not part of is refused.
  await asUser(c);
  await denied(`insert into public.connection_preferences(connection_id, owner_id) values ('${ab}','${a}')`, 'Cannot write preferences as another person');
  await denied(`insert into public.connection_preferences(connection_id, owner_id) values ('${ab}','${c}')`, 'Cannot attach preferences to a foreign connection');
  await denied(`insert into public.connection_preferences(connection_id, owner_id, nickname) values ('${bc}','${c}','${'x'.repeat(41)}')`, 'Nickname length is bounded');
  check(await count('personal_schedule', `user_id='${a}'`) === 0, 'Unconnected account still sees no schedule');
  check(await count('personal_status', `user_id='${a}'`) === 0, 'Unconnected account still sees no status');
  check((await rows(`select private.connection_shares('${a}', 'schedule') s`))[0].s === false, 'The sharing function answers only for the caller');

  // Unlinking removes both sides' preferences with the connection.
  await db.exec('reset role');
  check(await count('connection_preferences') === 2, 'Both sides stored');

  // Messages: only the recipient can mark as read or delete.
  await db.exec(`insert into public.connection_signals(id,sender_id,recipient_id,message) values
    ('20000000-0000-0000-0000-000000000001','${a}','${b}','Hola B'),
    ('20000000-0000-0000-0000-000000000002','${a}','${b}','Segundo'),
    ('20000000-0000-0000-0000-000000000003','${b}','${c}','Hola C')`);
  const m1 = '20000000-0000-0000-0000-000000000001';
  const m2 = '20000000-0000-0000-0000-000000000002';
  const m3 = '20000000-0000-0000-0000-000000000003';
  const readAt = async (id) => (await rows(`select read_at from public.connection_signals where id='${id}'`))[0]?.read_at ?? null;

  await asUser(a);
  check((await rows(`update public.connection_signals set read_at=now() where id='${m1}' returning id`)).length === 0, 'Sender cannot mark as read');
  check((await rows(`delete from public.connection_signals where id='${m1}' returning id`)).length === 0, 'Sender cannot delete a sent message');
  await asUser(c);
  check((await rows(`update public.connection_signals set read_at=now() where id='${m1}' returning id`)).length === 0, 'A third person cannot mark as read');
  check((await rows(`delete from public.connection_signals where id='${m1}' returning id`)).length === 0, 'A third person cannot delete');
  await asUser(b);
  await denied(`update public.connection_signals set message='Edited' where id='${m1}'`, 'Recipient cannot edit the text');
  await denied(`update public.connection_signals set recipient_id='${c}' where id='${m1}'`, 'Recipient cannot redirect the message');
  check((await rows(`update public.connection_signals set read_at='2000-01-01' where id='${m1}' returning id`)).length === 1, 'Recipient can mark as read');
  const first = await readAt(m1);
  check(first !== null && first.getFullYear() > 2000, 'The server decides the read time');
  await db.exec(`update public.connection_signals set read_at=now()+interval '1 day' where id='${m1}'`);
  check((await readAt(m1)).getTime() === first.getTime(), 'Marking again keeps the first read time');
  check((await rows(`update public.connection_signals set read_at=now() where id='${m3}' returning id`)).length === 0, "B cannot mark B's sent message as read");
  check((await rows(`delete from public.connection_signals where id='${m3}' returning id`)).length === 0, "B cannot delete the message B sent to C");
  check((await rows(`delete from public.connection_signals where id='${m2}' returning id`)).length === 1, 'Recipient can delete a received message');
  await db.exec('reset role');
  check(await count('connection_signals') === 2, 'Only the recipient deletion happened');
  check(await readAt(m3) === null, "Nobody marked C's message as read");

  await db.exec(`delete from public.person_connections where id='${ab}'`);
  check(await count('connection_preferences') === 0, "Unlinking removes that connection's preferences");

  await db.exec(`reset role; set role anon; select set_config('request.jwt.claim.sub','',false)`);
  await denied('select * from public.connection_preferences', 'Anonymous access denied: connection_preferences');
  await denied(`select private.connection_shares('${a}', 'schedule')`, 'Anonymous callers cannot ask the sharing function');
  await denied(`delete from public.connection_signals`, 'Anonymous delete denied');
  console.log(`Connection preferences: ${checks} checks passed.`);
} finally { await db.close(); }
