// Exercises dated writes and RLS in embedded PostgreSQL without touching production.
import { PGlite } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/index.js';
import { btree_gist } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/contrib/btree_gist.js';
import { readFile, readdir } from 'node:fs/promises';
import assert from 'node:assert/strict';

const db = new PGlite({ extensions: { btree_gist } });
const owner = '00000000-0000-0000-0000-000000000001';
const contact = '00000000-0000-0000-0000-000000000002';
const connection = '10000000-0000-0000-0000-000000000001';
const activity = '20000000-0000-0000-0000-000000000001';
const monday = "(date_trunc('week', now() at time zone 'UTC')::date + 7)";
const files = await readdir(new URL('../supabase/migrations/', import.meta.url));

async function migration(suffix) {
  const file = files.find(name => name.endsWith(suffix));
  assert.ok(file, `Missing migration ${suffix}`);
  await db.exec(await readFile(new URL(`../supabase/migrations/${file}`, import.meta.url), 'utf8'));
}
const as = user => db.exec(`reset role; set role authenticated; select set_config('request.jwt.claim.sub', '${user}', false)`);
const change = args => db.query(`select occurrence_exceptions from public.change_schedule_occurrence('${activity}', ${monday}, ${args})`);
const schedule = () => db.query(`select occurrence_exceptions from public.personal_schedule where id = '${activity}'`);
const starts = date => db.query(`select o.start_time::text from public.personal_schedule s
  cross join lateral private.schedule_starts_on(s, ${date}) o where s.id = '${activity}'`);

try {
  await db.exec(`
    create role anon; create role authenticated; create role service_role bypassrls;
    create schema auth; create schema extensions; create schema net;
    create table auth.users(id uuid primary key);
    create function auth.uid() returns uuid language sql stable as $$
      select nullif(current_setting('request.jwt.claim.sub', true),'')::uuid
    $$;
    grant usage on schema auth to authenticated, anon;
    insert into auth.users values ('${owner}'), ('${contact}');
  `);
  for (const suffix of [
    '_crear_cuentas_horarios_y_conexiones.sql', '_mensajes_en_vivo_y_retencion_diaria.sql',
    '_validar_cruces_horarios.sql', '_permitir_turnos_nocturnos.sql', '_preferencias_por_conexion.sql',
    '_excepciones_de_horario.sql',
  ]) await migration(suffix);
  await db.exec(`
    insert into public.account_profiles (id, display_name, timezone)
      values ('${owner}', 'Persona A', 'UTC'), ('${contact}', 'Persona B', 'UTC');
    insert into public.person_connections (id, person_a, person_b) values ('${connection}', '${owner}', '${contact}');
    insert into public.personal_schedule (id, user_id, day_of_week, start_time, end_time, title)
      values ('${activity}', '${owner}', 1, '09:00', '10:00', 'Clase');
    insert into public.personal_schedule (user_id, day_of_week, start_time, end_time, title)
      values ('${owner}', 2, '15:00', '16:00', 'Otra actividad');
  `);

  await as(owner);
  assert.deepEqual(Object.values((await change("'cancel'")).rows[0].occurrence_exceptions), [{ cancelled: true }]);
  await assert.rejects(change(`'move', ${monday} + 1, '15:30', '17:00', false`), { code: '23P01' });
  await assert.rejects(change(`'move', ${monday} + 1, '17:00', '17:00', false`), { code: 'FM004' });
  const [replacement] = Object.values((await change(`'move', ${monday} + 1, '23:00', '02:00', true`)).rows[0].occurrence_exceptions);
  assert.equal(replacement.start_time, '23:00:00');
  assert.equal(replacement.ends_next_day, true);
  await assert.rejects(db.exec(`insert into public.personal_schedule (user_id, day_of_week, start_time, end_time, title)
    values ('${owner}', 3, '01:00', '03:00', 'Cruza la actividad reprogramada')`), { code: '23P01' });
  await assert.rejects(db.exec(`update public.personal_schedule set occurrence_exceptions = '{}' where id = '${activity}'`), { code: '42501' });
  // This is the form of the original client's upsert.
  await db.exec(`
    insert into public.personal_schedule (id, user_id, day_of_week, start_time, end_time, title)
    values ('${activity}', '${owner}', 1, '09:00', '10:00', 'Clase actualizada')
    on conflict (id) do update set title = excluded.title;
  `);
  assert.equal(Object.values((await schedule()).rows[0].occurrence_exceptions)[0].start_time, '23:00:00');

  await db.exec('reset role');
  assert.deepEqual((await starts(monday)).rows, []);
  assert.deepEqual((await starts(`${monday} + 1`)).rows, [{ start_time: '23:00:00' }]);
  assert.deepEqual((await starts(`${monday} + 7`)).rows, [{ start_time: '09:00:00' }]);

  await as(contact);
  assert.equal((await schedule()).rows.length, 1, 'A connected person can see the move');
  await assert.rejects(change("'restore'"), { code: '42501' });
  await as(owner);
  await db.exec(`insert into public.connection_preferences (connection_id, owner_id, share_schedule)
    values ('${connection}', '${owner}', false)`);
  await as(contact);
  assert.equal((await schedule()).rows.length, 0, 'Revoking access hides the weekly activity and its exceptions');
  await as(owner);
  assert.deepEqual((await change("'restore'")).rows[0].occurrence_exceptions, {});
  console.log('Dated schedule: cancel, move, restore, overlap checks, compatibility and access verified.');
} finally {
  await db.close();
}
