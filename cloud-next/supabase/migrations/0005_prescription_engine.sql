create table if not exists public.prescription_snapshots (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  snapshot_date timestamptz not null default now(),
  trigger_type text not null,
  domain_scores_json jsonb not null default '{}'::jsonb,
  evidence_facts_json jsonb not null default '{}'::jsonb,
  red_flags_json jsonb not null default '[]'::jsonb,
  trace_id text not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_prescription_snapshots_user_date
  on public.prescription_snapshots (user_id, snapshot_date desc);

create table if not exists public.prescription_recommendations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  snapshot_id uuid references public.prescription_snapshots(id) on delete set null,
  provider_id text not null,
  primary_goal text not null,
  risk_level text not null,
  target_domains_json jsonb not null default '[]'::jsonb,
  primary_intervention_type text not null,
  secondary_intervention_type text not null default '',
  lifestyle_task_codes_json jsonb not null default '[]'::jsonb,
  timing_slot text not null,
  duration_sec integer not null,
  rationale text not null,
  evidence_json jsonb not null default '[]'::jsonb,
  contraindications_json jsonb not null default '[]'::jsonb,
  followup_metric text not null,
  is_fallback boolean not null default false,
  created_at timestamptz not null default now()
);

create index if not exists idx_prescription_recommendations_user_created
  on public.prescription_recommendations (user_id, created_at desc);

create index if not exists idx_prescription_recommendations_snapshot
  on public.prescription_recommendations (snapshot_id);

create table if not exists public.prescription_generation_logs (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  snapshot_id uuid references public.prescription_snapshots(id) on delete set null,
  recommendation_id uuid references public.prescription_recommendations(id) on delete set null,
  provider_id text not null,
  success boolean not null,
  latency_ms integer not null,
  failure_code text,
  trace_id text not null,
  created_at timestamptz not null default now()
);

create index if not exists idx_prescription_generation_logs_user_created
  on public.prescription_generation_logs (user_id, created_at desc);

create index if not exists idx_prescription_generation_logs_snapshot
  on public.prescription_generation_logs (snapshot_id);

alter table public.prescription_snapshots enable row level security;
alter table public.prescription_recommendations enable row level security;
alter table public.prescription_generation_logs enable row level security;

drop policy if exists "prescription_snapshots_select_own" on public.prescription_snapshots;
create policy "prescription_snapshots_select_own"
  on public.prescription_snapshots
  for select
  using (auth.uid() = user_id);

drop policy if exists "prescription_recommendations_select_own" on public.prescription_recommendations;
create policy "prescription_recommendations_select_own"
  on public.prescription_recommendations
  for select
  using (auth.uid() = user_id);

drop policy if exists "prescription_generation_logs_select_own" on public.prescription_generation_logs;
create policy "prescription_generation_logs_select_own"
  on public.prescription_generation_logs
  for select
  using (auth.uid() = user_id);

drop policy if exists "prescription_snapshots_insert_own" on public.prescription_snapshots;
create policy "prescription_snapshots_insert_own"
  on public.prescription_snapshots
  for insert
  with check (auth.uid() = user_id);

drop policy if exists "prescription_recommendations_insert_own" on public.prescription_recommendations;
create policy "prescription_recommendations_insert_own"
  on public.prescription_recommendations
  for insert
  with check (auth.uid() = user_id);

drop policy if exists "prescription_generation_logs_insert_own" on public.prescription_generation_logs;
create policy "prescription_generation_logs_insert_own"
  on public.prescription_generation_logs
  for insert
  with check (auth.uid() = user_id);
