-- Mantiene los privilegios elevados fuera del esquema de la API.
alter function public.sync_tombstones(bigint) set schema onvexa_private;
grant usage on schema onvexa_private to authenticated;
revoke all on function onvexa_private.sync_tombstones(bigint) from public,anon;
grant execute on function onvexa_private.sync_tombstones(bigint) to authenticated;
create function public.sync_tombstones(after_sequence bigint default 0)
returns table(sequence bigint,table_name text,row_key text)
language sql stable security invoker set search_path='' as $$
 select * from onvexa_private.sync_tombstones(after_sequence);
$$;
revoke all on function public.sync_tombstones(bigint) from public,anon;
grant execute on function public.sync_tombstones(bigint) to authenticated;
