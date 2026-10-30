import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createLoginHandler } from '../supabase/functions/username-login/handler.mjs';

const config = { url: 'https://example.supabase.co', serviceKey: 'server-secret', anonKey: 'public-key' };
const json = (value, status = 200) => new Response(JSON.stringify(value), { status });
const login = (username = 'JhonTMich', password = 'test-password') => new Request('https://example.test', {
  method: 'POST', body: JSON.stringify({ username, password }),
});

test('successful login normalizes username and returns only an authenticated session', async () => {
  const calls = [];
  const handler = createLoginHandler(config, async (url, options) => {
    calls.push(url);
    if (url.includes('/rpc/')) return json(true);
    if (url.includes('/account_profiles?')) { assert.ok(url.includes('username=eq.jhontmich')); return json([{ id: 'own' }]); }
    if (url.includes('/admin/users/own')) return json({ email: 'private@example.test' });
    assert.equal(options.headers.apikey, 'public-key');
    assert.deepEqual(JSON.parse(options.body), { email: 'private@example.test', password: 'test-password' });
    return json({ access_token: 'session', refresh_token: 'refresh' });
  });
  const response = await handler(login());
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.deepEqual(await response.json(), { access_token: 'session', refresh_token: 'refresh' });
  assert.equal(calls.length, 4);
});

test('unknown user and wrong password have the same response without email disclosure', async () => {
  for (const exists of [true, false]) {
    const handler = createLoginHandler(config, async (url) => {
      if (url.includes('/rpc/')) return json(true);
      if (url.includes('/account_profiles?')) return json(exists ? [{ id: 'own' }] : []);
      if (url.includes('/admin/')) return json({ email: 'private@example.test' });
      return json({ msg: 'sensitive upstream error' }, 400);
    });
    const response = await handler(login());
    assert.equal(response.status, 401);
    assert.deepEqual(await response.json(), { message: 'Usuario o contraseña incorrectos.' });
  }
});

test('rate limited request never looks up a user or checks a password', async () => {
  let calls = 0;
  const handler = createLoginHandler(config, async () => { calls++; return json(false); });
  const response = await handler(login());
  assert.equal(response.status, 429);
  assert.equal(calls, 1);
});

test('invalid or oversized bodies do not reach privileged services', async () => {
  const handler = createLoginHandler(config, () => { throw new Error('Should not call server'); });
  for (const req of [login('bad@email.test'), login('a'), login('valid', ''), login('valid', 'x'.repeat(5000)),
    new Request('https://example.test', { method: 'POST', body: 'not-json' })]) {
    assert.equal((await handler(req)).status, 400);
  }
});

test('server failure does not leak privileged errors or fall through rate limiting', async () => {
  const handler = createLoginHandler(config, async () => json({ error: 'server-secret' }, 500));
  const response = await handler(login());
  assert.equal(response.status, 503);
  assert.equal((await response.text()).includes('server-secret'), false);
});
