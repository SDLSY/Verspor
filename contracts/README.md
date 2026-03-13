# Contracts (v2)

Cross-stack contracts for Android, Cloud, and ML.

- `schemas/`: JSON Schema source of truth.
- `typescript/`: generated or hand-maintained TS types for cloud.
- `kotlin/`: generated target DTOs for Android/core modules.

Current shared AI types:
- `AiProviderId`
- `AiCapability`
- `AiLogicalModelId`
- `AiExecutionMode`
- `AiJobStatus`

Envelope policy:
`{ code, message, data, traceId }`
