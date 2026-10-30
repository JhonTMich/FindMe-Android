import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createSignupHandler } from '../supabase/functions/authorized-signup/handler.mjs';

const config = { url: 'https://example.supabase.co', serviceKey: 'server-key', anonKey: 'public-key' };
const json = (body, status = 200) => new Response(JSON.stringify(body), { status });
const input = { email: 'allowed@example.test', name: 'Chosen Name', username: 'My_User', password: 'local-test-password', timezone: 'America/Lima' };
const req = (data = input) => new Request('https://example.test', { method: 'POST', body: JSON.stringify(data) });
function server({ entry = { enabled: true, expires_at: '2099-01-01', used_at: null }, taken = false, autoConfirm = false, smtpFails = false } = {}) {
  const calls = [];
  return { calls, request: async (url, options) => {
    calls.push({ url, options });
    if (url.includes('/rpc/')) return json(true);
    if (url.includes('/signup_allowed_emails?')) return json(entry ? [entry] : []);
    if (url.includes('/account_profiles?')) return json(taken ? [{ id: 'existing' }] : []);
    if (url.endsWith('/settings')) return json({ mailer_autoconfirm: autoConfirm, disable_signup: false });
    assert.equal(options.headers.apikey, 'public-key');
    return smtpFails ? json({ error: 'sensitive details' }, 500) : json({ id: 'created', email: input.email });
  } };
}
test('registration forwards only self-chosen data to native Auth and requires confirmation', async () => {
  const mock = server();
  const result = await createSignupHandler(config, mock.request)(req());
  assert.equal(result.status, 200);
  assert.equal(new URL(mock.calls.at(-1).url).searchParams.get('redirect_to'), 'findme://auth/confirmed');
  assert.deepEqual(await result.json(), { needs_confirmation: true });
  assert.equal(result.headers.get('cache-control'), 'no-store');
  assert.deepEqual(JSON.parse(mock.calls.at(-1).options.body), {
    email: input.email, password: input.password, data: { username: 'my_user', display_name: input.name, timezone: input.timezone },
  });
});
test('missing, disabled, expired and consumed authorizations never reach Auth signup', async () => {
  for (const entry of [null, { enabled: false }, { enabled: true, expires_at: '2000-01-01' }, { enabled: true, used_at: '2026-01-01' }]) {
    const mock = server({ entry });
    assert.equal((await createSignupHandler(config, mock.request)(req())).status, 403);
    assert.equal(mock.calls.some((c) => c.url.includes('/signup?')), false);
  }
});
test('occupied username leaves authorization and Auth untouched', async () => {
  const mock = server({ taken: true });
  assert.equal((await createSignupHandler(config, mock.request)(req())).status, 409);
  assert.equal(mock.calls.some((c) => c.url.includes('/signup?')), false);
});
test('resend accepts consumed authorization and sends no password', async () => {
  const mock = server({ entry: { enabled: true, used_at: '2026-01-01' } });
  assert.equal((await createSignupHandler(config, mock.request)(req({ action: 'resend', email: input.email }))).status, 200);
  assert.ok(mock.calls.at(-1).url.includes('/resend?'));
  assert.deepEqual(JSON.parse(mock.calls.at(-1).options.body), { type: 'signup', email: input.email });
});
test('registration refuses a project with email confirmation disabled', async () => {
  const mock = server({ autoConfirm: true });
  assert.equal((await createSignupHandler(config, mock.request)(req())).status, 503);
  assert.equal(mock.calls.some((c) => c.url.includes('/signup?')), false);
});
test('invalid input and oversized requests do not reach privileged services', async () => {
  const handler = createSignupHandler(config, () => { throw new Error('Should not call service'); });
  for (const data of [{ ...input, email: 'invalid' }, { ...input, username: 'bad user' }, { ...input, password: 'short' }, { ...input, password: 'x'.repeat(5000) }]) {
    assert.equal((await handler(req(data))).status, 400);
  }
});
test('SMTP failure returns no success, password, email or upstream details', async () => {
  const mock = server({ smtpFails: true });
  const result = await createSignupHandler(config, mock.request)(req());
  assert.equal(result.status, 400);
  const body = await result.text();
  for (const sensitive of [input.password, input.email, 'sensitive details']) assert.equal(body.includes(sensitive), false);
});
test('rate limit failure stops registration before looking up any email', async () => {
  let calls = 0;
  const result = await createSignupHandler(config, async () => { calls++; return json(false); })(req());
  assert.equal(result.status, 429);
  assert.equal(calls, 1);
});
