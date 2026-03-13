# Cloudflare Security Checklist

## Objective

Protect Vercel-hosted Next.js APIs that handle sensitive wearable telemetry and inference outputs.

## Baseline Controls

1. Proxy only the API domain through Cloudflare.
2. Enable WAF managed rules for the API host.
3. Add endpoint-specific rate limits.
4. Bypass cache for all dynamic API routes.
5. Keep service-side auth and authorization in backend logic and Supabase RLS.

## DNS and TLS

- Use proxied DNS records for `api.<your-domain>`.
- Use strict TLS mode between Cloudflare and origin.
- Keep certificate renewal paths reachable.

## WAF Rules

Recommended first-pass custom rules:

1. Block unexpected hostnames.
2. Block unexpected HTTP methods for `/api/*`.
3. Require `POST` on ingest paths.
4. Challenge or block obvious abuse signatures.

## Rate Limiting

Apply different thresholds by endpoint class:

- Auth endpoints: strict.
- Telemetry upload endpoints: moderate and burst-tolerant.
- Report query endpoints: moderate.

Use both IP and stable client signal when available.

## Cache Policy

- Bypass cache for `/api/*`.
- Do not cache verification or certificate challenge paths.
- Keep static web assets on normal cache policy.

## Origin Protection

- Enforce host allowlist in Next.js for API routes.
- Reject direct unexpected host traffic.
- Keep internal worker endpoint protected with shared secret header.

## Logging and Monitoring

- Capture Cloudflare firewall and request logs.
- Correlate logs using request IDs.
- Alert on spikes in 4xx/5xx and sudden rate-limit triggers.

## Verification Checklist

- Confirm unauthorized host request is blocked.
- Confirm API cache bypass is effective.
- Confirm rate-limit rules trigger under synthetic load.
- Confirm normal mobile upload traffic is not challenged incorrectly.
