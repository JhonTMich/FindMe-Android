import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createDeleteAccountHandler } from '../supabase/functions/delete-account/handler.mjs';

const config = { url: 'https://example.supabase.co', serviceKey: 'server-secret', anonKey: 'public-key' };
const json = (value, status = 200) => new Response(JSON.stringify(value), { status });
const req = (body = { password: 'correct-password' }, token = 'user-token') => new Request('https://example.test', {
  method: 'POST', body: JSON.stringify(body), headers: token ? { authorization: `Bearer ${token}` } : {},
});
function server({ session = true, limited = false, passwordOk = true } = {}) {
  const calls = [];
  return { calls, request: async (url, options = {}) => {
    calls.push({ url, method: options.method ?? 'GET', options });
    if (url.endsWith('/auth/v1/user')) return session ? json({ id: 'own-id', email: 'private@example.test' }) : json({}, 401);
    if (url.includes('/rpc/')) return json(!limited);
    if (url.includes('/token?')) return passwordOk ? json({ access_token: 'fresh' }) : json({ error: 'invalid_grant' }, 400);
    if (url.includes('/admin/users/')) return json({});
    throw new Error(`Unexpected ${url}`);
  } };
}
const deleted = (calls) => calls.some((c) => c.method === 'DELETE');

test('deletes only the caller resolved from their own token, after checking the password', async () => {
  const mock = server();
  const response = await createDeleteAccountHandler(config, mock.request)(req());
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { deleted: true });
  assert.equal(mock.calls[0].options.headers.Authorization, 'Bearer user-token');
  assert.deepEqual(JSON.parse(mock.calls.find((c) => c.url.includes('/token?')).options.body),
    { email: 'private@example.test', password: 'correct-password' });
  const removal = mock.calls.find((c) => c.method === 'DELETE');
  assert.ok(removal.url.endsWith('/auth/v1/admin/users/own-id'));
  assert.equal(removal.options.headers.Authorization, 'Bearer server-secret');
});

test('a wrong password, a missing session or the rate limit never delete anything', async () => {
  for (const [options, request, status] of [
    [{ passwordOk: false }, req(), 401],
    [{ session: false }, req(), 401],
    [{ limited: true }, req(), 429],
    [{}, req({ password: '' }), 401],
    [{}, req({ password: 'x'.repeat(5000) }), 401],
  ]) {
    const mock = server(options);
    assert.equal((await createDeleteAccountHandler(config, mock.request)(request)).status, status);
    assert.equal(deleted(mock.calls), false);
  }
});

test('requests without a bearer token are refused before any upstream call', async () => {
  const handler = createDeleteAccountHandler(config, () => { throw new Error('Should not call server'); });
  assert.equal((await handler(req(undefined, null))).status, 401);
});

test('upstream failures report unavailability without details', async () => {
  const handler = createDeleteAccountHandler(config, async () => json({ error: 'server-secret detail' }, 500));
  const response = await handler(req());
  assert.equal(response.status, 503);
  assert.equal((await response.text()).includes('server-secret'), false);
});
