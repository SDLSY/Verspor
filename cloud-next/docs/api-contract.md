# API Contract (Draft)

This contract defines the active Next.js + Supabase API behavior used by `cloud-next`.

## 1) POST /api/sleep/upload

### Request
```json
{
  "userId": "u_123",
  "sleepRecordId": "sleep_20260218",
  "date": 1760000000000,
  "bedTime": 1759970000000,
  "wakeTime": 1760000000000,
  "totalSleepMinutes": 480,
  "deepSleepMinutes": 120,
  "lightSleepMinutes": 240,
  "remSleepMinutes": 90
}
```

### Response
```json
{
  "code": 0,
  "message": "uploaded sleep_20260218",
  "success": true
}
```

## 2) POST /api/sleep/analyze

Compatibility endpoint for current Android flow.
Internally it should create/refresh a nightly inference job and return latest known result.

### Request (recommended multimodal window payload)
```json
{
  "userId": "u_123",
  "sleepRecordId": "sleep_20260218",
  "rawData": {
    "windowStart": 1760000000000,
    "windowEnd": 1760000030000,
    "hrFeatures": {
      "heartRate": 58,
      "heartRateAvg": 57,
      "heartRateMin": 53,
      "heartRateMax": 63
    },
    "spo2Features": {
      "bloodOxygen": 97,
      "bloodOxygenAvg": 96,
      "bloodOxygenMin": 94
    },
    "hrvFeatures": {
      "hrv": 61,
      "rmssd": 42,
      "sdnn": 48,
      "lfHfRatio": 1.6
    },
    "tempFeatures": {
      "temperature": 36.5,
      "temperatureAvg": 36.4,
      "temperatureTrend": -0.01
    },
    "motionFeatures": {
      "accelerometer": [0.02, 0.01, 1.00],
      "gyroscope": [0.01, 0.02, 0.00],
      "motionIntensity": 2.6
    },
    "ppgFeatures": {
      "ppg": [1234, 1240, 1238],
      "ppgValue": 1238
    },
    "anomalyScore": 0.18
  }
}
```

### Request compatibility notes
- If `rawData` only contains flat keys (for example `heartRate`, `bloodOxygen`, `temperature`, `hrv`), server will auto-map available keys into modality columns.
- If `windowStart/windowEnd` are missing, server falls back to current time and a default `30s` window.

### Response
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "sleepStages": ["LIGHT", "DEEP", "REM", "LIGHT", "AWAKE"],
    "sleepStages5": ["N2", "N3", "REM", "N2", "WAKE"],
    "recoveryScore": 82,
    "sleepQuality": "优秀",
    "insights": [
      "云端模型判定整体恢复较好。",
      "主要风险来自夜间血氧轻度波动。"
    ],
    "anomalyScore": 22,
    "modelVersion": "mmt-v1.0.0"
  }
}
```

## 3) POST /api/v1/inference/nightly

### Request
```json
{
  "userId": "u_123",
  "sleepRecordId": "sleep_20260218",
  "idempotencyKey": "u_123-sleep_20260218-v1"
}
```

### Response
```json
{
  "code": 0,
  "message": "accepted",
  "data": {
    "jobId": "job_abc123",
    "status": "queued"
  }
}
```

## 4) GET /api/v1/inference/nightly/:jobId

### Response
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "jobId": "job_abc123",
    "status": "succeeded",
    "modelVersion": "mmt-v1.0.0",
    "finishedAt": 1760001000000
  }
}
```

## 5) GET /api/v1/reports/nightly/:sleepRecordId

### Response
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "sleepRecordId": "sleep_20260218",
    "sleepStages5": ["N1", "N2", "N2", "N3", "REM"],
    "sleepStages": ["LIGHT", "LIGHT", "LIGHT", "DEEP", "REM"],
    "anomalyScore": 31,
    "recoveryScore": 76,
    "factors": ["spo2_variability", "hrv_drop"],
    "insights": ["存在轻中度波动，建议当日降低训练强度。"],
    "modelVersion": "mmt-v1.0.0"
  }
}
```

## 6) POST /api/data/upload

### Request (raw window ingestion)
```json
{
  "userId": "u_123",
  "deviceId": "ring_01",
  "sleepRecordId": "sleep_20260218",
  "timestamp": 1760000000000,
  "sensorData": {
    "windowStart": 1760000000000,
    "windowEnd": 1760000030000,
    "hrFeatures": { "heartRate": 58 },
    "spo2Features": { "bloodOxygen": 97 },
    "hrvFeatures": { "hrv": 61 },
    "tempFeatures": { "temperature": 36.5 },
    "motionFeatures": { "motionIntensity": 2.6 },
    "ppgFeatures": { "ppgValue": 1238 },
    "anomalyScore": 0.18
  }
}
```

### Behavior
- `sensorData` uses the same modality mapping as `/api/sleep/analyze`.
- `sleepRecordId` is optional; if missing server falls back to `raw_YYYYMMDD`.

## Error shape (all endpoints)
```json
{
  "code": 400,
  "message": "invalid payload",
  "success": false
}
```

## 7) POST /api/report/metrics/upsert

Stores one medical report summary and its structured metrics.

### Request
```json
{
  "reportId": "report_20260301_a",
  "reportDate": 1772304000000,
  "reportType": "PHOTO",
  "riskLevel": "MEDIUM",
  "metrics": [
    {
      "metricCode": "GLU",
      "metricName": "Fasting glucose",
      "metricValue": 6.8,
      "unit": "mmol/L",
      "refLow": 3.9,
      "refHigh": 6.1,
      "isAbnormal": true,
      "confidence": 0.92
    }
  ]
}
```

### Response
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "reportId": "report_20260301_a",
    "metricCount": 1
  }
}
```

## 8) GET /api/report/latest

### Response
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "reportId": "report_20260301_a",
    "reportDate": 1772304000000,
    "riskLevel": "MEDIUM",
    "abnormalCount": 2
  }
}
```

## 9) POST /api/intervention/task/upsert

### Request
```json
{
  "taskId": "task_123",
  "date": 1772304000000,
  "sourceType": "AI_COACH",
  "triggerReason": "3D hotspot tap",
  "bodyZone": "CHEST",
  "protocolType": "BREATH_4_7_8",
  "durationSec": 300,
  "plannedAt": 1772311200000,
  "status": "PENDING"
}
```

## 10) POST /api/intervention/execution/upsert

### Request
```json
{
  "executionId": "exe_123",
  "taskId": "task_123",
  "startedAt": 1772311200000,
  "endedAt": 1772311500000,
  "elapsedSec": 300,
  "beforeStress": 78,
  "afterStress": 61,
  "beforeHr": 82,
  "afterHr": 74,
  "effectScore": 84,
  "completionType": "FULL"
}
```

## 11) GET /api/intervention/effect/trend?days=7

### Response
```json
{
  "code": 0,
  "message": "ok",
  "data": [
    {
      "date": 1772236800000,
      "avgEffectScore": 79.5,
      "avgStressDrop": 11.2,
      "executionCount": 3
    }
  ]
}
```

## 12) POST /api/intervention/daily-prescription

Cloud prescription endpoint for the current Android intervention engine.

### Request
```json
{
  "triggerType": "DAILY_REFRESH",
  "domainScores": {
    "sleepDisturbance": 78,
    "stressLoad": 82,
    "fatigueLoad": 48,
    "recoveryCapacity": 42,
    "anxietyRisk": 70,
    "depressiveRisk": 18,
    "adherenceReadiness": 61
  },
  "evidenceFacts": {
    "sleep": ["近7天平均睡眠 5.9h", "ISI 17 分，中度失眠"],
    "stress": ["PSS-10 26 分，高压力"]
  },
  "redFlags": [],
  "ragContext": "",
  "catalog": [
    {
      "protocolCode": "SLEEP_WIND_DOWN_15M",
      "displayName": "睡前减刺激 15 分钟",
      "interventionType": "SLEEP_WIND_DOWN",
      "description": "帮助睡前降刺激和固定入睡节律"
    }
  ]
}
```

### Response
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "primaryGoal": "降低晚间高唤醒并改善入睡准备度",
    "riskLevel": "MEDIUM",
    "targetDomains": ["sleepDisturbance", "stressLoad"],
    "primaryInterventionType": "SLEEP_WIND_DOWN_15M",
    "secondaryInterventionType": "BODY_SCAN_NSDR_10M",
    "lifestyleTaskCodes": ["TASK_LIMIT_CAFFEINE_AFTER_1400"],
    "timing": "BEFORE_SLEEP",
    "durationSec": 900,
    "rationale": "结合近期睡眠不足与高压力证据，优先安排睡前减刺激并辅以身体扫描。",
    "evidence": ["近7天平均睡眠 5.9h", "PSS-10 26 分，高压力"],
    "contraindications": [],
    "followupMetric": "sleep_latency",
    "metadata": {
      "providerId": "openrouter",
      "snapshotId": "uuid",
      "recommendationId": "uuid",
      "isFallback": false
    }
  },
  "traceId": "trace_abcd1234"
}
```
