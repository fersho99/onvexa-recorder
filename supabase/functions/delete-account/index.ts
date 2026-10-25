import { ApiError, body, clients, cors, failure, json } from '../_shared/http.ts';

export async function handler(req: Request) {
  if (req.method === 'OPTIONS') return new Response(null, { headers: cors });
  if (req.method !== 'POST') return json({ error: 'method_not_allowed', message: 'Use POST.' }, 405);
  try {
    const { admin, userId } = await clients(req);
    const request = await body(req, 1024);
    if (request.confirm !== 'DELETE_MY_ACCOUNT') throw new ApiError(400, 'confirmation_required');
    // Nunca aceptar user_id del cuerpo. Bloquea escrituras antes de vaciar los buckets.
    const { error: lockError } = await admin.from('account_deletions').upsert({ user_id: userId });
    if (lockError) throw new ApiError(503, 'deletion_unavailable');
    const deadline = Date.now() + 100_000;
    async function clearPrefix(bucket: string, prefix: string): Promise<void> {
      while (true) {
        if (Date.now() > deadline) throw new ApiError(503, 'deletion_pending', 'Reintente para continuar el borrado.');
        // Siempre pagina cero: los elementos anteriores ya fueron eliminados.
        const { data, error } = await admin.storage.from(bucket).list(prefix, { limit: 100, offset: 0, sortBy: { column: 'name', order: 'asc' } });
        if (error) throw new ApiError(502, 'storage_list_failed');
        if (!data?.length) return;
        const paths: string[] = [];
        for (const item of data) {
          const path = prefix + '/' + item.name;
          if (!item.id) await clearPrefix(bucket, path); else paths.push(path);
        }
        if (paths.length) {
          const { error } = await admin.storage.from(bucket).remove(paths);
          if (error) throw new ApiError(502, 'storage_delete_failed');
        }
      }
    }
    for (const bucket of ['audio','attachments']) await clearPrefix(bucket, userId);
    // CASCADE borra profiles y sus filas hijas. Solo despues de limpiar archivos.
    const { error } = await admin.auth.admin.deleteUser(userId);
    if (error) throw new ApiError(502, 'user_delete_failed');
    return json({ deleted: true });
  } catch (error) { return failure(error); }
}
if (import.meta.main) Deno.serve(handler);
