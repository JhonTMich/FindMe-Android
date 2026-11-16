const headers = { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' };
const reply = (body, status = 200, extra = {}) => new Response(JSON.stringify(body), { status, headers: { ...headers, ...extra } });
const unavailable = () => reply({ message: 'No se pudo eliminar la cuenta. Inténtalo más tarde.' }, 503);
const wrongPassword = () => reply({ message: 'La contraseña no es correcta.' }, 401);

/**
 * Deletes the caller's own account after re-checking the password. Removing the Auth user
 * cascades to the profile, schedule, status, quick messages, connections and messages.
 */
export function createDeleteAccountHandler({ url, serviceKey, anonKey }, request = fetch) {
  return async (req) => {
    if (req.method !== 'POST') return reply({ message: 'Método no permitido.' }, 405);
    if (!url || !serviceKey || !anonKey) return unavailable();
    const token = req.headers.get('authorization')?.match(/^Bearer (\S+)$/)?.[1];
    if (!token) return reply({ message: 'Inicia sesión para continuar.' }, 401);
    try {
      const reader = req.body?.getReader();
      if (!reader) return wrongPassword();
      let size = 0;
      const parts = [];
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.length;
        if (size > 4096) { await reader.cancel(); return wrongPassword(); }
        parts.push(value);
      }
      let body;
      try { body = JSON.parse(new TextDecoder().decode(Uint8Array.from(parts.flatMap((part) => [...part])))); }
      catch { return wrongPassword(); }
      const password = body?.password;
      if (typeof password !== 'string' || !password || password.length > 1024) return wrongPassword();

      const timeout = () => AbortSignal.timeout(12000);
      // Identify the caller only from their own session token.
      const me = await request(`${url}/auth/v1/user`, { headers: { apikey: anonKey, Authorization: `Bearer ${token}` }, signal: timeout() });
      if (me.status === 401 || me.status === 403) return reply({ message: 'Inicia sesión para continuar.' }, 401);
      if (!me.ok) return unavailable();
      const { id, email } = await me.json();
      if (typeof id !== 'string' || typeof email !== 'string') return unavailable();

      const adminHeaders = { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, 'Content-Type': 'application/json' };
      const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`delete:${id}`));
      const hash = Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
      const limit = await request(`${url}/rest/v1/rpc/consume_username_login_attempt`, {
        method: 'POST', headers: adminHeaders, body: JSON.stringify({ username_hash: hash }), signal: timeout(),
      });
      if (!limit.ok) return unavailable();
      if (await limit.json() !== true) return reply({ message: 'Demasiados intentos. Espera unos minutos.' }, 429, { 'Retry-After': '300' });

      // A stolen session alone must not be enough to destroy the account.
      const check = await request(`${url}/auth/v1/token?grant_type=password`, {
        method: 'POST', headers: { apikey: anonKey, 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }), signal: timeout(),
      });
      if (check.status >= 500) return unavailable();
      if (!check.ok) return wrongPassword();

      const removed = await request(`${url}/auth/v1/admin/users/${encodeURIComponent(id)}`, {
        method: 'DELETE', headers: adminHeaders, signal: timeout(),
      });
      if (!removed.ok) return unavailable();
      return reply({ deleted: true });
    } catch {
      // Never log tokens, passwords or upstream error details.
      return unavailable();
    }
  };
}
