import { createClient } from '@supabase/supabase-js';

/** Invocacion administrativa. La clave service_role permanece exclusivamente en el servidor. */
export async function handler(req: Request): Promise<Response> {
  const key = Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '';
  if (!key) return new Response('Unavailable',{status:503});
  if (req.method !== 'POST') return new Response('Method not allowed',{status:405});
  const db = createClient(Deno.env.get('SUPABASE_URL')!,key,{auth:{persistSession:false,autoRefreshToken:false}});
  if (req.headers.get('authorization') !== `Bearer ${key}`) {
    const token = req.headers.get('x-maintenance-token');
    if (!token || token.length !== 72) return new Response('Unauthorized',{status:401});
    const verified = await db.rpc('maintenance_authorized',{candidate:token});
    if (verified.error || verified.data !== true) return new Response('Unauthorized',{status:401});
  }
  if (req.headers.get('x-onvexa-diagnostic') === 'ai') {
    // Prueba ficticia con una cuenta temporal; nunca usa grabaciones del propietario.
    const password=crypto.randomUUID()+crypto.randomUUID();
    const email=`diagnostic-${crypto.randomUUID()}@example.invalid`;
    const created=await db.auth.admin.createUser({email,password,email_confirm:true});
    if (created.error || !created.data.user) return Response.json({ok:false,error:'diagnostic_auth_failed'},{status:503});
    try {
      const client=createClient(Deno.env.get('SUPABASE_URL')!,Deno.env.get('SUPABASE_ANON_KEY')!,{auth:{persistSession:false,autoRefreshToken:false}});
      const session=await client.auth.signInWithPassword({email,password});
      if (session.error || !session.data.session) return Response.json({ok:false,error:'diagnostic_login_failed'},{status:503});
      const response=await fetch(Deno.env.get('SUPABASE_URL')!+'/functions/v1/ai',{
        method:'POST',headers:{'Content-Type':'application/json',Authorization:`Bearer ${session.data.session.access_token}`,'Idempotency-Key':crypto.randomUUID()},
        body:JSON.stringify({task:'summary',language:'es',transcript:[{seq:0,startMs:0,endMs:9000,text:'Ejemplo ficticio escolar: el equipo acordo preparar una demostracion. Alex revisara la conexion del reloj. No se fijaron fechas.'}]}),signal:AbortSignal.timeout(90000)
      });
      const result=await response.json();
      return Response.json({ok:response.ok,status:response.status,...(response.ok?{executive:result.executive}:{error:result.error_code??result.error??'provider_failed'})},{status:response.ok?200:502});
    } finally { await db.auth.admin.deleteUser(created.data.user.id); }
  }
  const { data, error } = await db.rpc('retention_candidates');
  if (error) return new Response('Retention unavailable',{status:503});
  let purged=0,failed=0;
  for (const candidate of data ?? []) {
    const row = candidate.row;
    const files: Array<{bucket:string,path:string}> = [];
    if (candidate.table === 'recordings') {
      if (row.audio_path) files.push({bucket:'audio',path:row.audio_path});
      const attachments = await db.from('attachments').select('image_path').eq('recording_id',row.id).eq('user_id',row.user_id);
      if (attachments.error) { failed++; continue; }
      for (const image of attachments.data) files.push({bucket:'attachments',path:image.image_path});
    } else if (candidate.table === 'attachments') files.push({bucket:'attachments',path:row.image_path});
    let ok = true;
    for (const file of files) {
      if (!file.path.startsWith(row.user_id+'/') || file.path.includes('..')) { ok=false; break; }
      const removed = await db.storage.from(file.bucket).remove([file.path]);
      if (removed.error) { ok=false; break; }
    }
    if (!ok) { failed++; continue; }
    const rowKey = candidate.table==='recording_tags' ? row.recording_id+':'+row.tag_id : row.id;
    const result = await db.rpc('retention_finalize',{target_table:candidate.table,target_key:rowKey,target_deleted_at:row.deleted_at});
    if (result.error) failed++; else if (result.data) purged++;
  }
  return Response.json({purged,failed},{status:failed?207:200});
}
if (import.meta.main) Deno.serve(handler);
