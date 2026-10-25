-- Consultas administrativas sin prompts, claves ni contenido de conversaciones.
select status,count(*) from public.recordings group by status;
select task,status,count(*),sum(input_tokens) as input_tokens,sum(output_tokens) as output_tokens
from public.ai_usage where created_at>now()-interval '24 hours' group by task,status;
select jobname,schedule,active from cron.job where jobname like 'onvexa-%';
select jobid,status,start_time,end_time from cron.job_run_details order by start_time desc limit 30;
select bucket_id,count(*),sum(coalesce((metadata->>'size')::bigint,0)) as bytes
from storage.objects where bucket_id in ('audio','attachments') group by bucket_id;
