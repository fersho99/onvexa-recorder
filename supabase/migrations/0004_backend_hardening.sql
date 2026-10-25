-- Aplicar despues de 0001-0003. Compatible con instalar_supabase.sql.
begin;

-- Resolver nombres solo en pg_catalog y referencias public calificadas.
alter function public.set_updated_at() set search_path = '';
alter function public.handle_new_user() set search_path = '';
alter function public.refresh_recording_search_vector(uuid) set search_path = '';
alter function public.owns_recording(uuid) set search_path = '';
alter function public.delete_my_data() set search_path = '';
alter function public.owns_recording(uuid) security invoker;
alter function public.delete_my_data() security invoker;
revoke all on function public.handle_new_user() from public, anon, authenticated;
revoke all on function public.owns_recording(uuid) from public, anon;
grant execute on function public.owns_recording(uuid) to authenticated;

alter table public.recording_tags
  add column if not exists updated_at timestamptz not null default now(),
  add column if not exists deleted_at timestamptz;
drop trigger if exists set_updated_at on public.recording_tags;
create trigger set_updated_at before update on public.recording_tags
  for each row execute function public.set_updated_at();

do $$
declare t text;
begin
  foreach t in array array[
    'devices','folders','tags','templates','recordings','recording_tags','bookmarks',
    'speakers','transcript_segments','summaries','action_items','mind_maps',
    'attachments','notes','chat_messages','vocabulary_terms'
  ] loop
    execute format('create index if not exists %I on public.%I (user_id, updated_at)',
      t || '_sync_idx', t);
  end loop;
end $$;

-- BEFORE: no UPDATE recursivo al refrescar el titulo.
create or replace function public.recording_title_search()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.search_vector =
    setweight(to_tsvector('simple', coalesce(new.title, '')), 'A') ||
    setweight(to_tsvector('simple', coalesce((
      select string_agg(s.text, ' ' order by s.seq)
      from public.transcript_segments s
      where s.recording_id = new.id and s.deleted_at is null
    ), '')), 'B');
  return new;
end $$;
create trigger recordings_title_search before insert or update of title
  on public.recordings for each row execute function public.recording_title_search();

create or replace function public.trg_segments_refresh_search()
returns trigger language plpgsql set search_path = '' as $$
begin
  if tg_op <> 'INSERT' then
    perform public.refresh_recording_search_vector(old.recording_id);
  end if;
  if tg_op = 'INSERT' or (tg_op = 'UPDATE' and new.recording_id <> old.recording_id) then
    perform public.refresh_recording_search_vector(new.recording_id);
  end if;
  return null;
end $$;

-- Valida todas las FK de pertenencia tanto en INSERT como UPDATE.
-- SECURITY INVOKER: las consultas tambien respetan RLS.
create or replace function public.check_owned_references()
returns trigger language plpgsql set search_path = '' as $$
declare
  doc jsonb := to_jsonb(new);
  parent_table text;
  parent_column text;
  parent_id uuid;
  parent_recording uuid;
  owner_id uuid;
  r record;
begin
  for r in select * from (values
    ('recording_id','recordings'), ('tag_id','tags'), ('folder_id','folders'),
    ('device_id','devices'), ('template_id','templates'), ('speaker_id','speakers'),
    ('summary_id','summaries'), ('parent_id','folders')
  ) as refs(col, tbl) loop
    parent_column := r.col; parent_table := r.tbl;
    parent_id := nullif(doc ->> parent_column, '')::uuid;
    if parent_id is null then continue; end if;
    owner_id := null;
    execute format('select user_id from public.%I where id = $1', parent_table)
      into owner_id using parent_id;
    if owner_id is null and parent_table = 'templates' then
      if exists(select 1 from public.templates where id = parent_id and user_id is null) then
        continue;
      end if;
    end if;
    if owner_id is null or owner_id <> new.user_id then
      raise exception 'reference_not_owned: %', parent_column using errcode = '23514';
    end if;
    if parent_column in ('speaker_id','summary_id') then
      execute format('select recording_id from public.%I where id = $1', parent_table)
        into parent_recording using parent_id;
      if parent_recording is distinct from (doc ->> 'recording_id')::uuid then
        raise exception 'reference_recording_mismatch' using errcode = '23514';
      end if;
    end if;
    if tg_table_name = 'folders' and parent_id = (doc ->> 'id')::uuid then
      raise exception 'folder_cycle' using errcode = '23514';
    end if;
  end loop;
  return new;
end $$;

do $$
declare t text;
begin
  foreach t in array array[
    'folders','recordings','recording_tags','bookmarks','speakers','transcript_segments',
    'summaries','action_items','mind_maps','attachments','notes','chat_messages','processing_jobs'
  ] loop
    execute format('create trigger owned_references before insert or update on public.%I
      for each row execute function public.check_owned_references()', t);
  end loop;
end $$;

-- Bloquea subidas mientras se completa el borrado de cuenta (tambien con JWT aun vigente).
create table public.account_deletions (
  user_id uuid primary key references auth.users(id) on delete cascade,
  requested_at timestamptz not null default now()
);
alter table public.account_deletions enable row level security;
revoke all on public.account_deletions from anon, authenticated;
create schema if not exists onvexa_private;
revoke all on schema onvexa_private from public, anon;
grant usage on schema onvexa_private to authenticated, service_role;
create or replace function onvexa_private.account_is_active()
returns boolean language sql stable security definer set search_path = '' as $$
  select auth.uid() is not null and exists (
    select 1 from auth.users where id = auth.uid()
  ) and not exists (
    select 1 from public.account_deletions where user_id = auth.uid()
  );
$$;
revoke all on function onvexa_private.account_is_active() from public, anon;
grant execute on function onvexa_private.account_is_active() to authenticated;
create or replace function public.account_is_active()
returns boolean language sql stable security invoker set search_path = '' as $$
  select onvexa_private.account_is_active();
$$;
revoke all on function public.account_is_active() from public;
grant execute on function public.account_is_active() to authenticated;
do $$
declare t text;
begin
  foreach t in array array[
    'profiles','devices','folders','tags','templates','recordings','recording_tags','bookmarks',
    'speakers','transcript_segments','summaries','action_items','mind_maps','attachments',
    'notes','chat_messages','vocabulary_terms','processing_jobs'
  ] loop
    execute format('create policy active_account on public.%I as restrictive for all
      to authenticated using (public.account_is_active()) with check (public.account_is_active())', t);
  end loop;
end $$;
create policy active_recorder_account on storage.objects as restrictive for all to authenticated
  using (bucket_id not in ('audio','attachments') or public.account_is_active())
  with check (bucket_id not in ('audio','attachments') or public.account_is_active());

-- Metricas sin transcripciones ni prompts. Solo el servidor puede escribirlas.
create table public.ai_usage (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  task text not null check(task in ('summary','mind_map','vision','chat','followup_email')),
  request_key text not null,
  request_hash text not null,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  input_tokens bigint not null default 0,
  output_tokens bigint not null default 0,
  duration_ms bigint,
  status text not null default 'pending' check(status in ('pending','succeeded','failed')),
  unique(user_id, request_key)
);
create index ai_usage_user_day_idx on public.ai_usage(user_id, created_at);
alter table public.ai_usage enable row level security;
revoke all on public.ai_usage from anon, authenticated;
grant select on public.ai_usage to authenticated;
create policy ai_usage_read_own on public.ai_usage for select to authenticated
  using (user_id = (select auth.uid()) and public.account_is_active());

-- Cache privada de respuestas para reintentos, separada de las metricas; TTL 24 h.
create table public.ai_response_cache (
  usage_id uuid primary key references public.ai_usage(id) on delete cascade,
  response jsonb not null,
  expires_at timestamptz not null default now() + interval '24 hours'
);
alter table public.ai_response_cache enable row level security;
revoke all on public.ai_response_cache from anon, authenticated;
grant all on public.ai_usage, public.ai_response_cache, public.account_deletions to service_role;

create or replace function public.reserve_ai_request(
  p_user_id uuid, p_task text, p_request_key text, p_request_hash text, p_daily_limit integer
) returns jsonb language plpgsql security invoker set search_path = '' as $$
declare prev public.ai_usage; cached jsonb; request_id uuid;
begin
  if p_daily_limit < 1 or length(p_request_key) not between 1 and 128 then
    raise exception 'invalid_quota_request';
  end if;
  perform pg_advisory_xact_lock(hashtextextended(p_user_id::text, 0));
  if exists(select 1 from public.account_deletions where user_id = p_user_id) then
    return jsonb_build_object('state','deleting');
  end if;
  select * into prev from public.ai_usage where user_id = p_user_id and request_key = p_request_key;
  if found then
    if prev.request_hash <> p_request_hash then return jsonb_build_object('state','mismatch'); end if;
    select response into cached from public.ai_response_cache
      where usage_id = prev.id and expires_at > now();
    return jsonb_build_object('state',prev.status,'response',cached);
  end if;
  if (select count(*) from public.ai_usage where user_id = p_user_id
      and created_at >= date_trunc('day', now() at time zone 'UTC') at time zone 'UTC') >= p_daily_limit then
    return jsonb_build_object('state','quota', 'resetAt',
      (date_trunc('day', now() at time zone 'UTC') + interval '1 day') at time zone 'UTC');
  end if;
  insert into public.ai_usage(user_id,task,request_key,request_hash)
    values(p_user_id,p_task,p_request_key,p_request_hash) returning id into request_id;
  return jsonb_build_object('state','reserved','id',request_id);
end $$;
revoke all on function public.reserve_ai_request(uuid,text,text,text,integer) from public,anon,authenticated;
grant execute on function public.reserve_ai_request(uuid,text,text,text,integer) to service_role;

-- El rol anon no necesita acceso a las tablas de la app.
revoke all on all tables in schema public from anon;
-- Evita el borrado directo de profiles: el flujo debe limpiar primero Storage.
drop policy if exists profiles_delete_own on public.profiles;
update storage.buckets set file_size_limit = 52428800 where id = 'audio';
commit;
