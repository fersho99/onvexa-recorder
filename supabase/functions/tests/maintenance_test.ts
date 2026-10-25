import { handler } from '../maintenance/serve.ts';
function assert(ok:boolean,message='Assertion failed') { if (!ok) throw new Error(message); }
Deno.test('maintenance rejects forged credentials and never finalizes before Storage succeeds',async () => {
  const oldFetch=globalThis.fetch;
  const oldKey=Deno.env.get('SUPABASE_SERVICE_ROLE_KEY'),oldUrl=Deno.env.get('SUPABASE_URL');
  Deno.env.set('SUPABASE_SERVICE_ROLE_KEY','server-test'); Deno.env.set('SUPABASE_URL','https://backend.test');
  let failStorage=true,finalized=0,network=0;
  globalThis.fetch=(async (input:string|URL|Request) => {
    network++;
    const url=new URL(input instanceof Request?input.url:input.toString());
    if (url.pathname.endsWith('/maintenance_authorized')) return Response.json(false);
    if (url.pathname.endsWith('/retention_candidates')) return Response.json([{table:'attachments',row:{id:'image',user_id:'owner',image_path:'owner/recording/image.jpg',deleted_at:'2020-01-01T00:00:00Z'}}]);
    if (url.pathname.includes('/storage/v1/object/attachments')) return failStorage?Response.json({message:'unavailable'},{status:503}):Response.json([]);
    if (url.pathname.endsWith('/retention_finalize')) { finalized++; return Response.json(true); }
    throw new Error('Unexpected request '+url.pathname);
  }) as typeof fetch;
  const request=(headers:Record<string,string>={})=>new Request('https://example.test/maintenance',{method:'POST',headers});
  try {
    assert((await handler(request())).status===401); assert(network===0);
    assert((await handler(request({'x-maintenance-token':'x'.repeat(72)}))).status===401);
    const failed=await handler(request({authorization:'Bearer server-test'}));
    assert(failed.status===207); assert(finalized===0,'Storage failure must preserve retryable metadata');
    failStorage=false;
    const success=await handler(request({authorization:'Bearer server-test'}));
    assert(success.status===200); assert((await success.json()).purged===1); assert(finalized===1);
  } finally {
    globalThis.fetch=oldFetch;
    if (oldKey===undefined) Deno.env.delete('SUPABASE_SERVICE_ROLE_KEY'); else Deno.env.set('SUPABASE_SERVICE_ROLE_KEY',oldKey);
    if (oldUrl===undefined) Deno.env.delete('SUPABASE_URL'); else Deno.env.set('SUPABASE_URL',oldUrl);
  }
});
