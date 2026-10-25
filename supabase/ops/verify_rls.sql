-- Prueba administrativa transaccional: no deja grabaciones ni notas de prueba.
begin;
create temporary table probe_users as select id,row_number() over(order by created_at,id) as n from auth.users limit 2;
do $$ begin if (select count(*) from probe_users)<>2 then raise exception 'Se requieren dos usuarios existentes'; end if; end $$;
create temporary table probe_recordings as select id as user_id,gen_random_uuid() as id,n from probe_users;
grant select on probe_users,probe_recordings to authenticated;
insert into public.recordings(id,user_id,source,started_at,title)
  select id,user_id,'phone',now(),'Prueba transaccional RLS' from probe_recordings;
set local role authenticated;
select set_config('request.jwt.claim.sub',(select id::text from probe_users where n=1),true);
do $$ begin
  if (select count(*) from public.recordings where id in(select id from probe_recordings))<>1 then raise exception 'Aislamiento A incorrecto'; end if;
  begin
    insert into public.notes(id,user_id,recording_id,text)
      select gen_random_uuid(),(select id from probe_users where n=1),(select id from probe_recordings where n=2),'No debe insertarse';
    raise exception 'cross_reference_allowed';
  exception when others then if sqlerrm='cross_reference_allowed' then raise; end if; end;
  begin
    perform public.retention_candidates();
    raise exception 'maintenance_allowed';
  exception when insufficient_privilege then null; end;
end $$;
select set_config('request.jwt.claim.sub',(select id::text from probe_users where n=2),true);
do $$ begin
  if (select count(*) from public.recordings where id in(select id from probe_recordings))<>1 then raise exception 'Aislamiento B incorrecto'; end if;
  if exists(select 1 from public.recordings where id=(select id from probe_recordings where n=1)) then raise exception 'B ve datos de A'; end if;
end $$;
reset role;
rollback;
select 'PASS: aislamiento real de dos usuarios, FK cruzada rechazada, mantenimiento privado, cambios revertidos' as result;
