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
  ('audio',       'audio',       false, 524288000),  -- 500 MB
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
