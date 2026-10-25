import { createClient } from '@supabase/supabase-js';

export class ApiError extends Error {
  constructor(public status: number, public code: string, message = code, public extra: Record<string, unknown> = {}) {
    super(message);
  }
}
export const cors = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, apikey, content-type, idempotency-key',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};
export function json(value: unknown, status = 200) {
  return new Response(JSON.stringify(value), { status, headers: { ...cors, 'Content-Type': 'application/json', 'Cache-Control': 'no-store' } });
}
export function failure(error: unknown) {
  // No volcar mensajes del proveedor, tokens, prompts ni conversaciones a logs/respuestas.
  if (error instanceof ApiError) return json({ error: error.code, message: error.message, ...error.extra }, error.status);
  return json({ error: 'internal_error', message: 'No se pudo completar la solicitud.' }, 500);
}
export function env(name: string) {
  const value = Deno.env.get(name);
  if (!value) throw new ApiError(503, 'server_not_configured');
  return value;
}
export async function clients(req: Request) {
  const authorization = req.headers.get('Authorization') ?? '';
  if (!/^Bearer \S+$/i.test(authorization)) throw new ApiError(401, 'unauthorized');
  const url = env('SUPABASE_URL');
  const user = createClient(url, env('SUPABASE_ANON_KEY'), {
    global: { headers: { Authorization: authorization } },
    auth: { persistSession: false, autoRefreshToken: false },
  });
  const { data, error } = await user.auth.getUser(authorization.slice(7));
  if (error || !data.user) throw new ApiError(401, 'unauthorized');
  const admin = createClient(url, env('SUPABASE_SERVICE_ROLE_KEY'), {
    auth: { persistSession: false, autoRefreshToken: false },
  });
  return { user, admin, userId: data.user.id };
}
export async function body(req: Request, maxBytes = 1_000_000): Promise<Record<string, unknown>> {
  if (!req.headers.get('Content-Type')?.toLowerCase().startsWith('application/json')) {
    throw new ApiError(400, 'invalid_content_type');
  }
  // Contar los bytes reales: Content-Length puede faltar o ser incorrecto.
  const reader = req.body?.getReader();
  if (!reader) throw new ApiError(400, 'invalid_body');
  const chunks: Uint8Array[] = []; let size = 0;
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    size += value.byteLength;
    if (size > maxBytes) { await reader.cancel(); throw new ApiError(413, 'payload_too_large'); }
    chunks.push(value);
  }
  const bytes = new Uint8Array(size); let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  try {
    const value = JSON.parse(new TextDecoder().decode(bytes));
    if (!value || Array.isArray(value) || typeof value !== 'object') throw new Error();
    return value;
  } catch { throw new ApiError(400, 'invalid_json'); }
}
export async function hash(value: unknown) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(value)));
  return Array.from(new Uint8Array(digest), b => b.toString(16).padStart(2, '0')).join('');
}
