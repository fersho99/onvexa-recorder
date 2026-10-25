-- Ejecutar despues de desplegar maintenance. No contiene claves ni las imprime.
create extension if not exists pg_net with schema extensions;
create or replace function onvexa_private.run_retention() returns bigint
language plpgsql security definer set search_path='' as $$
declare token_value text; request_id bigint;
begin
 select token into token_value from onvexa_private.maintenance_credentials where singleton;
 select net.http_post(
   url := 'https://uvjkebvnqyuoyaypynez.supabase.co/functions/v1/maintenance',
   headers := jsonb_build_object('Content-Type','application/json','x-maintenance-token',token_value),
   body := '{}'::jsonb, timeout_milliseconds := 120000
 ) into request_id;
 return request_id;
end; $$;
revoke all on function onvexa_private.run_retention() from public,anon,authenticated;
select cron.schedule('onvexa-retention-cleanup','37 4 * * *',$$select onvexa_private.run_retention();$$);
