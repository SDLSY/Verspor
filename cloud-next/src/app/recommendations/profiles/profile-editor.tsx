"use client";

import { useMemo, useState } from "react";

type NumericRecord = Record<string, number>;
type ScalarRecord = Record<string, string | number | boolean>;
type PriorityRecord = Record<string, string[]>;
type ProfileStatus = "draft" | "active" | "archived";

type ProfileEditorProps = {
  modelCode: string;
  initialProfileCode: string;
  initialStatus: ProfileStatus;
  initialDescription: string;
  initialThresholds: NumericRecord;
  initialWeights: NumericRecord;
  initialGateRules: ScalarRecord;
  initialModePriorities: PriorityRecord;
  initialConfidenceFormula: NumericRecord;
  submitLabel: string;
  lockedProfileCode?: boolean;
};

type FieldMeta = {
  key: string;
  label: string;
  hint: string;
  step?: string;
  min?: number;
  max?: number;
};

const thresholdFields: FieldMeta[] = [
  { key: "sleepDisturbance", label: "睡眠扰动阈值", hint: "超过该值时优先考虑睡前准备与作息收口", min: 0, max: 100 },
  { key: "stressLoad", label: "压力负荷阈值", hint: "超过该值时抬升压力调节与恢复优先级", min: 0, max: 100 },
  { key: "fatigueLoad", label: "疲劳负荷阈值", hint: "超过该值时倾向恢复型建议", min: 0, max: 100 },
  { key: "recoveryCapacityLow", label: "恢复能力低阈值", hint: "低于该值时进入恢复优先模式", min: 0, max: 100 },
  { key: "followUpMissingInfo", label: "继续追问阈值", hint: "缺失信息达到该值时触发 FOLLOW_UP", min: 0, max: 10 },
  { key: "doctorEvidenceLow", label: "医生证据下限", hint: "低于该覆盖率时建议继续追问补充证据", min: 0, max: 1, step: "0.01" },
];

const weightFields: FieldMeta[] = [
  { key: "evidenceCoverage", label: "证据覆盖权重", hint: "控制整体证据完整度在决策中的占比", min: 0, max: 1, step: "0.01" },
  { key: "evidenceCount", label: "证据条目权重", hint: "控制证据数量对解释置信度的影响", min: 0, max: 1, step: "0.01" },
  { key: "hypothesisCount", label: "假设数量权重", hint: "控制域级假设命中数的贡献", min: 0, max: 1, step: "0.01" },
];

const confidenceFields: FieldMeta[] = [
  { key: "coverageWeight", label: "覆盖率权重", hint: "证据覆盖率对 explanation confidence 的贡献", min: 0, max: 1, step: "0.01" },
  { key: "missingPenaltyWeight", label: "缺失惩罚权重", hint: "缺失信息对最终置信度的惩罚力度", min: 0, max: 1, step: "0.01" },
  { key: "riskSignalWeight", label: "风险信号权重", hint: "高风险信号对解释置信度的强化权重", min: 0, max: 1, step: "0.01" },
];

const knownGateLabels: Record<string, string> = {
  redFlagGate: "红旗症状闸门",
  highMedicalRiskGate: "高风险医检闸门",
  highPeriodRiskGate: "高风险周期闸门",
  escalatedStageGate: "升级问诊闸门",
  mediumRiskGate: "中风险闸门",
  mediumPeriodRiskGate: "中风险周期闸门",
};

const gateOptions = ["OFF", "GREEN", "AMBER", "RED"];
const modeOptions = ["ESCALATE", "RECOVERY", "STRESS_REGULATION", "FOLLOW_UP", "SLEEP_PREP", "STABILIZE"];
const priorityKeys = ["RED", "AMBER", "GREEN"];

function cloneNumberRecord(value: NumericRecord): NumericRecord {
  return { ...value };
}

function cloneScalarRecord(value: ScalarRecord): ScalarRecord {
  return { ...value };
}

function clonePriorityRecord(value: PriorityRecord): PriorityRecord {
  return Object.fromEntries(Object.entries(value).map(([key, items]) => [key, [...items]]));
}

function sumValues(record: NumericRecord): number {
  return Object.values(record).reduce((total, value) => total + (Number.isFinite(value) ? value : 0), 0);
}

function normalizePriority(items: string[]): string[] {
  return Array.from(new Set(items.map((item) => item.trim()).filter(Boolean)));
}

export function RecommendationProfileEditor({
  modelCode,
  initialProfileCode,
  initialStatus,
  initialDescription,
  initialThresholds,
  initialWeights,
  initialGateRules,
  initialModePriorities,
  initialConfidenceFormula,
  submitLabel,
  lockedProfileCode = false,
}: ProfileEditorProps) {
  const [profileCode, setProfileCode] = useState(initialProfileCode);
  const [status, setStatus] = useState<ProfileStatus>(initialStatus);
  const [description, setDescription] = useState(initialDescription);
  const [thresholds, setThresholds] = useState<NumericRecord>(() => cloneNumberRecord(initialThresholds));
  const [weights, setWeights] = useState<NumericRecord>(() => cloneNumberRecord(initialWeights));
  const [gateRules, setGateRules] = useState<ScalarRecord>(() => cloneScalarRecord(initialGateRules));
  const [modePriorities, setModePriorities] = useState<PriorityRecord>(() => clonePriorityRecord(initialModePriorities));
  const [confidenceFormula, setConfidenceFormula] = useState<NumericRecord>(() => cloneNumberRecord(initialConfidenceFormula));

  const gateEntries = useMemo(() => {
    const known = Object.keys(knownGateLabels).filter((key) => key in gateRules);
    const extras = Object.keys(gateRules).filter((key) => !(key in knownGateLabels));
    return [...known, ...extras];
  }, [gateRules]);

  const previewJson = useMemo(
    () =>
      JSON.stringify(
        {
          modelCode,
          profileCode,
          status,
          description,
          thresholds,
          weights,
          gateRules,
          modePriorities,
          confidenceFormula,
        },
        null,
        2
      ),
    [confidenceFormula, description, gateRules, modePriorities, modelCode, profileCode, status, thresholds, weights]
  );

  const thresholdAverage = useMemo(() => {
    const values = Object.values(thresholds);
    if (values.length === 0) return 0;
    return Math.round(sumValues(thresholds) / values.length);
  }, [thresholds]);

  const weightTotal = useMemo(() => sumValues(weights), [weights]);
  const confidenceTotal = useMemo(() => sumValues(confidenceFormula), [confidenceFormula]);

  const updateNumericRecord = (
    setter: React.Dispatch<React.SetStateAction<NumericRecord>>,
    key: string,
    value: string
  ) => {
    setter((current) => ({
      ...current,
      [key]: value === "" ? 0 : Number(value),
    }));
  };

  const updateGateRule = (key: string, value: string) => {
    setGateRules((current) => ({
      ...current,
      [key]: value,
    }));
  };

  const updatePriorityLane = (laneKey: string, value: string) => {
    setModePriorities((current) => ({
      ...current,
      [laneKey]: normalizePriority(value.split(",")),
    }));
  };

  return (
    <div className="admin-editor-shell">
      <div className="admin-editor-main">
        <section className="admin-editor-panel">
          <div className="admin-editor-panel-header">
            <div>
              <h4>基础配置</h4>
              <p>先确定 profile 的身份、状态与用途，再编辑具体策略参数。</p>
            </div>
          </div>
          <div className="admin-editor-field-grid two-up">
            <label className="admin-field">
              <span>模型代码</span>
              <input className="admin-input" value={modelCode} readOnly />
            </label>
            <label className="admin-field">
              <span>Profile Code</span>
              <input
                className="admin-input"
                name="profileCode"
                value={profileCode}
                onChange={(event) => setProfileCode(event.target.value)}
                readOnly={lockedProfileCode}
                required
              />
            </label>
            <label className="admin-field">
              <span>状态</span>
              <select className="admin-select" name="status" value={status} onChange={(event) => setStatus(event.target.value as ProfileStatus)}>
                <option value="draft">草稿</option>
                <option value="active">激活</option>
                <option value="archived">归档</option>
              </select>
            </label>
            <label className="admin-field">
              <span>描述</span>
              <input
                className="admin-input"
                name="description"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                placeholder="例如：成人中文默认策略，适合睡眠恢复场景"
              />
            </label>
          </div>
        </section>

        <section className="admin-editor-panel">
          <div className="admin-editor-panel-header">
            <div>
              <h4>证据阈值</h4>
              <p>决定系统何时把用户状态提升为睡前准备、压力调节、恢复或继续追问。</p>
            </div>
          </div>
          <div className="admin-editor-card-grid">
            {thresholdFields.map((field) => (
              <label key={field.key} className="admin-editor-card">
                <span className="admin-editor-card-title">{field.label}</span>
                <span className="admin-editor-card-copy">{field.hint}</span>
                <input
                  className="admin-input"
                  type="number"
                  min={field.min}
                  max={field.max}
                  step={field.step ?? "1"}
                  value={String(thresholds[field.key] ?? 0)}
                  onChange={(event) => updateNumericRecord(setThresholds, field.key, event.target.value)}
                />
              </label>
            ))}
          </div>
        </section>

        <section className="admin-editor-panel">
          <div className="admin-editor-panel-header">
            <div>
              <h4>解释权重</h4>
              <p>控制 explanation confidence 的主要构成，决定系统是更看重覆盖率、证据数量还是假设命中数。</p>
            </div>
          </div>
          <div className="admin-editor-card-grid compact">
            {weightFields.map((field) => (
              <label key={field.key} className="admin-editor-card compact">
                <span className="admin-editor-card-title">{field.label}</span>
                <span className="admin-editor-card-copy">{field.hint}</span>
                <input
                  className="admin-input"
                  type="number"
                  min={field.min}
                  max={field.max}
                  step={field.step ?? "0.01"}
                  value={String(weights[field.key] ?? 0)}
                  onChange={(event) => updateNumericRecord(setWeights, field.key, event.target.value)}
                />
              </label>
            ))}
          </div>
        </section>

        <section className="admin-editor-panel">
          <div className="admin-editor-panel-header">
            <div>
              <h4>安全门控</h4>
              <p>高风险、红旗和周期异常优先级始终高于个性化建议，避免生成层越权。</p>
            </div>
          </div>
          <div className="admin-editor-field-grid two-up">
            {gateEntries.map((key) => (
              <label key={key} className="admin-field">
                <span>{knownGateLabels[key] ?? key}</span>
                <select
                  className="admin-select"
                  value={String(gateRules[key] ?? "OFF")}
                  onChange={(event) => updateGateRule(key, event.target.value)}
                >
                  {gateOptions.map((option) => (
                    <option key={option} value={option}>
                      {option}
                    </option>
                  ))}
                </select>
              </label>
            ))}
          </div>
        </section>

        <section className="admin-editor-panel">
          <div className="admin-editor-panel-header">
            <div>
              <h4>建议模式优先级</h4>
              <p>按风险分层配置模式顺序，系统会优先命中靠前的 recommendation mode。</p>
            </div>
          </div>
          <div className="admin-editor-lane-grid">
            {priorityKeys.map((laneKey) => (
              <label key={laneKey} className="admin-editor-lane">
                <span className="admin-editor-lane-title">{laneKey} 风险层</span>
                <span className="admin-editor-lane-copy">使用英文逗号分隔。可选模式：{modeOptions.join(" / ")}</span>
                <input
                  className="admin-input"
                  value={(modePriorities[laneKey] ?? []).join(", ")}
                  onChange={(event) => updatePriorityLane(laneKey, event.target.value)}
                />
              </label>
            ))}
          </div>
        </section>

        <section className="admin-editor-panel">
          <div className="admin-editor-panel-header">
            <div>
              <h4>置信度公式</h4>
              <p>这一组权重控制 explanation 层如何把证据覆盖、缺失惩罚和风险信号映射成最终置信度。</p>
            </div>
          </div>
          <div className="admin-editor-card-grid compact">
            {confidenceFields.map((field) => (
              <label key={field.key} className="admin-editor-card compact">
                <span className="admin-editor-card-title">{field.label}</span>
                <span className="admin-editor-card-copy">{field.hint}</span>
                <input
                  className="admin-input"
                  type="number"
                  min={field.min}
                  max={field.max}
                  step={field.step ?? "0.01"}
                  value={String(confidenceFormula[field.key] ?? 0)}
                  onChange={(event) => updateNumericRecord(setConfidenceFormula, field.key, event.target.value)}
                />
              </label>
            ))}
          </div>
        </section>

        <input type="hidden" name="modelCode" value={modelCode} />
        <input type="hidden" name="thresholdsJson" value={JSON.stringify(thresholds)} />
        <input type="hidden" name="weightsJson" value={JSON.stringify(weights)} />
        <input type="hidden" name="gateRulesJson" value={JSON.stringify(gateRules)} />
        <input type="hidden" name="modePrioritiesJson" value={JSON.stringify(modePriorities)} />
        <input type="hidden" name="confidenceFormulaJson" value={JSON.stringify(confidenceFormula)} />

        <div className="admin-button-row left-align">
          <button className="admin-primary-button" type="submit">{submitLabel}</button>
        </div>
      </div>

      <aside className="admin-editor-sidebar">
        <section className="admin-editor-panel sticky">
          <div className="admin-editor-panel-header">
            <div>
              <h4>策略摘要</h4>
              <p>提交前先看核心参数是否合理，避免激活明显失衡的 profile。</p>
            </div>
          </div>
          <div className="admin-editor-summary-grid">
            <article className="admin-editor-summary-card">
              <span>阈值平均值</span>
              <strong>{thresholdAverage}</strong>
            </article>
            <article className="admin-editor-summary-card">
              <span>解释权重合计</span>
              <strong>{weightTotal.toFixed(2)}</strong>
            </article>
            <article className="admin-editor-summary-card">
              <span>置信度权重合计</span>
              <strong>{confidenceTotal.toFixed(2)}</strong>
            </article>
          </div>
          <div className="admin-editor-preview-block">
            <div className="admin-editor-preview-header">
              <span>实时 JSON 预览</span>
              <code>{profileCode || "未命名 profile"}</code>
            </div>
            <pre className="admin-code-block admin-editor-preview">{previewJson}</pre>
          </div>
        </section>
      </aside>
    </div>
  );
}
