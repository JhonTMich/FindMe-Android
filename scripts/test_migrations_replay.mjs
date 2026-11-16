// npm.cmd install --prefix build/schema-check --no-save --ignore-scripts @electric-sql/pglite@0.3.14
// node scripts/test_migrations_replay.mjs
// Applies every file in supabase/migrations, in version order, to an empty database, the way
// `supabase db reset` would. Never connects to the remote project.
import { PGlite } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/index.js';
import { btree_gist } from '../build/schema-check/node_modules/@electric-sql/pglite/dist/contrib/btree_gist.js';
import { readFile, readdir } from 'node:fs/promises';
import assert from 'node:assert/strict';

// pg_cron is not available in PGlite; the scheduling migration is checked by test_message_retention.mjs.
const needsPgCron = (name) => name.endsWith('_programar_limpieza_mensajes.sql');

const db = new PGlite({ extensions: { btree_gist } });
try {
  await db.exec(`create role anon; create role authenticated; create role service_role bypassrls; create role supabase_auth_admin;
    create schema auth; create schema extensions;
    create table auth.users(id uuid primary key, email text unique, raw_user_meta_data jsonb);
    create function auth.uid() returns uuid language sql stable as $$ select nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
    grant usage on schema auth to anon, authenticated, supabase_auth_admin;
    grant insert on auth.users to supabase_auth_admin;`);
  const files = (await readdir(new URL('../supabase/migrations/', import.meta.url))).filter((name) => name.endsWith('.sql')).sort();
  const versions = files.map((name) => name.slice(0, 14));
  assert.ok(files.every((name) => /^\d{14}_[a-z0-9_]+\.sql$/.test(name)), 'Migration names follow <version>_<name>.sql');
  assert.equal(new Set(versions).size, versions.length, 'Migration versions are unique');
  let applied = 0;
  for (const name of files) {
    if (needsPgCron(name)) continue;
    try {
      await db.exec(await readFile(new URL(`../supabase/migrations/${name}`, import.meta.url), 'utf8'));
    } catch (error) {
      throw new Error(`${name} failed on a fresh database: ${error.message}`);
    }
    applied++;
  }
  const tables = (await db.query(`select string_agg(tablename, ',' order by tablename) t from pg_tables where schemaname='public'`)).rows[0].t;
  assert.equal(tables, 'account_profiles,connection_invitations,connection_preferences,connection_signals,person_connections,personal_schedule,personal_status,quick_messages,signup_allowed_emails');
  const limiter = (await db.query(`select count(*) n from pg_proc where proname='consume_username_login_attempt' and pronargs=2`)).rows[0].n;
  assert.equal(Number(limiter), 1, 'Latest limiter signature is installed');
  console.log(`Migrations replay: ${applied} of ${files.length} migrations applied in order (pg_cron migration skipped).`);
} finally { await db.close(); }
