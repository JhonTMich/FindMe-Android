// Identifies the caller's network for rate limiting without storing the raw address.
// Cloudflare sets cf-connecting-ip itself; x-forwarded-for is only a fallback.
export async function clientKey(req, secret) {
  const address = req.headers.get('cf-connecting-ip')?.trim()
    || req.headers.get('x-forwarded-for')?.split(',')[0]?.trim();
  if (!address || address.length > 64 || !secret) return null;
  const key = await crypto.subtle.importKey('raw', new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  const mac = await crypto.subtle.sign('HMAC', key, new TextEncoder().encode(`client:${address}`));
  return Array.from(new Uint8Array(mac), (b) => b.toString(16).padStart(2, '0')).join('');
}
