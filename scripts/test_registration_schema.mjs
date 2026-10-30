import { PGlite } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/index.js';
import { readFile, readdir } from 'node:fs/promises';
import assert from 'node:assert/strict';

const db = new PGlite();
let checks = 0;
const check = (value, message) => { assert.ok(value, message); checks++; };
async function denied(sql, message) {
  let failed = false;
  try { await db.exec(sql); } catch { failed = true; }
  check(failed, message);
}
const user = (n) => `00000000-0000-0000-0000-${String(n).padStart(12, '0')}`;
const meta = (username = 'persona_local', name = 'Persona de prueba', timezone = 'America/Lima') => JSON.stringify({ username, display_name: name, timezone });
const insert = (n, email, data = meta()) => `insert into auth.users(id,email,raw_user_meta_data) values ('${user(n)}','${email}','${data}'::jsonb)`;
try {
  await db.exec(`create role anon; create role authenticated; create role service_role bypassrls; create role supabase_auth_admin;
    create schema auth; create table auth.users(id uuid primary key, email text unique, raw_user_meta_data jsonb);
    create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    grant usage on schema auth to anon,authenticated,supabase_auth_admin;
    grant insert on auth.users to supabase_auth_admin;
    insert into auth.users(id,email) values ('${user(1)}','existing@example.test');`);
  await db.exec(await readFile(new URL('../supabase/account-model.sql', import.meta.url), 'utf8'));
  await db.exec(await readFile(new URL('../supabase/upgrades/username-login.sql', import.meta.url), 'utf8'));
  await db.exec(`insert into public.account_profiles(id,display_name,username) values ('${user(1)}','Existing','existing')`);
  const migration = (await readdir(new URL('../supabase/migrations/', import.meta.url))).find((name) => name.endsWith('_registro_por_correos_autorizados.sql'));
  await db.exec(await readFile(new URL(`../supabase/migrations/${migration}`, import.meta.url), 'utf8'));
  for (const role of ['anon', 'authenticated']) {
    await db.exec(`set role ${role}`);
    await denied('select * from public.signup_allowed_emails', `${role} cannot list allowed emails`);
    await denied("insert into public.signup_allowed_emails(email) values ('attacker@example.test')", `${role} cannot self-authorize`);
    await db.exec('reset role');
  }
  await db.exec('set role supabase_auth_admin');
  await denied(insert(2, 'unknown@example.test'), 'Direct Auth INSERT without authorization is blocked');
  await db.exec('reset role');
  await db.exec(`insert into public.signup_allowed_emails(email) values ('allowed@example.test'),('second@example.test'),('invalid@example.test'),('disabled@example.test'),('expired@example.test');
    update public.signup_allowed_emails set enabled=false where email='disabled@example.test';
    update public.signup_allowed_emails set expires_at=now()-interval '1 day' where email='expired@example.test';`);
  await db.exec('set role supabase_auth_admin');
  await denied(insert(3, 'disabled@example.test'), 'Disabled authorization blocked');
  await denied(insert(4, 'expired@example.test'), 'Expired authorization blocked');
  await denied(insert(5, 'invalid@example.test', '{}'), 'Missing chosen name and username blocked');
  await denied(insert(5, 'invalid@example.test', meta('invalid user')), 'Invalid username blocked');
  await denied(insert(5, 'invalid@example.test', meta('new_user', 'Mi cuenta')), 'Placeholder name blocked');
  await denied(insert(5, 'invalid@example.test', meta('new_user', 'Name', 'Invalid/Zone')), 'Invalid timezone blocked');
  await db.exec(insert(6, 'ALLOWED@example.test', meta('Chosen_User', 'My chosen name')));
  await db.exec('reset role');
  const profile = (await db.query(`select display_name,username from public.account_profiles where id='${user(6)}'`)).rows[0];
  check(profile.display_name === 'My chosen name' && profile.username === 'chosen_user', 'Profile uses user choices and normalized username');
  check((await db.query("select used_at is not null and registered_user_id is not null as used from public.signup_allowed_emails where email='allowed@example.test'")).rows[0].used, 'Authorization consumed atomically');
  await db.exec('set role supabase_auth_admin');
  await denied(insert(7, 'allowed@example.test', meta('other_user')), 'Authorization cannot be reused');
  await denied(insert(8, 'second@example.test', meta('chosen_user')), 'Duplicate username rolls back signup');
  await db.exec('reset role');
  check((await db.query("select used_at is null as available from public.signup_allowed_emails where email='second@example.test'")).rows[0].available, 'Failed signup does not consume authorization');
  check(Number((await db.query(`select count(*) n from auth.users where id='${user(8)}'`)).rows[0].n) === 0, 'Failed profile leaves no orphan Auth user');
  check(Number((await db.query('select count(*) n from auth.users')).rows[0].n) === 2, 'Existing account preserved; no unauthorized accounts added');
  check((await db.query(`select username from public.account_profiles where id='${user(1)}'`)).rows[0].username === 'existing', 'Existing profile unchanged');
  console.log(`Registration schema: ${checks} checks passed.`);
} finally { await db.close(); }
