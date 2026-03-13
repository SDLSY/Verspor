create or replace view public.recommendation_effect_links_v1
with (security_invoker = on) as
select
  exec.id as execution_row_id,
  exec.user_id,
  exec.execution_id,
  exec.task_id,
  exec.started_at,
  exec.ended_at,
  exec.elapsed_sec,
  exec.effect_score,
  exec.before_stress,
  exec.after_stress,
  exec.before_hr,
  exec.after_hr,
  exec.completion_type,
  coalesce(exec.before_stress, 0) - coalesce(exec.after_stress, 0) as stress_drop,
  trace.id as trace_row_id,
  trace.trace_id as attributed_trace_id,
  trace.trace_type,
  trace.provider_id,
  trace.risk_level,
  trace.personalization_level,
  coalesce(trace.metadata_json ->> 'modelVersion', 'SRM_V2') as model_version,
  coalesce(trace.metadata_json ->> 'profileCode', trace.metadata_json ->> 'modelProfile', 'default_adult_cn') as model_profile,
  coalesce(trace.metadata_json ->> 'configSource', 'default') as config_source,
  coalesce(trace.metadata_json ->> 'recommendationMode', 'FOLLOW_UP') as recommendation_mode,
  case
    when trace.id is not null then 'WINDOW_LAST_DAILY'
    else 'UNATTRIBUTED'
  end as attribution_mode,
  trace.created_at as trace_created_at
from public.intervention_executions exec
left join lateral (
  select rt.*
  from public.recommendation_traces rt
  where rt.user_id = exec.user_id
    and rt.trace_type = 'DAILY_PRESCRIPTION'
    and rt.created_at <= exec.ended_at
    and rt.created_at >= exec.ended_at - interval '36 hours'
  order by rt.created_at desc
  limit 1
) trace on true;

create or replace view public.recommendation_effect_summary_v1
with (security_invoker = on) as
select
  user_id,
  trace_type,
  model_version,
  model_profile,
  config_source,
  recommendation_mode,
  attribution_mode,
  count(*)::bigint as execution_count,
  avg(effect_score) as avg_effect_score,
  avg(stress_drop) as avg_stress_drop,
  avg(elapsed_sec) as avg_elapsed_sec,
  max(ended_at) as last_execution_at
from public.recommendation_effect_links_v1
group by
  user_id,
  trace_type,
  model_version,
  model_profile,
  config_source,
  recommendation_mode,
  attribution_mode;
