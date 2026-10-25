import { handler as ai } from '../ai/index.ts';
import { handler as removeAccount } from '../delete-account/index.ts';
function assert(ok: boolean, message = 'Assertion failed') { if (!ok) throw new Error(message); }
const userId = '11111111-1111-4111-8111-111111111111';
const recordingId = '22222222-2222-4222-8222-222222222222';
function request(body: unknown, authenticated = true) {
  return new Request('https://example.test/functions/v1/ai', { method: 'POST', headers: {
    'Content-Type': 'application/json', ...(authenticated ? { Authorization: 'Bearer test.jwt.token' } : {}),
  }, body: JSON.stringify(body) });
}
Deno.test('both functions reject missing JWT before reading server secrets', async () => {
  assert((await ai(request({}, false))).status === 401);
  assert((await removeAccount(request({}, false))).status === 401);
});
Deno.test('AI handles all five tasks, reserves quota and caches without saving generated app rows', async () => {
  const original = globalThis.fetch;
  const oldEnv: Record<string,string|undefined> = {};
  for (const [key,value] of Object.entries({ SUPABASE_URL:'https://backend.test', SUPABASE_ANON_KEY:'public-test',
    SUPABASE_SERVICE_ROLE_KEY:'server-test', LLM_API_KEY:'provider-test', LLM_MODEL:'gemini-test', AI_DAILY_LIMIT:'2' })) {
    oldEnv[key] = Deno.env.get(key); Deno.env.set(key,value);
  }
  let quota = false; let providerCalls = 0; let lastOutput: unknown; let providerStatus = 200;
  let replay: unknown = null;
  const writes: string[] = [];
  const outputs = {
    summary:{executive:'a',detailed:'b',keyTopics:[],decisions:[],openQuestions:[],actionItems:[]},
    mind_map:{tree:{title:'a',children:[]},markdown:'- a'}, vision:{description:'image'},
    chat:{answer:'answer',citations:[{ms:0,text:'source'}]}, followup_email:{subject:'subject',body:'body'},
  };
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    const url = new URL(input instanceof Request ? input.url : input.toString());
    if (url.pathname === '/auth/v1/user') return Response.json({id:userId,email:'test@example.test'});
    if (url.pathname === '/rest/v1/recordings') return Response.json({id:recordingId});
    if (url.pathname === '/rest/v1/rpc/reserve_ai_request') return Response.json(replay ?? (quota ? {state:'quota',resetAt:'2026-10-25T00:00:00Z'} : {state:'reserved',id:crypto.randomUUID()}));
    if (url.hostname === 'generativelanguage.googleapis.com' && url.pathname === '/v1beta/models/gemini-test:generateContent') {
      providerCalls++;
      const sent = JSON.parse(init?.body as string);
      assert(sent.generationConfig.responseMimeType === 'application/json');
      assert(new Headers(init?.headers).get('x-goog-api-key') === 'provider-test');
      assert(!url.search, 'API key must stay out of URLs');
      assert(sent.systemInstruction.parts[0].text.includes('JSON'));
      if (sent.contents[0].parts.length > 1) assert(sent.contents[0].parts[1].inlineData.mimeType === 'image/jpeg');
      if (providerStatus !== 200) return Response.json({error:{message:'provider test error'}},{status:providerStatus});
      return Response.json({candidates:[{content:{parts:[{text:JSON.stringify(lastOutput)}]}}],usageMetadata:{promptTokenCount:10,candidatesTokenCount:5}});
    }
    if (url.pathname.includes('/storage/v1/object/') && url.pathname.includes('/attachments/')) {
      return new Response(new Uint8Array([1,2,3]), {headers:{'Content-Type':'image/jpeg'}});
    }
    if (url.pathname === '/rest/v1/ai_usage' || url.pathname === '/rest/v1/ai_response_cache') {
      writes.push(url.pathname); return new Response(null,{status:201});
    }
    throw new Error('Unexpected network request: ' + url.pathname);
  }) as typeof fetch;
  try {
    for (const [task,output] of Object.entries(outputs)) {
      lastOutput = output;
      const response = await ai(request({task,language:'es',recordingId,
        transcript:[{seq:0,startMs:0,endMs:100,text:'source'}], question:'What?', imagePath:userId + '/image.jpg'}));
      assert(response.status === 200, task + ': ' + await response.clone().text());
      const value = await response.json(); assert(value.usage.inputTokens === 10);
    }
    assert(providerCalls === 5);
    assert(writes.length === 10);
    replay = {state:'succeeded',response:outputs.summary};
    assert((await ai(request({task:'summary',language:'es',transcript:[]}))).status === 200);
    assert(providerCalls === 5, 'cache replay must not call provider');
    replay = null;
    lastOutput = {invalid:'shape'};
    assert((await ai(request({task:'summary',language:'es',transcript:[]}))).status === 502);
    assert(providerCalls === 7, 'malformed output retried exactly once');
    providerStatus = 429;
    const providerLimit = await ai(request({task:'summary',language:'es',transcript:[]}));
    assert(providerLimit.status === 429);
    assert((await providerLimit.json()).error === 'provider_rate_limited');
    assert(providerCalls === 8, 'provider rate limit must not retry automatically');
    quota = true;
    const limited = await ai(request({task:'summary',language:'es',transcript:[]}));
    assert(limited.status === 429);
    assert(providerCalls === 8, 'quota checked before provider call');
  } finally {
    globalThis.fetch = original;
    for (const [key,value] of Object.entries(oldEnv)) { if (value === undefined) Deno.env.delete(key); else Deno.env.set(key,value); }
  }
});
Deno.test('delete account derives owner from JWT and removes nested files before auth user', async () => {
  const original = globalThis.fetch;
  const old = ['SUPABASE_URL','SUPABASE_ANON_KEY','SUPABASE_SERVICE_ROLE_KEY'].map(k => [k,Deno.env.get(k)]);
  Deno.env.set('SUPABASE_URL','https://backend.test'); Deno.env.set('SUPABASE_ANON_KEY','public-test'); Deno.env.set('SUPABASE_SERVICE_ROLE_KEY','server-test');
  const files = new Set([userId + '/a.opus',userId + '/nested/b.jpg']);
  let locked = false; let deleted = false;
  globalThis.fetch = (async (input: string | URL | Request, init?: RequestInit) => {
    const url = new URL(input instanceof Request ? input.url : input.toString());
    if (url.pathname === '/auth/v1/user') return Response.json({id:userId});
    if (url.pathname === '/rest/v1/account_deletions') {
      assert(JSON.parse(init?.body as string).user_id === userId); locked = true; return new Response(null,{status:201});
    }
    if (url.pathname.startsWith('/storage/v1/object/list/')) {
      assert(locked);
      const {prefix} = JSON.parse(init?.body as string);
      assert(prefix === userId || prefix.startsWith(userId + '/'));
      const names = new Map();
      for (const file of files) if (file.startsWith(prefix + '/')) {
        const rest = file.slice(prefix.length + 1); const parts = rest.split('/');
        names.set(parts[0],{name:parts[0],id:parts.length > 1 ? null : 'file-id'});
      }
      return Response.json([...names.values()]);
    }
    if (url.pathname.startsWith('/storage/v1/object/')) {
      for (const path of JSON.parse(init?.body as string).prefixes) files.delete(path);
      return Response.json([]);
    }
    if (url.pathname === '/auth/v1/admin/users/' + userId) {
      assert(files.size === 0); deleted = true; return Response.json({id:userId});
    }
    throw new Error('Unexpected delete request: ' + url.pathname);
  }) as typeof fetch;
  try {
    const response = await removeAccount(request({confirm:'DELETE_MY_ACCOUNT',user_id:'attacker-supplied-ignored'}));
    assert(response.status === 200, await response.clone().text()); assert(deleted);
  } finally {
    globalThis.fetch = original;
    for (const [key,value] of old) { if (value === undefined) Deno.env.delete(key!); else Deno.env.set(key!,value); }
  }
});
