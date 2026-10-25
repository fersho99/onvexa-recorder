// Postgres real embebido; Auth y Storage son esquemas de prueba, sin llamadas a la nube.
import { PGlite } from '@electric-sql/pglite';
import { readFile } from 'node:fs/promises';
import assert from 'node:assert/strict';
const db = new PGlite();
try {
  await db.exec(`
    create role anon nologin;
    create role authenticated nologin;
    create role service_role nologin bypassrls;
    create schema auth; create schema storage;
    create table auth.users(id uuid primary key, email text, raw_user_meta_data jsonb default '{}');
    create function auth.uid() returns uuid language sql stable as $$
      select nullif(current_setting('request.jwt.claim.sub', true),'')::uuid $$;
    grant usage on schema auth,public,storage to authenticated,anon,service_role;
    grant execute on function auth.uid() to authenticated,anon,service_role;
    create table storage.buckets(id text primary key,name text,public boolean,file_size_limit bigint);
    create table storage.objects(id uuid primary key default gen_random_uuid(),bucket_id text,name text);
    alter table storage.objects enable row level security;
    create function storage.foldername(text) returns text[] language sql immutable as $$
      select (string_to_array($1,'/'))[1:array_length(string_to_array($1,'/'),1)-1] $$;
  `);
  const files = process.argv.includes('--installed')
    ? ['../setup/instalar_supabase.sql','../migrations/0004_backend_hardening.sql']
    : ['0001_init_schema.sql','0002_rls_and_storage.sql','0003_notes_chat_vocabulary.sql','0004_backend_hardening.sql'].map(f => '../migrations/' + f);
  for (const file of files) {
    let sql = await readFile(new URL(file, import.meta.url), 'utf8');
    // PGlite incluye gen_random_uuid() en core; no necesita pgcrypto.
    sql = sql.replace('create extension if not exists "pgcrypto";', '');
    await db.exec(sql);
  }
  await db.exec(await readFile(new URL('../migrations/20261025042402_retention_cleanup.sql',import.meta.url),'utf8'));
  await db.exec(await readFile(new URL('../migrations/20261025043034_restrict_tombstone_rpc.sql',import.meta.url),'utf8'));
  const tables = await db.query(`select tablename,rowsecurity from pg_tables where schemaname='public'`);
  assert(tables.rows.every(t => t.rowsecurity), 'RLS en todas las tablas');
  assert.equal((await db.query('select * from templates')).rows.length, 5);
  const A = '11111111-1111-4111-8111-111111111111', B = '22222222-2222-4222-8222-222222222222';
  const R = '33333333-3333-4333-8333-333333333333', RB = '44444444-4444-4444-8444-444444444444';
  await db.exec(`insert into auth.users(id,email) values('${A}','a@example.test'),('${B}','b@example.test');
    grant select,insert,update,delete on all tables in schema public to authenticated;
    revoke all on account_deletions,ai_response_cache from authenticated;
    revoke insert,update,delete on ai_usage from authenticated;
    grant select,insert,update,delete on storage.objects to authenticated;
    insert into recordings(id,user_id,source,started_at,title) values
      ('${R}','${A}','phone',now(),'Antes'),('${RB}','${B}','phone',now(),'Ajena');
  `);
  async function asUser(id, action) {
    await db.exec(`set role authenticated; select set_config('request.jwt.claim.sub','${id}',false);`);
    try { return await action(); } finally { await db.exec('reset role'); }
  }
  await asUser(A, async () => {
    assert.equal((await db.query('select * from recordings')).rows.length, 1);
    await db.exec(`insert into notes(id,user_id,recording_id,text) values(gen_random_uuid(),'${A}','${R}','propia')`);
    await assert.rejects(db.exec(`insert into notes(id,user_id,recording_id,text) values(gen_random_uuid(),'${A}','${RB}','ajena')`));
    await assert.rejects(db.exec(`update notes set recording_id='${RB}'`));
    await db.exec(`update recordings set title='Despues' where id='${R}'`);
    const search = await db.query(`select search_vector::text as value from recordings where id='${R}'`);
    assert(search.rows[0].value.includes('despues'));
    await db.exec(`insert into tags(id,user_id,name) values('${RB}','${A}','tag');
      insert into recording_tags(recording_id,tag_id,user_id) values('${R}','${RB}','${A}');
      update recording_tags set deleted_at=now() where recording_id='${R}';`);
    await db.exec(`insert into storage.objects(bucket_id,name) values('audio','${A}/test.opus')`);
    await assert.rejects(db.exec(`insert into storage.objects(bucket_id,name) values('audio','${B}/test.opus')`));
  });
  await asUser(B, async () => {
    assert.equal((await db.query('select * from notes')).rows.length, 0);
    assert.equal((await db.query('select * from storage.objects')).rows.length, 0);
  });
  const reserve = () => db.query(`select reserve_ai_request('${A}','summary','request1','hash1',1) as r`);
  const RC='77777777-7777-4777-8777-777777777777', NC='88888888-8888-4888-8888-888888888888';
  await db.exec(`select set_config('request.jwt.claim.sub','',false);
    insert into recordings(id,user_id,source,started_at,title,deleted_at) values('${RC}','${A}','phone',now(),'borrada',now()-interval '31 days');
    insert into notes(id,user_id,recording_id,text) values('${NC}','${A}','${RC}','texto que debe desaparecer');`);
  await asUser(A,async () => {
    await db.exec(`update recordings set deleted_at=null,title='resucitada' where id='${RC}'`);
    assert.equal((await db.query(`select title from recordings where id='${RC}'`)).rows[0].title,'borrada');
    await assert.rejects(db.query('select retention_candidates()'));
    await assert.rejects(db.query("select maintenance_authorized('invalid')"));
  });
  await db.exec(`select set_config('request.jwt.claim.sub','',false);`);
  assert.equal((await db.query(`select retention_finalize('recordings','${R}',now()) as removed`)).rows[0].removed,false);
  assert.equal((await db.query(`select retention_finalize('recordings','${RC}',(select deleted_at from recordings where id='${RC}')) as removed`)).rows[0].removed,true);
  assert.equal((await db.query(`select * from notes where id='${NC}'`)).rows.length,0);
  await asUser(A,async () => {
    const markers=(await db.query('select * from sync_tombstones(0)')).rows;
    assert(markers.some(x=>x.table_name==='recordings' && x.row_key===RC));
    assert(markers.some(x=>x.table_name==='notes' && x.row_key===NC));
    await assert.rejects(db.exec(`insert into recordings(id,user_id,source,started_at) values('${RC}','${A}','phone',now())`),/purged_entity/);
  });
  await asUser(B,async () => assert.equal((await db.query('select * from sync_tombstones(0)')).rows.length,0));
  await db.exec(`select set_config('request.jwt.claim.sub','',false);`);
  assert.equal((await reserve()).rows[0].r.state, 'reserved');
  assert.equal((await reserve()).rows[0].r.state, 'pending');
  assert.equal((await db.query(`select reserve_ai_request('${A}','chat','request2','hash2',1) as r`)).rows[0].r.state, 'quota');
  assert.equal((await db.query(`select reserve_ai_request('${A}','summary','request1','DIFFERENT',1) as r`)).rows[0].r.state, 'mismatch');
  await db.exec(`
    insert into speakers(id,user_id,recording_id,label) values('55555555-5555-4555-8555-555555555555','${A}','${R}','Speaker');
    insert into transcript_segments(id,user_id,recording_id,speaker_id,seq,start_ms,end_ms,text)
      values(gen_random_uuid(),'${A}','${R}','55555555-5555-4555-8555-555555555555',0,0,100,'source');
    insert into summaries(id,user_id,recording_id,executive,detailed)
      values('66666666-6666-4666-8666-666666666666','${A}','${R}','a','b');
    insert into action_items(id,user_id,recording_id,summary_id,description)
      values(gen_random_uuid(),'${A}','${R}','66666666-6666-4666-8666-666666666666','action');
  `);
  await db.exec(`insert into account_deletions(user_id) values('${A}')`);
  await asUser(A, async () => {
    assert.equal((await db.query('select * from recordings')).rows.length, 0);
    await assert.rejects(db.exec(`insert into storage.objects(bucket_id,name) values('audio','${A}/blocked.opus')`));
  });
  await db.exec(`delete from auth.users where id='${A}'`);
  assert.equal((await db.query(`select * from profiles where id='${A}'`)).rows.length,0);
  assert.equal((await db.query(`select * from recordings where user_id='${A}'`)).rows.length,0);
  assert.equal((await db.query(`select * from ai_usage where user_id='${A}'`)).rows.length,0);
  await asUser(A, async () => {
    assert.equal((await db.query('select public.account_is_active() as active')).rows[0].active,false);
    await assert.rejects(db.exec(`insert into storage.objects(bucket_id,name) values('audio','${A}/deleted-account.opus')`));
  });
  console.log('PASS: migraciones, 5 plantillas, RLS, FK en insert/update, busqueda, etiquetas, Storage, cuotas e idempotencia, bloqueo de borrado.');
} finally { await db.close(); }
