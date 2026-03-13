create table if not exists public.model_registry (
  id uuid primary key default gen_random_uuid(),
  model_kind text not null,
  version text not null,
  artifact_path text,
  feature_schema_version text not null default 'v1',
  is_active boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(model_kind, version)
);

create index if not exists idx_model_registry_kind_active on public.model_registry(model_kind, is_active);

insert into public.model_registry (model_kind, version, artifact_path, feature_schema_version, is_active)
select 'sleep-multimodal', 'mmt-v1', 'models/sleep-multimodal/mmt-v1', 'v1', true
where not exists (
  select 1 from public.model_registry where model_kind = 'sleep-multimodal'
);

alter table public.model_registry enable row level security;

drop policy if exists model_registry_select_authed on public.model_registry;
drop policy if exists audit_events_insert_own on public.audit_events;

create policy model_registry_select_authed on public.model_registry
  for select using (auth.role() = 'authenticated');

create policy audit_events_insert_own on public.audit_events
  for insert with check (auth.uid() = user_id);
