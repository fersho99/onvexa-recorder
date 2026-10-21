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
