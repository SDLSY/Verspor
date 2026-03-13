create table if not exists public.assessment_baseline_snapshots (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique,
  completed_scale_codes_json jsonb not null default '[]'::jsonb,
  completed_count integer not null default 0,
  completed_at timestamptz not null default now(),
  freshness_until timestamptz not null default now(),
  source text not null default 'ANDROID',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_assessment_baseline_snapshots_user_completed
  on public.assessment_baseline_snapshots (user_id, completed_at desc);

create table if not exists public.doctor_inquiry_summaries (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  session_id text not null,
  assessed_at timestamptz not null default now(),
  risk_level text not null,
  chief_complaint text not null default '',
  red_flags_json jsonb not null default '[]'::jsonb,
  recommended_department text not null default '',
  doctor_summary text not null default '',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, session_id)
);

create index if not exists idx_doctor_inquiry_summaries_user_assessed
  on public.doctor_inquiry_summaries (user_id, assessed_at desc);

alter table public.assessment_baseline_snapshots enable row level security;
alter table public.doctor_inquiry_summaries enable row level security;

drop policy if exists "assessment_baseline_snapshots_select_own" on public.assessment_baseline_snapshots;
create policy "assessment_baseline_snapshots_select_own"
  on public.assessment_baseline_snapshots
  for select
  using (auth.uid() = user_id);

drop policy if exists "assessment_baseline_snapshots_insert_own" on public.assessment_baseline_snapshots;
create policy "assessment_baseline_snapshots_insert_own"
  on public.assessment_baseline_snapshots
  for insert
  with check (auth.uid() = user_id);

drop policy if exists "assessment_baseline_snapshots_update_own" on public.assessment_baseline_snapshots;
create policy "assessment_baseline_snapshots_update_own"
  on public.assessment_baseline_snapshots
  for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "doctor_inquiry_summaries_select_own" on public.doctor_inquiry_summaries;
create policy "doctor_inquiry_summaries_select_own"
  on public.doctor_inquiry_summaries
  for select
  using (auth.uid() = user_id);

drop policy if exists "doctor_inquiry_summaries_insert_own" on public.doctor_inquiry_summaries;
create policy "doctor_inquiry_summaries_insert_own"
  on public.doctor_inquiry_summaries
  for insert
  with check (auth.uid() = user_id);

drop policy if exists "doctor_inquiry_summaries_update_own" on public.doctor_inquiry_summaries;
create policy "doctor_inquiry_summaries_update_own"
  on public.doctor_inquiry_summaries
  for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);
