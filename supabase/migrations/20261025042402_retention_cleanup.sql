-- Conserva solo IDs de borrados definitivos para que un dispositivo viejo no los resucite.
create table onvexa_private.purged_entities (
  sequence bigint generated always as identity primary key,
  user_id uuid not null references public.profiles(id) on delete cascade,
  table_name text not null,
  row_key text not null,
  purged_at timestamptz not null default now(),
  unique(user_id,table_name,row_key)
);
revoke all on onvexa_private.purged_entities from public,anon,authenticated;
create index purged_entities_user_sequence on onvexa_private.purged_entities(user_id,sequence);

create function onvexa_private.retention_guard() returns trigger
language plpgsql security definer set search_path='' as $$
declare payload jsonb; owner_id uuid; row_id text;
begin
  payload := case when tg_op='DELETE' then to_jsonb(old) else to_jsonb(new) end;
  owner_id := (payload->>'user_id')::uuid;
  row_id := case when tg_table_name='recording_tags' then (payload->>'recording_id')||':'||(payload->>'tag_id') else payload->>'id' end;
  if tg_op='DELETE' then
    if exists(select 1 from public.profiles where id=owner_id) then
      insert into onvexa_private.purged_entities(user_id,table_name,row_key) values(owner_id,tg_table_name,row_id) on conflict do nothing;
    end if;
    return old;
  end if;
  if exists(select 1 from onvexa_private.purged_entities where user_id=owner_id and table_name=tg_table_name and row_key=row_id) then
    raise exception 'purged_entity' using errcode='P0001';
  end if;
  -- Una fila borrada no acepta cambios tardios desde un cliente desconectado.
  if tg_op='UPDATE' and old.deleted_at is not null and auth.uid() is not null then return old; end if;
  return new;
end; $$;
revoke all on function onvexa_private.retention_guard() from public,anon,authenticated;
do $$ declare t text; begin
  foreach t in array array['folders','tags','templates','recordings','recording_tags','bookmarks','speakers','transcript_segments','summaries','action_items','mind_maps','attachments','notes','chat_messages','vocabulary_terms'] loop
    execute format('create trigger retention_guard before insert or update or delete on public.%I for each row execute function onvexa_private.retention_guard()',t);
  end loop;
end $$;

create function public.sync_tombstones(after_sequence bigint default 0)
returns table(sequence bigint,table_name text,row_key text)
language sql stable security definer set search_path='' as $$
  select p.sequence,p.table_name,p.row_key from onvexa_private.purged_entities p
  where p.user_id=(select auth.uid()) and p.sequence>after_sequence order by p.sequence limit 500;
$$;
revoke all on function public.sync_tombstones(bigint) from public,anon;
grant execute on function public.sync_tombstones(bigint) to authenticated;

-- Unicamente el servidor puede preparar/finalizar la purga de datos con mas de 30 dias de borrados.
create function public.retention_candidates()
returns jsonb language plpgsql security definer set search_path='' as $$
declare t text; result jsonb:='[]'; batch jsonb;
begin
  foreach t in array array['recordings','attachments','folders','tags','templates','bookmarks','speakers','transcript_segments','summaries','action_items','mind_maps','notes','chat_messages','vocabulary_terms','recording_tags'] loop
    execute format($q$select coalesce(jsonb_agg(jsonb_build_object('table',%L,'row',to_jsonb(r))),'[]') from
      (select * from public.%I where deleted_at < now()-interval '30 days' order by deleted_at limit 25) r$q$,t,t) into batch;
    result := result || batch;
  end loop;
  return result;
end; $$;
create function public.retention_finalize(target_table text,target_key text,target_deleted_at timestamptz)
returns boolean language plpgsql security definer set search_path='' as $$
declare n integer;
begin
  if target_table not in ('recordings','attachments','folders','tags','templates','bookmarks','speakers','transcript_segments','summaries','action_items','mind_maps','notes','chat_messages','vocabulary_terms','recording_tags') then raise exception 'invalid_table'; end if;
  if target_table='recording_tags' then
    delete from public.recording_tags where recording_id::text||':'||tag_id::text=target_key and deleted_at=target_deleted_at and deleted_at<now()-interval '30 days';
  else
    execute format('delete from public.%I where id::text=$1 and deleted_at=$2 and deleted_at<now()-interval ''30 days''',target_table) using target_key,target_deleted_at;
  end if;
  get diagnostics n=row_count;
  return n>0;
end; $$;
revoke all on function public.retention_candidates() from public,anon,authenticated;
revoke all on function public.retention_finalize(text,text,timestamptz) from public,anon,authenticated;
grant execute on function public.retention_candidates(),public.retention_finalize(text,text,timestamptz) to service_role;

create table onvexa_private.maintenance_credentials(singleton boolean primary key default true check(singleton), token text not null);
insert into onvexa_private.maintenance_credentials(token) values(gen_random_uuid()::text||gen_random_uuid()::text);
revoke all on onvexa_private.maintenance_credentials from public,anon,authenticated;
create function public.maintenance_authorized(candidate text) returns boolean
language sql stable security definer set search_path='' as $$
 select exists(select 1 from onvexa_private.maintenance_credentials where token=candidate);
$$;
revoke all on function public.maintenance_authorized(text) from public,anon,authenticated;
grant execute on function public.maintenance_authorized(text) to service_role;
