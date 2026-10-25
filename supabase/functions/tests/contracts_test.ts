import { validateOutput, validateRequest } from '../_shared/contracts.ts';
import { ApiError, body } from '../_shared/http.ts';
function assert(ok: boolean) { if (!ok) throw new Error('Assertion failed'); }
Deno.test('reject invalid input and impersonated tasks', () => {
  for (const value of [{task:'admin',language:'es'}, {task:'chat',language:'es'},
    {task:'vision',language:'es',imagePath:'../other.jpg'},
    {task:'summary',language:'es',transcript:[{seq:0,startMs:20,endMs:10,text:'x'}]}]) {
    let rejected = false; try { validateRequest(value); } catch(e) { rejected = e instanceof ApiError; }
    assert(rejected);
  }
});
Deno.test('validate all five output shapes, including citations and nested trees', () => {
  assert(validateOutput('summary', {executive:'a',detailed:'b',keyTopics:[],decisions:[],openQuestions:[],actionItems:[]}));
  assert(!validateOutput('summary', {executive:'a'}));
  assert(validateOutput('mind_map',{tree:{title:'a',children:[]},markdown:'- a'}));
  assert(!validateOutput('mind_map',{tree:{title:'a',children:[null]},markdown:''}));
  assert(validateOutput('vision',{description:'a'}));
  assert(validateOutput('chat',{answer:'a',citations:[{ms:100,text:'a'}]}));
  assert(!validateOutput('chat',{answer:'a',citations:[{ms:-1,text:'a'}]}));
  assert(validateOutput('followup_email',{subject:'a',body:'b'}));
});
Deno.test('body limit counts bytes without trusting Content-Length', async () => {
  const req = new Request('https://example.test', {method:'POST',headers:{'Content-Type':'application/json'}, body:JSON.stringify({text:'ñ'.repeat(30)})});
  let code = 0; try { await body(req, 40); } catch(e) { if (e instanceof ApiError) code = e.status; }
  assert(code === 413);
});
