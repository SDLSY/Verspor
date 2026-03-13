create table if not exists public.intervention_tasks (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  task_id text not null,
  task_date timestamptz not null,
  source_type text not null check (source_type in ('AI_COACH', 'MEDICAL_REPORT', 'RULE_ENGINE')),
  trigger_reason text,
  body_zone text not null,
  protocol_type text not null,
  duration_sec int not null check (duration_sec > 0),
  planned_at timestamptz not null,
  status text not null check (status in ('PENDING', 'RUNNING', 'COMPLETED', 'SKIPPED', 'FAILED')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(user_id, task_id)
);

create index if not exists idx_intervention_tasks_user_date
  on public.intervention_tasks(user_id, task_date desc);
create index if not exists idx_intervention_tasks_user_status
  on public.intervention_tasks(user_id, status);

create table if not exists public.intervention_executions (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  execution_id text not null,
  task_id text not null,
  started_at timestamptz not null,
  ended_at timestamptz not null,
  elapsed_sec int not null check (elapsed_sec >= 0),
  before_stress numeric(6,2),
  after_stress numeric(6,2),
  before_hr int,
  after_hr int,
  effect_score numeric(6,2),
  completion_type text not null default 'FULL',
  created_at timestamptz not null default now(),
  unique(user_id, execution_id),
  foreign key (user_id, task_id) references public.intervention_tasks(user_id, task_id)
    on update cascade on delete cascade
);

create index if not exists idx_intervention_executions_user_started
  on public.intervention_executions(user_id, started_at desc);
create index if not exists idx_intervention_executions_user_task
  on public.intervention_executions(user_id, task_id);

create table if not exists public.medical_reports (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  report_id text not null,
  report_date timestamptz not null,
  report_type text not null,
  parse_status text not null default 'PARSED',
  risk_level text not null default 'LOW',
  ocr_text_digest text,
  created_at timestamptz not null default now(),
  unique(user_id, report_id)
);

create index if not exists idx_medical_reports_user_date
  on public.medical_reports(user_id, report_date desc);
create index if not exists idx_medical_reports_user_risk
  on public.medical_reports(user_id, risk_level);

create table if not exists public.medical_metrics (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  report_id text not null,
  metric_code text not null,
  metric_name text not null,
  metric_value numeric(10,3) not null,
  unit text not null,
  ref_low numeric(10,3),
  ref_high numeric(10,3),
  is_abnormal boolean not null default false,
  confidence numeric(6,3) not null default 0.8,
  created_at timestamptz not null default now(),
  unique(user_id, report_id, metric_code),
  foreign key (user_id, report_id) references public.medical_reports(user_id, report_id)
    on update cascade on delete cascade
);

create index if not exists idx_medical_metrics_user_report
  on public.medical_metrics(user_id, report_id);
create index if not exists idx_medical_metrics_user_abnormal
  on public.medical_metrics(user_id, is_abnormal);

alter table public.intervention_tasks enable row level security;
alter table public.intervention_executions enable row level security;
alter table public.medical_reports enable row level security;
alter table public.medical_metrics enable row level security;

drop policy if exists intervention_tasks_select_own on public.intervention_tasks;
drop policy if exists intervention_tasks_insert_own on public.intervention_tasks;
drop policy if exists intervention_tasks_update_own on public.intervention_tasks;

drop policy if exists intervention_executions_select_own on public.intervention_executions;
drop policy if exists intervention_executions_insert_own on public.intervention_executions;
drop policy if exists intervention_executions_update_own on public.intervention_executions;

drop policy if exists medical_reports_select_own on public.medical_reports;
drop policy if exists medical_reports_insert_own on public.medical_reports;
drop policy if exists medical_reports_update_own on public.medical_reports;

drop policy if exists medical_metrics_select_own on public.medical_metrics;
drop policy if exists medical_metrics_insert_own on public.medical_metrics;
drop policy if exists medical_metrics_update_own on public.medical_metrics;

create policy intervention_tasks_select_own on public.intervention_tasks
  for select using (auth.uid() = user_id);
create policy intervention_tasks_insert_own on public.intervention_tasks
  for insert with check (auth.uid() = user_id);
create policy intervention_tasks_update_own on public.intervention_tasks
  for update using (auth.uid() = user_id);

create policy intervention_executions_select_own on public.intervention_executions
  for select using (auth.uid() = user_id);
create policy intervention_executions_insert_own on public.intervention_executions
  for insert with check (auth.uid() = user_id);
create policy intervention_executions_update_own on public.intervention_executions
  for update using (auth.uid() = user_id);

create policy medical_reports_select_own on public.medical_reports
  for select using (auth.uid() = user_id);
create policy medical_reports_insert_own on public.medical_reports
  for insert with check (auth.uid() = user_id);
create policy medical_reports_update_own on public.medical_reports
  for update using (auth.uid() = user_id);

create policy medical_metrics_select_own on public.medical_metrics
  for select using (auth.uid() = user_id);
create policy medical_metrics_insert_own on public.medical_metrics
  for insert with check (auth.uid() = user_id);
create policy medical_metrics_update_own on public.medical_metrics
  for update using (auth.uid() = user_id);
