create table if not exists public.medication_analysis_records (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  record_id text not null,
  captured_at timestamptz not null default now(),
  image_uri text not null default '',
  recognized_name text not null default '',
  dosage_form text not null default '',
  specification text not null default '',
  active_ingredients_json jsonb not null default '[]'::jsonb,
  matched_symptoms_json jsonb not null default '[]'::jsonb,
  usage_summary text not null default '',
  risk_level text not null default 'LOW',
  risk_flags_json jsonb not null default '[]'::jsonb,
  evidence_notes_json jsonb not null default '[]'::jsonb,
  advice text not null default '',
  confidence real not null default 0,
  requires_manual_review boolean not null default false,
  analysis_mode text not null default 'MANUAL',
  provider_id text,
  model_id text,
  trace_id text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, record_id)
);

create index if not exists idx_medication_analysis_records_user_captured
  on public.medication_analysis_records (user_id, captured_at desc);

create index if not exists idx_medication_analysis_records_user_risk
  on public.medication_analysis_records (user_id, risk_level);

create table if not exists public.food_analysis_records (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  record_id text not null,
  captured_at timestamptz not null default now(),
  image_uri text not null default '',
  meal_type text not null default 'UNSPECIFIED',
  food_items_json jsonb not null default '[]'::jsonb,
  estimated_calories integer not null default 0,
  carbohydrate_grams real not null default 0,
  protein_grams real not null default 0,
  fat_grams real not null default 0,
  nutrition_risk_level text not null default 'LOW',
  nutrition_flags_json jsonb not null default '[]'::jsonb,
  daily_contribution text not null default '',
  advice text not null default '',
  confidence real not null default 0,
  requires_manual_review boolean not null default false,
  analysis_mode text not null default 'MANUAL',
  provider_id text,
  model_id text,
  trace_id text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, record_id)
);

create index if not exists idx_food_analysis_records_user_captured
  on public.food_analysis_records (user_id, captured_at desc);

create index if not exists idx_food_analysis_records_user_risk
  on public.food_analysis_records (user_id, nutrition_risk_level);

alter table public.medication_analysis_records enable row level security;
alter table public.food_analysis_records enable row level security;

drop policy if exists "medication_analysis_records_select_own" on public.medication_analysis_records;
create policy "medication_analysis_records_select_own"
  on public.medication_analysis_records
  for select
  using (auth.uid() = user_id);

drop policy if exists "medication_analysis_records_insert_own" on public.medication_analysis_records;
create policy "medication_analysis_records_insert_own"
  on public.medication_analysis_records
  for insert
  with check (auth.uid() = user_id);

drop policy if exists "medication_analysis_records_update_own" on public.medication_analysis_records;
create policy "medication_analysis_records_update_own"
  on public.medication_analysis_records
  for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

drop policy if exists "food_analysis_records_select_own" on public.food_analysis_records;
create policy "food_analysis_records_select_own"
  on public.food_analysis_records
  for select
  using (auth.uid() = user_id);

drop policy if exists "food_analysis_records_insert_own" on public.food_analysis_records;
create policy "food_analysis_records_insert_own"
  on public.food_analysis_records
  for insert
  with check (auth.uid() = user_id);

drop policy if exists "food_analysis_records_update_own" on public.food_analysis_records;
create policy "food_analysis_records_update_own"
  on public.food_analysis_records
  for update
  using (auth.uid() = user_id)
  with check (auth.uid() = user_id);

notify pgrst, 'reload schema';
