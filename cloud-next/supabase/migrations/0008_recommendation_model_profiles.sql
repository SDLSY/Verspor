create table if not exists public.recommendation_model_profiles (
  id uuid primary key default gen_random_uuid(),
  model_code text not null,
  profile_code text not null,
  status text not null default 'draft',
  description text,
  thresholds_json jsonb not null default '{}'::jsonb,
  weights_json jsonb not null default '{}'::jsonb,
  gate_rules_json jsonb not null default '{}'::jsonb,
  mode_priorities_json jsonb not null default '{}'::jsonb,
  confidence_formula_json jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint recommendation_model_profiles_status_check check (
    status in ('draft', 'active', 'archived')
  ),
  constraint recommendation_model_profiles_unique unique (model_code, profile_code)
);

create index if not exists idx_recommendation_model_profiles_status
  on public.recommendation_model_profiles (model_code, status, updated_at desc);

insert into public.recommendation_model_profiles (
  model_code,
  profile_code,
  status,
  description,
  thresholds_json,
  weights_json,
  gate_rules_json,
  mode_priorities_json,
  confidence_formula_json
)
values (
  'SRM_V2',
  'default_adult_cn',
  'active',
  'SRM_V2 默认成人中文配置，用于多源证据融合、安全门控、策略选择和解释置信度计算。',
  '{
    "sleepDisturbance": 60,
    "stressLoad": 60,
    "fatigueLoad": 60,
    "recoveryCapacityLow": 40,
    "followUpMissingInfo": 2,
    "doctorEvidenceLow": 0.45
  }'::jsonb,
  '{
    "evidenceCoverage": 0.45,
    "evidenceCount": 0.25,
    "hypothesisCount": 0.30
  }'::jsonb,
  '{
    "redFlagGate": "RED",
    "highMedicalRiskGate": "RED",
    "highPeriodRiskGate": "RED",
    "escalatedStageGate": "RED",
    "mediumRiskGate": "AMBER",
    "mediumPeriodRiskGate": "AMBER"
  }'::jsonb,
  '{
    "RED": ["ESCALATE"],
    "AMBER": ["RECOVERY", "STRESS_REGULATION", "FOLLOW_UP", "SLEEP_PREP", "STABILIZE"],
    "GREEN": ["SLEEP_PREP", "RECOVERY", "FOLLOW_UP", "STABILIZE", "STRESS_REGULATION"]
  }'::jsonb,
  '{
    "coverageWeight": 0.40,
    "missingPenaltyWeight": 0.35,
    "riskSignalWeight": 0.25
  }'::jsonb
)
on conflict (model_code, profile_code) do update
set
  status = excluded.status,
  description = excluded.description,
  thresholds_json = excluded.thresholds_json,
  weights_json = excluded.weights_json,
  gate_rules_json = excluded.gate_rules_json,
  mode_priorities_json = excluded.mode_priorities_json,
  confidence_formula_json = excluded.confidence_formula_json,
  updated_at = now();
