create table if not exists public.recommendation_traces (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  trace_type text not null,
  trace_key text,
  trace_id text,
  provider_id text,
  related_snapshot_id uuid,
  related_recommendation_id uuid,
  risk_level text,
  personalization_level text,
  missing_inputs_json jsonb not null default '[]'::jsonb,
  input_materials_json jsonb not null default '{}'::jsonb,
  derived_signals_json jsonb not null default '{}'::jsonb,
  output_payload_json jsonb not null default '{}'::jsonb,
  metadata_json jsonb not null default '{}'::jsonb,
  is_fallback boolean not null default false,
  source text not null default 'CLOUD_NEXT',
  created_at timestamptz not null default now(),
  constraint recommendation_traces_type_check check (
    trace_type in ('DAILY_PRESCRIPTION', 'PERIOD_SUMMARY', 'DOCTOR_TURN')
  )
);

create index if not exists idx_recommendation_traces_user_type_created
  on public.recommendation_traces (user_id, trace_type, created_at desc);

create index if not exists idx_recommendation_traces_trace_id
  on public.recommendation_traces (trace_id);

create index if not exists idx_recommendation_traces_snapshot
  on public.recommendation_traces (related_snapshot_id);

alter table public.recommendation_traces enable row level security;

drop policy if exists "recommendation_traces_select_own" on public.recommendation_traces;
create policy "recommendation_traces_select_own"
  on public.recommendation_traces
  for select
  using (auth.uid() = user_id);

drop policy if exists "recommendation_traces_insert_own" on public.recommendation_traces;
create policy "recommendation_traces_insert_own"
  on public.recommendation_traces
  for insert
  with check (auth.uid() = user_id);
