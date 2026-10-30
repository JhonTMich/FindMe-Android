const headers = { 'Content-Type': 'application/json', 'Cache-Control': 'no-store' };
const reply = (body, status = 200, extra = {}) => new Response(JSON.stringify(body), { status, headers: { ...headers, ...extra } });
const unavailable = () => reply({ message: 'No se pudo completar el registro. Inténtalo más tarde.' }, 503);

export function createSignupHandler({ url, serviceKey, anonKey }, request = fetch) {
  return async (req) => {
    if (req.method !== 'POST') return reply({ message: 'Método no permitido.' }, 405);
    if (!url || !serviceKey || !anonKey) return unavailable();
    try {
      const reader = req.body?.getReader();
      if (!reader) return reply({ message: 'Revisa los datos del registro.' }, 400);
      let size = 0;
      const parts = [];
      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;
        size += value.length;
        if (size > 4096) { await reader.cancel(); return reply({ message: 'Revisa los datos del registro.' }, 400); }
        parts.push(value);
      }
      const bytes = new Uint8Array(size);
      let offset = 0;
      for (const part of parts) { bytes.set(part, offset); offset += part.length; }
      let body;
      try { body = JSON.parse(new TextDecoder().decode(bytes)); }
      catch { return reply({ message: 'Revisa los datos del registro.' }, 400); }
      const email = typeof body?.email === 'string' ? body.email.trim().toLowerCase() : '';
      const resend = body?.action === 'resend';
      const username = typeof body?.username === 'string' ? body.username.trim().toLowerCase() : '';
      const name = typeof body?.name === 'string' ? body.name.trim() : '';
      if (email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) || (!resend && (
        !/^[a-z0-9_]{3,24}$/.test(username) || name.length < 1 || name.length > 80 || name.toLowerCase() === 'mi cuenta' ||
        typeof body.password !== 'string' || body.password.length < 8 || body.password.length > 128 ||
        typeof body.timezone !== 'string' || body.timezone.length > 80
      ))) return reply({ message: 'Revisa el correo, el usuario y la contraseña (de 8 a 128 caracteres).' }, 400);

      const adminHeaders = { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, 'Content-Type': 'application/json' };
      const serverRequest = (path, options = {}) => request(`${url}${path}`, { ...options, headers: adminHeaders, signal: AbortSignal.timeout(12000) });
      const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(`signup:${email}`));
      const hash = Array.from(new Uint8Array(digest), (b) => b.toString(16).padStart(2, '0')).join('');
      const limit = await serverRequest('/rest/v1/rpc/consume_username_login_attempt', { method: 'POST', body: JSON.stringify({ username_hash: hash }) });
      if (!limit.ok) return unavailable();
      if (await limit.json() !== true) return reply({ message: 'Demasiados intentos. Espera unos minutos.' }, 429, { 'Retry-After': '300' });
      const allowed = await serverRequest(`/rest/v1/signup_allowed_emails?select=enabled,expires_at,used_at&email=eq.${encodeURIComponent(email)}&limit=1`);
      if (!allowed.ok) return unavailable();
      const entry = (await allowed.json())[0];
      // A consumed authorization can resend confirmation, but cannot create another account.
      if (!entry || !entry.enabled || (!resend && (entry.used_at || Date.parse(entry.expires_at) <= Date.now()))) {
        return reply({ message: 'No se pudo registrar este correo. Revisa tu autorización o inicia sesión si ya tienes cuenta.' }, 403);
      }
      if (!resend) {
        const profiles = await serverRequest(`/rest/v1/account_profiles?select=id&username=eq.${encodeURIComponent(username)}&limit=1`);
        if (!profiles.ok) return unavailable();
        if ((await profiles.json()).length) return reply({ message: 'Ese nombre de usuario ya está ocupado. Elige otro.' }, 409);
      }
      // Require proof of email ownership. Do not silently accept a project with autoconfirm on.
      const settingsResponse = await request(`${url}/auth/v1/settings`, { headers: { apikey: anonKey }, signal: AbortSignal.timeout(12000) });
      if (!settingsResponse.ok) return unavailable();
      const settings = await settingsResponse.json();
      if (settings.mailer_autoconfirm !== false || settings.disable_signup === true) return unavailable();
      const result = await request(`${url}/auth/v1/${resend ? 'resend' : 'signup'}`, {
        method: 'POST', headers: { apikey: anonKey, 'Content-Type': 'application/json' },
        body: JSON.stringify(resend ? { type: 'signup', email } : {
          email, password: body.password, data: { display_name: name, username, timezone: body.timezone },
        }), signal: AbortSignal.timeout(15000),
      });
      if (result.status === 429) return reply({ message: 'Espera unos minutos antes de solicitar otro correo.' }, 429);
      if (!result.ok) return reply({ message: 'No se pudo completar el registro o enviar la confirmación. Revisa tus datos y vuelve a intentarlo. Si persiste, contacta al responsable de FindMe.' }, 400);
      return reply({ needs_confirmation: true });
    } catch {
      // Never log emails, names, passwords, tokens or upstream error details.
      return unavailable();
    }
  };
}
