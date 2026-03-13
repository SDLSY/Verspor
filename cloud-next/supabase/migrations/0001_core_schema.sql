create extension if not exists pgcrypto;

create table if not exists public.devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  device_id text not null,
  device_name text,
  firmware_version text,
  created_at timestamptz not null default now(),
  unique(user_id, device_id)
);

create table if not exists public.sleep_sessions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  sleep_record_id text not null,
  session_date timestamptz not null,
  bed_time timestamptz,
  wake_time timestamptz,
  total_sleep_minutes int,
  deep_sleep_minutes int,
  light_sleep_minutes int,
  rem_sleep_minutes int,
  source text not null default 'mobile_upload',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id, sleep_record_id)
);

create index if not exists idx_sleep_sessions_user_date on public.sleep_sessions(user_id, session_date desc);

create table if not exists public.sleep_windows (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  sleep_record_id text not null,
  window_start timestamptz not null,
  window_end timestamptz not null,
  hr_features jsonb,
  spo2_features jsonb,
  hrv_features jsonb,
  temp_features jsonb,
  motion_features jsonb,
  ppg_features jsonb,
  edge_anomaly_signal numeric(5,4),
  created_at timestamptz not null default now()
);

create index if not exists idx_sleep_windows_user_record on public.sleep_windows(user_id, sleep_record_id);

create table if not exists public.inference_jobs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  sleep_record_id text not null,
  status text not null check (status in ('queued', 'running', 'succeeded', 'failed')),
  idempotency_key text not null,
  model_version text,
  error_message text,
  created_at timestamptz not null default now(),
  started_at timestamptz,
  finished_at timestamptz,
  unique(user_id, idempotency_key)
);

create index if not exists idx_inference_jobs_status_created on public.inference_jobs(status, created_at);

create table if not exists public.sleep_stage_results (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  sleep_record_id text not null,
  epoch_index int not null,
  stage_5 text not null check (stage_5 in ('WAKE', 'N1', 'N2', 'N3', 'REM')),
  stage_legacy text not null check (stage_legacy in ('AWAKE', 'LIGHT', 'DEEP', 'REM')),
  confidence numeric(5,4),
  model_version text not null,
  created_at timestamptz not null default now(),
  unique(user_id, sleep_record_id, epoch_index)
);

create table if not exists public.anomaly_scores (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  sleep_record_id text not null,
  score_0_100 int not null check (score_0_100 >= 0 and score_0_100 <= 100),
  primary_factors jsonb,
  model_version text not null,
  created_at timestamptz not null default now(),
  unique(user_id, sleep_record_id, model_version)
);

create table if not exists public.nightly_reports (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  sleep_record_id text not null,
  recovery_score int,
  sleep_quality text,
  insights jsonb,
  advice jsonb,
  model_version text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id, sleep_record_id)
);

create table if not exists public.audit_events (
  id uuid primary key default gen_random_uuid(),
  user_id uuid,
  actor text not null,
  action text not null,
  resource_type text not null,
  resource_id text,
  metadata jsonb,
  created_at timestamptz not null default now()
);

create index if not exists idx_audit_events_user_time on public.audit_events(user_id, created_at desc);

alter table public.devices enable row level security;
alter table public.sleep_sessions enable row level security;
alter table public.sleep_windows enable row level security;
alter table public.inference_jobs enable row level security;
alter table public.sleep_stage_results enable row level security;
alter table public.anomaly_scores enable row level security;
alter table public.nightly_reports enable row level security;
alter table public.audit_events enable row level security;

drop policy if exists devices_select_own on public.devices;
drop policy if exists devices_insert_own on public.devices;
drop policy if exists devices_update_own on public.devices;

drop policy if exists sleep_sessions_select_own on public.sleep_sessions;
drop policy if exists sleep_sessions_insert_own on public.sleep_sessions;
drop policy if exists sleep_sessions_update_own on public.sleep_sessions;

drop policy if exists sleep_windows_select_own on public.sleep_windows;
drop policy if exists sleep_windows_insert_own on public.sleep_windows;

drop policy if exists inference_jobs_select_own on public.inference_jobs;
drop policy if exists inference_jobs_insert_own on public.inference_jobs;

drop policy if exists sleep_stage_results_select_own on public.sleep_stage_results;
drop policy if exists anomaly_scores_select_own on public.anomaly_scores;
drop policy if exists nightly_reports_select_own on public.nightly_reports;

drop policy if exists audit_events_select_own on public.audit_events;

create policy devices_select_own on public.devices
  for select using (auth.uid() = user_id);
create policy devices_insert_own on public.devices
  for insert with check (auth.uid() = user_id);
create policy devices_update_own on public.devices
  for update using (auth.uid() = user_id);

create policy sleep_sessions_select_own on public.sleep_sessions
  for select using (auth.uid() = user_id);
create policy sleep_sessions_insert_own on public.sleep_sessions
  for insert with check (auth.uid() = user_id);
create policy sleep_sessions_update_own on public.sleep_sessions
  for update using (auth.uid() = user_id);

create policy sleep_windows_select_own on public.sleep_windows
  for select using (auth.uid() = user_id);
create policy sleep_windows_insert_own on public.sleep_windows
  for insert with check (auth.uid() = user_id);

create policy inference_jobs_select_own on public.inference_jobs
  for select using (auth.uid() = user_id);
create policy inference_jobs_insert_own on public.inference_jobs
  for insert with check (auth.uid() = user_id);

create policy sleep_stage_results_select_own on public.sleep_stage_results
  for select using (auth.uid() = user_id);

create policy anomaly_scores_select_own on public.anomaly_scores
  for select using (auth.uid() = user_id);

create policy nightly_reports_select_own on public.nightly_reports
  for select using (auth.uid() = user_id);

create policy audit_events_select_own on public.audit_events
  for select using (auth.uid() = user_id);
