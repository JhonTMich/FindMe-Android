const invalidCredentials = { message: 'Usuario o contraseña incorrectos.' };
const headers = { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' };
const reply = (body, status = 200, extra = {}) => new Response(JSON.stringify(body), { status, headers: { ...headers, ...extra } });

export function createLoginHandler({ url, serviceKey, anonKey }, request = fetch) {
  return async (req) => {
    if (req.method !== 'POST') return reply({ message: 'Método no permitido.' }, 405);
    if (!url || !serviceKey || !anonKey) return reply({ message: 'Servicio no disponible.' }, 503);
    if (Number(req.headers.get('content-length')) > 4096) return reply(invalidCredentials, 400);
    try {
      // Bound the stream even if Content-Length is absent or misleading.
      const reader = req.body?.getReader();
      if (!reader) return reply(invalidCredentials, 400);
      let size = 0;
      const chunks = [];
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        size += value.length;
        if (size > 4096) { await reader.cancel(); return reply(invalidCredentials, 400); }
        chunks.push(value);
      }
      const bytes = new Uint8Array(size);
      let offset = 0;
      for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.length; }
      let body;
      try { body = JSON.parse(new TextDecoder().decode(bytes)); }
      catch { return reply(invalidCredentials, 400); }
      const username = typeof body?.username === 'string' ? body.username.trim().toLowerCase() : '';
      const password = body?.password;
      if (!/^[a-z0-9_]{3,24}$/.test(username) || typeof password !== 'string' || !password || password.length > 1024) {
        return reply(invalidCredentials, 400);
      }
      const adminHeaders = { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, 'Content-Type': 'application/json' };
      const serverRequest = (path, options = {}) => request(`${url}${path}`, { ...options, headers: adminHeaders, signal: AbortSignal.timeout(12000) });
      const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(username));
      const hash = Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
      const limit = await serverRequest('/rest/v1/rpc/consume_username_login_attempt', { method: 'POST', body: JSON.stringify({ username_hash: hash }) });
      if (!limit.ok) return reply({ message: 'Servicio no disponible.' }, 503);
      if (await limit.json() !== true) return reply({ message: 'Demasiados intentos. Espera unos minutos y vuelve a intentarlo.' }, 429, { 'Retry-After': '300' });

      const profiles = await serverRequest(`/rest/v1/account_profiles?select=id&username=eq.${encodeURIComponent(username)}&limit=1`);
      if (!profiles.ok) return reply({ message: 'Servicio no disponible.' }, 503);
      const profile = (await profiles.json())[0];
      let email;
      if (profile) {
        const user = await serverRequest(`/auth/v1/admin/users/${encodeURIComponent(profile.id)}`);
        if (!user.ok) return reply({ message: 'Servicio no disponible.' }, 503);
        email = (await user.json()).email;
      }
      // Also call Auth for missing usernames, using an address that cannot be registered.
      const session = await request(`${url}/auth/v1/token?grant_type=password`, {
        method: 'POST', headers: { apikey: anonKey, 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email || 'missing-user@invalid.invalid', password }),
        signal: AbortSignal.timeout(12000),
      });
      if (session.status === 429) return reply({ message: 'Demasiados intentos. Inténtalo más tarde.' }, 429);
      if (session.status >= 500) return reply({ message: 'Servicio no disponible.' }, 503);
      if (!session.ok || !email) return reply(invalidCredentials, 401);
      // Email is returned only as part of the authenticated user's own session.
      return reply(await session.json());
    } catch {
      // Never log request bodies, passwords, tokens or upstream error details.
      return reply({ message: 'Servicio no disponible.' }, 503);
    }
  };
}
