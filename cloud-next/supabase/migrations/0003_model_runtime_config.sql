alter table public.model_registry
  add column if not exists runtime_type text not null default 'fallback';

alter table public.model_registry
  add column if not exists inference_endpoint text;

alter table public.model_registry
  add column if not exists confidence_threshold numeric(5,4) not null default 0.60;

alter table public.model_registry
  add column if not exists fallback_enabled boolean not null default true;

alter table public.model_registry
  add column if not exists inference_timeout_ms int not null default 12000;

update public.model_registry
set runtime_type = 'fallback'
where runtime_type is null;

update public.model_registry
set confidence_threshold = 0.60
where confidence_threshold is null;

update public.model_registry
set fallback_enabled = true
where fallback_enabled is null;

update public.model_registry
set inference_timeout_ms = 12000
where inference_timeout_ms is null;

alter table public.model_registry
  add constraint model_registry_runtime_type_check
  check (runtime_type in ('fallback', 'http'));

alter table public.model_registry
  add constraint model_registry_confidence_threshold_check
  check (confidence_threshold >= 0 and confidence_threshold <= 1);

alter table public.model_registry
  add constraint model_registry_timeout_check
  check (inference_timeout_ms >= 1000 and inference_timeout_ms <= 120000);
