const reply = (body, status = 200) => new Response(JSON.stringify(body), {
  status, headers: { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
});
const TYPES = new Set(['message', 'status', 'status_end', 'activity']);
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const FCM_SCOPE = 'https://www.googleapis.com/auth/firebase.messaging';

const base64url = (bytes) => btoa(String.fromCharCode(...new Uint8Array(bytes)))
  .replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const encodeJson = (value) => base64url(new TextEncoder().encode(JSON.stringify(value)));

/** Same length and same bytes, without stopping at the first difference. */
function sameSecret(given, expected) {
  if (typeof given !== 'string' || typeof expected !== 'string' || !expected) return false;
  const a = new TextEncoder().encode(given);
  const b = new TextEncoder().encode(expected);
  let diff = a.length ^ b.length;
  for (let i = 0; i < Math.max(a.length, b.length); i++) diff |= (a[i] ?? 0) ^ (b[i] ?? 0);
  return diff === 0;
}

async function signedAssertion(account, nowSeconds) {
  const pem = account.private_key.replace(/-----(BEGIN|END) PRIVATE KEY-----/g, '').replace(/\s+/g, '');
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey('pkcs8', der, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']);
  const unsigned = `${encodeJson({ alg: 'RS256', typ: 'JWT' })}.${encodeJson({
    iss: account.client_email, scope: FCM_SCOPE, aud: account.token_uri ?? 'https://oauth2.googleapis.com/token',
    iat: nowSeconds, exp: nowSeconds + 3600,
  })}`;
  const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(unsigned));
  return `${unsigned}.${base64url(signature)}`;
}

/**
 * Sends one notification to every device of the given people through Firebase Cloud Messaging.
 * Called only by the database (pg_net) with the shared secret; never by the app.
 */
export function createSendPushHandler({ url, serviceKey, secret, serviceAccount }, request = fetch, now = () => Date.now()) {
  let cached = null; // { token, expires }
  async function accessToken(account) {
    if (cached && cached.expires > now() + 60_000) return cached.token;
    const assertion = await signedAssertion(account, Math.floor(now() / 1000));
    const response = await request(account.token_uri ?? 'https://oauth2.googleapis.com/token', {
      method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }).toString(),
      signal: AbortSignal.timeout(10000),
    });
    if (!response.ok) throw new Error(`OAuth ${response.status}`);
    const { access_token: token, expires_in: seconds } = await response.json();
    cached = { token, expires: now() + (Number(seconds) || 3600) * 1000 };
    return token;
  }
  const rest = (path, init = {}) => request(`${url}/rest/v1/${path}`, {
    ...init, headers: { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, ...init.headers },
    signal: AbortSignal.timeout(10000),
  });

  return async (req) => {
    if (req.method !== 'POST') return reply({ message: 'Método no permitido.' }, 405);
    if (!sameSecret(req.headers.get('x-push-secret'), secret)) return reply({ message: 'No autorizado.' }, 401);
    let account;
    try { account = JSON.parse(serviceAccount ?? ''); } catch { account = null; }
    if (!url || !serviceKey || !account?.private_key || !account?.client_email || !account?.project_id) {
      return reply({ message: 'Servicio no configurado.' }, 503);
    }
    const text = await req.text();
    if (text.length > 16384) return reply({ message: 'Solicitud demasiado grande.' }, 413);
    let body;
    try { body = JSON.parse(text); } catch { return reply({ message: 'Solicitud inválida.' }, 400); }
    const { type, recipients, data } = body ?? {};
    if (!TYPES.has(type) || !Array.isArray(recipients) || recipients.length > 500
        || !recipients.every((id) => typeof id === 'string' && UUID.test(id))
        || (data !== undefined && (typeof data !== 'object' || data === null || Array.isArray(data)))) {
      return reply({ message: 'Solicitud inválida.' }, 400);
    }
    if (recipients.length === 0) return reply({ sent: 0, removed: 0 });

    const found = await rest(`push_tokens?select=token,user_id&user_id=in.(${recipients.join(',')})`);
    if (!found.ok) return reply({ message: 'No se pudieron leer los dispositivos.' }, 502);
    const devices = await found.json();
    if (devices.length === 0) return reply({ sent: 0, removed: 0 });

    // FCM data values must be strings. The app builds the visible notification itself, so it can
    // honour each account's alert switches.
    const payload = { type };
    for (const [key, value] of Object.entries(data ?? {})) {
      if (value !== null && value !== undefined) payload[key] = String(value).slice(0, 1000);
    }
    let token;
    try { token = await accessToken(account); } catch { return reply({ message: 'No se pudo autenticar con Firebase.' }, 502); }

    let sent = 0;
    let removed = 0;
    for (const device of devices) {
      const response = await request(`https://fcm.googleapis.com/v1/projects/${account.project_id}/messages:send`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
        body: JSON.stringify({ message: { token: device.token, data: payload, android: { priority: 'HIGH', ttl: '3600s' } } }),
        signal: AbortSignal.timeout(10000),
      }).catch(() => null);
      if (response?.ok) { sent++; continue; }
      // Uninstalled apps and stale tokens: forget them so they are not tried again.
      let code = '';
      try { code = JSON.stringify(await response?.json()); } catch { /* ignore */ }
      if (response?.status === 404 || code.includes('UNREGISTERED') || code.includes('registration-token-not-registered')) {
        await rest(`push_tokens?token=eq.${encodeURIComponent(device.token)}`, { method: 'DELETE' }).catch(() => null);
        removed++;
      }
    }
    return reply({ sent, removed });
  };
}
