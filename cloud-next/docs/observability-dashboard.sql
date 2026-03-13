create or replace view public.v_inference_job_daily as
select
  date_trunc('day', created_at) as day,
  count(*) as total_jobs,
  count(*) filter (where status = 'succeeded') as succeeded_jobs,
  count(*) filter (where status = 'failed') as failed_jobs,
  count(*) filter (where status = 'queued') as queued_jobs,
  count(*) filter (where status = 'running') as running_jobs
from public.inference_jobs
group by 1
order by 1 desc;

create or replace view public.v_inference_latency as
select
  id,
  user_id,
  sleep_record_id,
  status,
  created_at,
  started_at,
  finished_at,
  extract(epoch from (finished_at - started_at)) * 1000 as duration_ms
from public.inference_jobs
where started_at is not null and finished_at is not null;
