// node --test scripts/test_send_push.mjs
// Exercises the send-push Edge Function with Firebase and Supabase replaced by local stand-ins.
import test from 'node:test';
import assert from 'node:assert/strict';
import { createSendPushHandler } from '../supabase/functions/send-push/handler.mjs';

const { privateKey, publicKey } = await crypto.subtle.generateKey(
  { name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['sign', 'verify']);
const pkcs8 = Buffer.from(await crypto.subtle.exportKey('pkcs8', privateKey)).toString('base64');
const account = JSON.stringify({ project_id: 'near-test', client_email: 'push@near-test.iam.gserviceaccount.com',
  private_key: `-----BEGIN PRIVATE KEY-----\n${pkcs8.match(/.{1,64}/g).join('\n')}\n-----END PRIVATE KEY-----\n` });
const a = '00000000-0000-0000-0000-000000000001';
const b = '00000000-0000-0000-0000-000000000002';

function fakeServices({ devices = [], failing = new Set() } = {}) {
  const calls = [];
  const request = async (url, init = {}) => {
    calls.push({ url, init });
    if (url === 'https://oauth2.googleapis.com/token') return Response.json({ access_token: 'oauth-token', expires_in: 3600 });
    if (url.includes('/rest/v1/push_tokens?select=')) return Response.json(devices);
    if (url.includes('/rest/v1/push_tokens?token=eq.')) return new Response(null, { status: 204 });
    if (url.includes('fcm.googleapis.com')) {
      const { message } = JSON.parse(init.body);
      return failing.has(message.token)
        ? Response.json({ error: { status: 'NOT_FOUND', details: [{ errorCode: 'UNREGISTERED' }] } }, { status: 404 })
        : Response.json({ name: 'projects/near-test/messages/1' });
    }
    throw new Error(`Unexpected request ${url}`);
  };
  return { calls, request };
}
const handler = (services) => createSendPushHandler(
  { url: 'https://db.example.test', serviceKey: 'service', secret: 's3cret', serviceAccount: account }, services.request);
const post = (body, secret = 's3cret') => new Request('https://fn.example.test/send-push', {
  method: 'POST', headers: { 'x-push-secret': secret, 'Content-Type': 'application/json' }, body: JSON.stringify(body) });

test('rejects calls without the shared secret', async () => {
  const services = fakeServices();
  assert.equal((await handler(services)(post({ type: 'message', recipients: [a] }, 'wrong'))).status, 401);
  assert.equal((await handler(services)(post({ type: 'message', recipients: [a] }, ''))).status, 401);
  assert.equal(services.calls.length, 0);
});

test('rejects unknown types and malformed recipients', async () => {
  const services = fakeServices();
  assert.equal((await handler(services)(post({ type: 'anything', recipients: [a] }))).status, 400);
  assert.equal((await handler(services)(post({ type: 'message', recipients: ["1) or true"] }))).status, 400);
});

test('sends a data message to every device and forgets uninstalled ones', async () => {
  const services = fakeServices({
    devices: [{ token: 'device-a', user_id: a }, { token: 'device-old', user_id: a }, { token: 'device-b', user_id: b }],
    failing: new Set(['device-old']),
  });
  const response = await handler(services)(post({ type: 'status', recipients: [a, b], data: { name: 'Ana', location: 'Biblioteca', message: null } }));
  assert.deepEqual(await response.json(), { sent: 2, removed: 1 });

  const lookup = services.calls.find((call) => call.url.includes('select=token'));
  assert.ok(lookup.url.endsWith(`user_id=in.(${a},${b})`), 'Looks up only the requested people');
  assert.equal(lookup.init.headers.Authorization, 'Bearer service');

  const messages = services.calls.filter((call) => call.url.includes('fcm.googleapis.com')).map((call) => JSON.parse(call.init.body).message);
  assert.deepEqual(messages[0].data, { type: 'status', name: 'Ana', location: 'Biblioteca' }, 'Data values are strings, nulls dropped');
  assert.equal(messages[0].android.priority, 'HIGH');
  assert.ok(services.calls.some((call) => call.url.endsWith('push_tokens?token=eq.device-old') && call.init.method === 'DELETE'));

  // The OAuth assertion is a valid RS256 JWT for the FCM scope, signed by the service account.
  const oauth = services.calls.find((call) => call.url === 'https://oauth2.googleapis.com/token');
  const jwt = new URLSearchParams(oauth.init.body).get('assertion');
  const [header, claims, signature] = jwt.split('.');
  const decoded = JSON.parse(Buffer.from(claims, 'base64url').toString());
  assert.equal(decoded.scope, 'https://www.googleapis.com/auth/firebase.messaging');
  assert.equal(decoded.iss, 'push@near-test.iam.gserviceaccount.com');
  assert.ok(await crypto.subtle.verify('RSASSA-PKCS1-v1_5', publicKey, Buffer.from(signature, 'base64url'),
    new TextEncoder().encode(`${header}.${claims}`)));
});

test('reuses the Firebase access token between calls', async () => {
  const services = fakeServices({ devices: [{ token: 'device-a', user_id: a }] });
  const send = handler(services);
  await send(post({ type: 'message', recipients: [a], data: { message: 'Hola' } }));
  await send(post({ type: 'message', recipients: [a], data: { message: 'Hola otra vez' } }));
  assert.equal(services.calls.filter((call) => call.url === 'https://oauth2.googleapis.com/token').length, 1);
});

test('does nothing when nobody has a device', async () => {
  const services = fakeServices();
  assert.deepEqual(await (await handler(services)(post({ type: 'activity', recipients: [a] }))).json(), { sent: 0, removed: 0 });
  assert.ok(!services.calls.some((call) => call.url.includes('fcm.googleapis.com')));
});
