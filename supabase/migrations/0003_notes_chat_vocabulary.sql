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
