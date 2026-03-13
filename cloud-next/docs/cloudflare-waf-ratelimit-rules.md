# Cloudflare WAF and Rate-Limit Rules

## Zone assumptions

- API host: `api.your-domain.com`
- Origin: Vercel deployment
- Dynamic API prefix: `/api/`

## WAF custom rules

1. Block unknown host

```txt
Expression:
  http.host ne "api.your-domain.com"
Action:
  Block
```

2. Restrict API methods

```txt
Expression:
  starts_with(http.request.uri.path, "/api/") and not http.request.method in {"GET" "POST" "PUT"}
Action:
  Block
```

3. Enforce POST for upload and analyze

```txt
Expression:
  (starts_with(http.request.uri.path, "/api/sleep/upload") or
   starts_with(http.request.uri.path, "/api/sleep/analyze") or
   starts_with(http.request.uri.path, "/api/data/upload")) and http.request.method ne "POST"
Action:
  Block
```

4. Enforce bearer token on protected APIs

```txt
Expression:
  starts_with(http.request.uri.path, "/api/") and
  not starts_with(http.request.uri.path, "/api/auth/") and
  not len(http.request.headers["authorization"][0]) > 10
Action:
  Managed Challenge
```

## Rate limiting rules

1. Auth brute-force guard

```txt
Match:
  starts_with(http.request.uri.path, "/api/auth/") and http.request.method eq "POST"
Threshold:
  20 requests per 10 minutes per IP
Action:
  Block for 1 hour
```

2. Telemetry upload guard

```txt
Match:
  (starts_with(http.request.uri.path, "/api/sleep/upload") or
   starts_with(http.request.uri.path, "/api/data/upload")) and http.request.method eq "POST"
Threshold:
  120 requests per 5 minutes per IP
Action:
  Block for 10 minutes
```

3. Analyze queue protection

```txt
Match:
  starts_with(http.request.uri.path, "/api/sleep/analyze") and http.request.method eq "POST"
Threshold:
  60 requests per 5 minutes per IP
Action:
  Block for 10 minutes
```

## Cache rules

- Bypass cache for `starts_with(http.request.uri.path, "/api/")`
- Keep static asset cache policy unchanged
