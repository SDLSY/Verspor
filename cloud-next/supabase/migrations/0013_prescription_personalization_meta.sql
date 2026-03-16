alter table if exists public.prescription_snapshots
  add column if not exists personalization_level text not null default 'PREVIEW';

alter table if exists public.prescription_snapshots
  add column if not exists missing_inputs_json jsonb not null default '[]'::jsonb;

create index if not exists idx_prescription_snapshots_user_personalization
  on public.prescription_snapshots (user_id, personalization_level, snapshot_date desc);
