-- Instalacion inicial: ejecutar una sola vez sobre un proyecto vacio.
-- Incluye las tres migraciones de origin/develop en una transaccion.
begin;

-- 0001_init_schema.sql
-- Esquema inicial: perfiles, dispositivos, grabaciones, transcripción, resúmenes,
-- puntos de acción, mapas mentales, adjuntos y cola de procesamiento.
-- Convenciones:
--   * IDs UUID generados en el cliente.
--   * created_at / updated_at / deleted_at (borrado lógico) en tablas sincronizables.
--   * updated_at lo fija el servidor con trigger; el cliente compara para last-write-wins.

create extension if not exists "pgcrypto";
-- Opcional (chat semántico, fase 3). Descomentar cuando se use:
-- create extension if not exists vector;

-- ---------------------------------------------------------------------------
-- Tipos enumerados
-- ---------------------------------------------------------------------------
create type recording_source as enum ('watch', 'phone');
create type recording_status as enum (
  'recording', 'recorded', 'uploading', 'uploaded',
  'transcribing', 'summarizing', 'ready', 'failed'
);
create type job_stage as enum ('transcription', 'diarization', 'summary', 'mind_map', 'vision');
create type job_status as enum ('queued', 'running', 'succeeded', 'failed', 'cancelled');
create type action_status as enum ('open', 'done', 'dismissed');

-- ---------------------------------------------------------------------------
-- Utilidades
-- ---------------------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- ---------------------------------------------------------------------------
-- profiles (1:1 con auth.users)
-- ---------------------------------------------------------------------------
create table public.profiles (
  id             uuid primary key references auth.users (id) on delete cascade,
  display_name   text,
  preferred_lang text not null default 'es',
  consent_at     timestamptz,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);

create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, display_name)
  values (new.id, coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1)));
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- devices
-- ---------------------------------------------------------------------------
create table public.devices (
  id           uuid primary key,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  kind         text not null check (kind in ('watch', 'phone')),
  name         text not null,
  model        text,
  last_seen_at timestamptz,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  deleted_at   timestamptz
);
create index devices_user_idx on public.devices (user_id);

-- ---------------------------------------------------------------------------
-- folders / tags
-- ---------------------------------------------------------------------------
create table public.folders (
  id         uuid primary key,
  user_id    uuid not null references public.profiles (id) on delete cascade,
  name       text not null,
  parent_id  uuid references public.folders (id) on delete set null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index folders_user_idx on public.folders (user_id);

create table public.tags (
  id         uuid primary key,
  user_id    uuid not null references public.profiles (id) on delete cascade,
  name       text not null,
  color      text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (user_id, name)
);

-- ---------------------------------------------------------------------------
-- templates (predefinidas: user_id null; propias: user_id del usuario)
-- ---------------------------------------------------------------------------
create table public.templates (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid references public.profiles (id) on delete cascade,
  slug          text not null,
  name_es       text not null,
  name_en       text not null,
  system_prompt text not null,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz not null default now(),
  deleted_at    timestamptz
);
create unique index templates_builtin_slug_idx on public.templates (slug) where user_id is null;
create unique index templates_user_slug_idx on public.templates (user_id, slug) where user_id is not null;

-- ---------------------------------------------------------------------------
-- recordings
-- ---------------------------------------------------------------------------
create table public.recordings (
  id             uuid primary key,
  user_id        uuid not null references public.profiles (id) on delete cascade,
  device_id      uuid references public.devices (id) on delete set null,
  folder_id      uuid references public.folders (id) on delete set null,
  template_id    uuid references public.templates (id) on delete set null,
  title          text,
  source         recording_source not null,
  status         recording_status not null default 'recorded',
  language       text,                       -- 'es', 'en' o null si aún no se detecta
  duration_ms    bigint not null default 0,
  started_at     timestamptz not null,
  audio_path     text,                       -- ruta en bucket 'audio': {user_id}/{id}.opus
  audio_bytes    bigint,
  audio_sha256   text,
  search_vector  tsvector,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now(),
  deleted_at     timestamptz
);
create index recordings_user_started_idx on public.recordings (user_id, started_at desc) where deleted_at is null;
create index recordings_folder_idx on public.recordings (folder_id);
create index recordings_status_idx on public.recordings (user_id, status);
create index recordings_search_idx on public.recordings using gin (search_vector);

create table public.recording_tags (
  recording_id uuid not null references public.recordings (id) on delete cascade,
  tag_id       uuid not null references public.tags (id) on delete cascade,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  created_at   timestamptz not null default now(),
  primary key (recording_id, tag_id)
);
create index recording_tags_user_idx on public.recording_tags (user_id);

-- ---------------------------------------------------------------------------
-- bookmarks
-- ---------------------------------------------------------------------------
create table public.bookmarks (
  id           uuid primary key,
  recording_id uuid not null references public.recordings (id) on delete cascade,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  position_ms  bigint not null check (position_ms >= 0),
  label        text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  deleted_at   timestamptz
);
create index bookmarks_recording_idx on public.bookmarks (recording_id, position_ms);

-- ---------------------------------------------------------------------------
-- speakers / transcript_segments
-- ---------------------------------------------------------------------------
create table public.speakers (
  id           uuid primary key,
  recording_id uuid not null references public.recordings (id) on delete cascade,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  label        text not null,               -- 'Hablante 1'
  display_name text,                        -- nombre asignado por el usuario
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  deleted_at   timestamptz
);
create index speakers_recording_idx on public.speakers (recording_id);

create table public.transcript_segments (
  id           uuid primary key,
  recording_id uuid not null references public.recordings (id) on delete cascade,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  speaker_id   uuid references public.speakers (id) on delete set null,
  seq          integer not null,
  start_ms     bigint not null,
  end_ms       bigint not null check (end_ms >= start_ms),
  text         text not null,
  language     text,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  deleted_at   timestamptz,
  unique (recording_id, seq)
);
create index segments_recording_time_idx on public.transcript_segments (recording_id, start_ms);
create index segments_text_idx on public.transcript_segments
  using gin (to_tsvector('simple', text));

-- Mantiene recordings.search_vector con título + transcripción.
-- Se usa la configuración 'simple' para ser neutral entre español e inglés.
create or replace function public.refresh_recording_search_vector(p_recording_id uuid)
returns void
language sql
as $$
  update public.recordings r
     set search_vector =
           setweight(to_tsvector('simple', coalesce(r.title, '')), 'A') ||
           setweight(to_tsvector('simple', coalesce((
             select string_agg(s.text, ' ' order by s.seq)
               from public.transcript_segments s
              where s.recording_id = r.id and s.deleted_at is null
           ), '')), 'B')
   where r.id = p_recording_id;
$$;

create or replace function public.trg_segments_refresh_search()
returns trigger
language plpgsql
as $$
begin
  perform public.refresh_recording_search_vector(coalesce(new.recording_id, old.recording_id));
  return null;
end;
$$;

create trigger segments_refresh_search
  after insert or update or delete on public.transcript_segments
  for each row execute function public.trg_segments_refresh_search();

-- ---------------------------------------------------------------------------
-- summaries / action_items / mind_maps
-- ---------------------------------------------------------------------------
create table public.summaries (
  id               uuid primary key,
  recording_id     uuid not null references public.recordings (id) on delete cascade,
  user_id          uuid not null references public.profiles (id) on delete cascade,
  template_id      uuid references public.templates (id) on delete set null,
  executive        text not null,
  detailed         text not null,
  key_topics       jsonb not null default '[]'::jsonb,
  decisions        jsonb not null default '[]'::jsonb,
  open_questions   jsonb not null default '[]'::jsonb,
  model            text,
  prompt_version   text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now(),
  deleted_at       timestamptz
);
create index summaries_recording_idx on public.summaries (recording_id, created_at desc);

create table public.action_items (
  id           uuid primary key,
  recording_id uuid not null references public.recordings (id) on delete cascade,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  summary_id   uuid references public.summaries (id) on delete set null,
  description  text not null,
  assignee     text,
  due_date     date,
  status       action_status not null default 'open',
  source_ms    bigint,                      -- momento de la grabación de donde salió
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  deleted_at   timestamptz
);
create index action_items_recording_idx on public.action_items (recording_id);
create index action_items_open_idx on public.action_items (user_id, status, due_date)
  where deleted_at is null;

create table public.mind_maps (
  id           uuid primary key,
  recording_id uuid not null references public.recordings (id) on delete cascade,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  tree         jsonb not null,              -- { "title": "...", "children": [ ... ] }
  markdown     text,                        -- misma estructura en Markdown (Markmap)
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  deleted_at   timestamptz
);
create index mind_maps_recording_idx on public.mind_maps (recording_id);

-- ---------------------------------------------------------------------------
-- attachments (imágenes con marca de tiempo)
-- ---------------------------------------------------------------------------
create table public.attachments (
  id           uuid primary key,
  recording_id uuid not null references public.recordings (id) on delete cascade,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  image_path   text not null,               -- bucket 'attachments': {user_id}/{recording_id}/{id}.jpg
  mime_type    text not null default 'image/jpeg',
  position_ms  bigint not null default 0,   -- ubicación dentro de la grabación
  description  text,                        -- generada por el modelo de visión
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  deleted_at   timestamptz
);
create index attachments_recording_idx on public.attachments (recording_id, position_ms);

-- ---------------------------------------------------------------------------
-- processing_jobs (cola con reintentos)
-- ---------------------------------------------------------------------------
create table public.processing_jobs (
  id           uuid primary key,
  recording_id uuid not null references public.recordings (id) on delete cascade,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  stage        job_stage not null,
  status       job_status not null default 'queued',
  attempts     integer not null default 0,
  max_attempts integer not null default 5,
  last_error   text,
  run_after    timestamptz not null default now(),
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
create index processing_jobs_queue_idx on public.processing_jobs (user_id, status, run_after);
create index processing_jobs_recording_idx on public.processing_jobs (recording_id);

-- ---------------------------------------------------------------------------
-- Triggers updated_at
-- ---------------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array[
    'profiles', 'devices', 'folders', 'tags', 'templates', 'recordings', 'bookmarks',
    'speakers', 'transcript_segments', 'summaries', 'action_items', 'mind_maps',
    'attachments', 'processing_jobs'
  ] loop
    execute format(
      'create trigger set_updated_at before update on public.%I
         for each row execute function public.set_updated_at()', t);
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- Plantillas predefinidas
-- ---------------------------------------------------------------------------
insert into public.templates (slug, name_es, name_en, system_prompt) values
  ('meeting',  'Junta de trabajo', 'Work meeting',
   'Resume una junta de trabajo. Prioriza decisiones, responsables, fechas y temas pendientes.'),
  ('class',    'Clase', 'Class',
   'Resume una clase. Prioriza conceptos clave, definiciones, ejemplos y tareas asignadas.'),
  ('interview','Entrevista', 'Interview',
   'Resume una entrevista. Separa preguntas y respuestas, y destaca citas y conclusiones relevantes.'),
  ('sales',    'Llamada de ventas', 'Sales call',
   'Resume una llamada de ventas. Prioriza necesidades del cliente, objeciones, presupuesto, plazos y siguientes pasos.'),
  ('personal', 'Nota personal', 'Personal note',
   'Resume una nota personal. Conserva ideas, recordatorios y pendientes sin formalidad innecesaria.');

-- 0002_rls_and_storage.sql
-- Row Level Security: cada usuario solo accede a sus propios datos.
-- Storage: buckets privados con carpeta raíz = user_id.

-- ---------------------------------------------------------------------------
-- Activar RLS en todas las tablas
-- ---------------------------------------------------------------------------
alter table public.profiles            enable row level security;
alter table public.devices             enable row level security;
alter table public.folders             enable row level security;
alter table public.tags                enable row level security;
alter table public.templates           enable row level security;
alter table public.recordings          enable row level security;
alter table public.recording_tags      enable row level security;
alter table public.bookmarks           enable row level security;
alter table public.speakers            enable row level security;
alter table public.transcript_segments enable row level security;
alter table public.summaries           enable row level security;
alter table public.action_items        enable row level security;
alter table public.mind_maps           enable row level security;
alter table public.attachments         enable row level security;
alter table public.processing_jobs     enable row level security;

-- ---------------------------------------------------------------------------
-- profiles: id = auth.uid()
-- ---------------------------------------------------------------------------
create policy profiles_select_own on public.profiles
  for select to authenticated using (id = (select auth.uid()));
create policy profiles_update_own on public.profiles
  for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));
-- El insert lo hace el trigger handle_new_user (security definer).
create policy profiles_delete_own on public.profiles
  for delete to authenticated using (id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- Tablas con user_id: una política por operación sobre las mismas condiciones
-- ---------------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array[
    'devices', 'folders', 'tags', 'recordings', 'recording_tags', 'bookmarks',
    'speakers', 'transcript_segments', 'summaries', 'action_items', 'mind_maps',
    'attachments', 'processing_jobs'
  ] loop
    execute format(
      'create policy %1$I_select_own on public.%1$I
         for select to authenticated using (user_id = (select auth.uid()))', t);
    execute format(
      'create policy %1$I_insert_own on public.%1$I
         for insert to authenticated with check (user_id = (select auth.uid()))', t);
    execute format(
      'create policy %1$I_update_own on public.%1$I
         for update to authenticated
         using (user_id = (select auth.uid()))
         with check (user_id = (select auth.uid()))', t);
    execute format(
      'create policy %1$I_delete_own on public.%1$I
         for delete to authenticated using (user_id = (select auth.uid()))', t);
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- Integridad entre tablas: el hijo debe pertenecer al mismo usuario que el padre.
-- Evita que un usuario enlace sus filas a una grabación ajena (aunque no pueda leerla).
-- ---------------------------------------------------------------------------
create or replace function public.owns_recording(p_recording_id uuid)
returns boolean
language sql
stable
security definer
set search_path = public
as $$
  select exists (
    select 1 from public.recordings r
     where r.id = p_recording_id and r.user_id = (select auth.uid())
  );
$$;

do $$
declare
  t text;
begin
  foreach t in array array[
    'recording_tags', 'bookmarks', 'speakers', 'transcript_segments', 'summaries',
    'action_items', 'mind_maps', 'attachments', 'processing_jobs'
  ] loop
    execute format(
      'create policy %1$I_insert_parent_owned on public.%1$I
         as restrictive for insert to authenticated
         with check (public.owns_recording(recording_id))', t);
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- templates: las predefinidas (user_id null) son de solo lectura para todos
-- ---------------------------------------------------------------------------
create policy templates_select on public.templates
  for select to authenticated
  using (user_id is null or user_id = (select auth.uid()));
create policy templates_insert_own on public.templates
  for insert to authenticated with check (user_id = (select auth.uid()));
create policy templates_update_own on public.templates
  for update to authenticated
  using (user_id = (select auth.uid())) with check (user_id = (select auth.uid()));
create policy templates_delete_own on public.templates
  for delete to authenticated using (user_id = (select auth.uid()));

-- ---------------------------------------------------------------------------
-- Storage: buckets privados. Ruta: {user_id}/...
-- ---------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit)
values
  ('audio',       'audio',       false, 52428800),   -- 50 MB: plan Free
  ('attachments', 'attachments', false,  26214400)   -- 25 MB
on conflict (id) do nothing;

create policy storage_own_select on storage.objects
  for select to authenticated
  using (
    bucket_id in ('audio', 'attachments')
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );
create policy storage_own_insert on storage.objects
  for insert to authenticated
  with check (
    bucket_id in ('audio', 'attachments')
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );
create policy storage_own_update on storage.objects
  for update to authenticated
  using (
    bucket_id in ('audio', 'attachments')
    and (storage.foldername(name))[1] = (select auth.uid())::text
  )
  with check (
    bucket_id in ('audio', 'attachments')
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );
create policy storage_own_delete on storage.objects
  for delete to authenticated
  using (
    bucket_id in ('audio', 'attachments')
    and (storage.foldername(name))[1] = (select auth.uid())::text
  );

-- ---------------------------------------------------------------------------
-- Borrado total de la cuenta (botón "borrar todo"). Las filas caen por ON DELETE CASCADE.
-- Los objetos de Storage se eliminan desde la Edge Function que invoca esto.
-- ---------------------------------------------------------------------------
create or replace function public.delete_my_data()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := (select auth.uid());
begin
  if uid is null then
    raise exception 'not authenticated';
  end if;
  delete from public.recordings where user_id = uid;
  delete from public.folders where user_id = uid;
  delete from public.tags where user_id = uid;
  delete from public.devices where user_id = uid;
  delete from public.templates where user_id = uid;
end;
$$;

revoke all on function public.delete_my_data() from public, anon;
grant execute on function public.delete_my_data() to authenticated;

-- 0003_notes_chat_vocabulary.sql
-- Notas de texto con marca de tiempo, conversaciones de "Preguntar" y vocabulario personalizado.
-- También fija ids estables para las plantillas predefinidas, para que el cliente
-- las pueda referenciar sin consultar primero al servidor.

-- ---------------------------------------------------------------------------
-- Ids estables de plantillas predefinidas
-- ---------------------------------------------------------------------------
update public.templates set id = '00000000-0000-4000-8000-000000000001' where slug = 'meeting'   and user_id is null;
update public.templates set id = '00000000-0000-4000-8000-000000000002' where slug = 'class'     and user_id is null;
update public.templates set id = '00000000-0000-4000-8000-000000000003' where slug = 'interview' and user_id is null;
update public.templates set id = '00000000-0000-4000-8000-000000000004' where slug = 'sales'     and user_id is null;
update public.templates set id = '00000000-0000-4000-8000-000000000005' where slug = 'personal'  and user_id is null;

-- ---------------------------------------------------------------------------
-- notes
-- ---------------------------------------------------------------------------
create table public.notes (
  id           uuid primary key,
  recording_id uuid not null references public.recordings (id) on delete cascade,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  position_ms  bigint not null default 0 check (position_ms >= 0),
  text         text not null,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  deleted_at   timestamptz
);
create index notes_recording_idx on public.notes (recording_id, position_ms);

-- ---------------------------------------------------------------------------
-- chat_messages (recording_id nulo = conversación global)
-- ---------------------------------------------------------------------------
create table public.chat_messages (
  id           uuid primary key,
  recording_id uuid references public.recordings (id) on delete cascade,
  user_id      uuid not null references public.profiles (id) on delete cascade,
  role         text not null check (role in ('user', 'assistant')),
  content      text not null,
  citations    jsonb not null default '[]'::jsonb,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now(),
  deleted_at   timestamptz
);
create index chat_messages_recording_idx on public.chat_messages (recording_id, created_at);
create index chat_messages_user_idx on public.chat_messages (user_id, created_at);

-- ---------------------------------------------------------------------------
-- vocabulary_terms
-- ---------------------------------------------------------------------------
create table public.vocabulary_terms (
  id         uuid primary key,
  user_id    uuid not null references public.profiles (id) on delete cascade,
  term       text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (user_id, term)
);

-- ---------------------------------------------------------------------------
-- Triggers updated_at
-- ---------------------------------------------------------------------------
do $$
declare
  t text;
begin
  foreach t in array array['notes', 'chat_messages', 'vocabulary_terms'] loop
    execute format(
      'create trigger set_updated_at before update on public.%I
         for each row execute function public.set_updated_at()', t);
  end loop;
end $$;

-- ---------------------------------------------------------------------------
-- RLS
-- ---------------------------------------------------------------------------
alter table public.notes            enable row level security;
alter table public.chat_messages    enable row level security;
alter table public.vocabulary_terms enable row level security;

do $$
declare
  t text;
begin
  foreach t in array array['notes', 'chat_messages', 'vocabulary_terms'] loop
    execute format(
      'create policy %1$I_select_own on public.%1$I
         for select to authenticated using (user_id = (select auth.uid()))', t);
    execute format(
      'create policy %1$I_insert_own on public.%1$I
         for insert to authenticated with check (user_id = (select auth.uid()))', t);
    execute format(
      'create policy %1$I_update_own on public.%1$I
         for update to authenticated
         using (user_id = (select auth.uid()))
         with check (user_id = (select auth.uid()))', t);
    execute format(
      'create policy %1$I_delete_own on public.%1$I
         for delete to authenticated using (user_id = (select auth.uid()))', t);
  end loop;
end $$;

-- La grabación a la que se engancha una nota o un mensaje debe ser del mismo usuario.
create policy notes_insert_parent_owned on public.notes
  as restrictive for insert to authenticated
  with check (public.owns_recording(recording_id));

create policy chat_messages_insert_parent_owned on public.chat_messages
  as restrictive for insert to authenticated
  with check (recording_id is null or public.owns_recording(recording_id));

-- ---------------------------------------------------------------------------
-- delete_my_data(): también borra vocabulario y la conversación global
-- ---------------------------------------------------------------------------
create or replace function public.delete_my_data()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  uid uuid := (select auth.uid());
begin
  if uid is null then
    raise exception 'not authenticated';
  end if;
  delete from public.recordings where user_id = uid;
  delete from public.chat_messages where user_id = uid;
  delete from public.vocabulary_terms where user_id = uid;
  delete from public.folders where user_id = uid;
  delete from public.tags where user_id = uid;
  delete from public.devices where user_id = uid;
  delete from public.templates where user_id = uid;
end;
$$;
-- Comprobar el padre tambien cuando se actualiza una fila existente.
do $$
declare t text;
begin
  foreach t in array array[
    'recording_tags', 'bookmarks', 'speakers', 'transcript_segments',
    'summaries', 'action_items', 'mind_maps', 'attachments', 'processing_jobs', 'notes'
  ] loop
    execute format(
      'create policy %I on public.%I as restrictive for update to authenticated
       using (public.owns_recording(recording_id))
       with check (public.owns_recording(recording_id))',
       t || '_update_parent_owned', t);
  end loop;
end $$;
create policy chat_messages_update_parent_owned on public.chat_messages
  as restrictive for update to authenticated
  using (recording_id is null or public.owns_recording(recording_id))
  with check (recording_id is null or public.owns_recording(recording_id));

commit;

-- Resultado esperado: 18 tablas, todas con rls_enabled = true.
select tablename, rowsecurity as rls_enabled
from pg_tables where schemaname = 'public' order by tablename;
