-- Opcional. Ejecutar en SQL Editor con privilegios administrativos una vez.
-- Limpia solo cache expirada y metricas antiguas; nunca borra grabaciones.
create extension if not exists pg_cron;
select cron.schedule('onvexa-ai-cache-cleanup', '17 * * * *',
  $$delete from public.ai_response_cache where expires_at < now();$$);
select cron.schedule('onvexa-ai-usage-cleanup', '23 4 * * *',
  $$delete from public.ai_usage where created_at < now() - interval '90 days';$$);
