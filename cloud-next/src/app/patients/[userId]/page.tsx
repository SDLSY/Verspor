import type { Route } from "next";
import Link from "next/link";
import { AdminShell } from "@/components/admin-shell";
import {
  AdminPill,
  AdminSectionCard,
  AdminStatCard,
  EmptyState,
  formatDate,
  formatDateTime,
  formatMinutes,
  formatRiskLabel,
  formatScore,
  formatSeconds,
  formatSleepQualityLabel,
  formatStatusLabel,
  toPillTone,
} from "@/components/admin-ui";
import {
  formatBodyZoneLabel,
  formatParseStatusLabel,
  formatProtocolTypeLabel,
  formatReportTypeLabel,
  formatSourceTypeLabel,
  formatTimelineTypeLabel,
} from "@/lib/admin-labels";
import { requireAdminPage } from "@/lib/admin-auth";
import { getAdminScenarioInfo } from "@/lib/admin-story";
import {
  getAdminPatientInterventions,
  getAdminPatientMedical,
  getAdminPatientOverview,
  getAdminPatientSleep,
  getAdminPatientTimeline,
} from "@/lib/admin-patients";
import { listAdminRecommendationTraces } from "@/lib/admin-recommendations";
import { createServiceClient } from "@/lib/supabase";
import { saveInterventionTask } from "./actions";

type SearchParams = Record<string, string | string[] | undefined>;
type Params = { userId: string };
type Row = Record<string, unknown>;

const timelineTypeOptions = [
  "sleep_session",
  "inference_job",
  "nightly_report",
  "intervention_task",
  "intervention_execution",
  "medical_report",
  "audit_event",
] as const;

function pickFirst(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

function pickList(value: string | string[] | undefined): string[] {
  if (Array.isArray(value)) {
    return value.flatMap((entry) => entry.split(",")).map((entry) => entry.trim()).filter(Boolean);
  }
  if (!value) {
    return [];
  }
  return value.split(",").map((entry) => entry.trim()).filter(Boolean);
}

function readString(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function readNumber(value: unknown): number | null {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function readStringArray(value: unknown): string[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((item) => (typeof item === "string" ? item.trim() : "")).filter(Boolean);
}

function isMissingRelationMessage(message: string | null | undefined): boolean {
  const normalized = (message ?? "").toLowerCase();
  return (
    normalized.includes("could not find the table") ||
    normalized.includes("schema cache") ||
    normalized.includes("does not exist")
  );
}

async function resolveOptionalData<T>(
  query: PromiseLike<{ data: T | null; error: { message: string } | null }>,
  fallback: T
): Promise<T> {
  const { data, error } = await query;
  if (error) {
    if (isMissingRelationMessage(error.message)) {
      return fallback;
    }
    throw new Error(error.message);
  }
  return data ?? fallback;
}

function formatBaselineStatus(completedCount: number | null, freshnessUntil: number | null): string {
  if (typeof completedCount !== "number" || completedCount <= 0) {
    return "仍有量表待补充";
  }
  if (freshnessUntil && freshnessUntil > Date.now()) {
    return "量表基线仍在有效期内";
  }
  return "建议重新完成量表";
}

function formatExecutionFeedback(executionCount: number, avgEffectScore: number | null | undefined): string {
  if (executionCount <= 0) {
    return "尚无执行回写，适合在演示时现场补一条干预动作。";
  }
  return `执行 ${executionCount} 次 / 平均效果 ${formatScore(avgEffectScore)}`;
}

export default async function PatientDetailPage({
  params,
  searchParams,
}: {
  params: Promise<Params>;
  searchParams?: Promise<SearchParams>;
}) {
  await requireAdminPage();
  const resolvedParams = await params;
  const resolvedSearch = (await searchParams) ?? {};
  const userId = resolvedParams.userId;
  const compose = pickFirst(resolvedSearch.compose) === "1";
  const timelineTypes = pickList(resolvedSearch.types);
  const noticeSaved = pickFirst(resolvedSearch.saved) === "1";
  const noticeError = pickFirst(resolvedSearch.error);
  const saveTaskAction = saveInterventionTask.bind(null, userId);
  const client = createServiceClient();

  const [overview, sleep, interventions, medical, timeline, traces, baselineSnapshot, inquirySummaries] =
    await Promise.all([
      getAdminPatientOverview(userId),
      getAdminPatientSleep(userId),
      getAdminPatientInterventions(userId),
      getAdminPatientMedical(userId),
      getAdminPatientTimeline(userId, { types: timelineTypes }),
      listAdminRecommendationTraces({ userId, days: 30, pageSize: 12 }),
      resolveOptionalData(
        client
          .from("assessment_baseline_snapshots")
          .select("completed_count,freshness_until")
          .eq("user_id", userId)
          .order("freshness_until", { ascending: false })
          .limit(1)
          .maybeSingle<Row>(),
        null
      ),
      resolveOptionalData(
        client
          .from("doctor_inquiry_summaries")
          .select("assessed_at,risk_level,red_flags_json,doctor_summary")
          .eq("user_id", userId)
          .order("assessed_at", { ascending: false })
          .limit(5)
          .returns<Row[]>(),
        []
      ),
    ]);

  const baselineCompletedCount = baselineSnapshot ? readNumber(baselineSnapshot.completed_count) : null;
  const baselineFreshnessUntil = baselineSnapshot ? Date.parse(readString(baselineSnapshot.freshness_until)) : null;
  const latestInquiry = inquirySummaries[0] ?? null;
  const latestInquiryRedFlags = latestInquiry ? readStringArray(latestInquiry.red_flags_json) : [];
  const patientTitle = overview.identity.displayName || overview.identity.email || overview.identity.userId;
  const latestSleepRecord = sleep.records[0] ?? null;
  const trendPreview = sleep.trend.slice(-7);
  const scenarioInfo = getAdminScenarioInfo({ email: overview.identity.email, user_metadata: null });

  return (
    <AdminShell
      section="patients"
      activePath="/patients"
      title={patientTitle}
      subtitle={scenarioInfo.summary ?? "围绕当前判断、关键证据、建议与回写结果组织患者工作台。"}
      actions={
        <div className="admin-button-row">
          <Link href={("/patients" as Route)} className="admin-secondary-button link-button">
            返回患者工作台
          </Link>
          <Link href={`/patients/${userId}?compose=1#interventions` as Route} className="admin-primary-button link-button">
            新建干预任务
          </Link>
        </div>
      }
    >
      <section className="admin-stat-grid four-up">
        <AdminStatCard
          label="当前风险"
          value={formatRiskLabel(overview.latestMedical.riskLevel)}
          detail={`高风险问诊 ${inquirySummaries.filter((item) => readString(item.risk_level).toUpperCase() === "HIGH").length} 次`}
          tone={toPillTone(overview.latestMedical.riskLevel, "risk")}
        />
        <AdminStatCard
          label="最新恢复分"
          value={formatScore(overview.latestSleep.recoveryScore)}
          detail={`睡眠质量 ${formatSleepQualityLabel(overview.latestSleep.sleepQuality)}`}
          tone="info"
        />
        <AdminStatCard
          label="待执行干预"
          value={String(overview.latestIntervention.pendingCount)}
          detail={`最近效果分 ${formatScore(overview.latestIntervention.latestExecutionEffectScore)}`}
          tone="warning"
        />
        <AdminStatCard
          label="近 30 天建议"
          value={String(traces.summary.totalTraces)}
          detail={`主模式 ${traces.summary.topRecommendationMode ?? "-"}`}
          tone="success"
        />
      </section>

      <nav className="admin-subnav" aria-label="患者工作台章节">
        <a href="#overview" className="admin-subnav-link is-active">当前判断</a>
        <a href="#evidence" className="admin-subnav-link">关键证据</a>
        <a href="#reports" className="admin-subnav-link">报告与问诊</a>
        <a href="#recommendations" className="admin-subnav-link">建议与干预</a>
        <a href="#interventions" className="admin-subnav-link">结果回写</a>
        <a href="#timeline" className="admin-subnav-link">时间线</a>
      </nav>

      <div className="admin-stack">
        <AdminSectionCard title="当前判断" actions={<span id="overview" className="admin-table-secondary">{overview.identity.userId}</span>}>
          <section className="admin-grid two-up">
            <div className="admin-panel subtle">
              <div className="admin-panel-header">
                <div>
                  <h3>患者身份</h3>
                  <p>查看账号、最近活跃和最近睡眠记录。</p>
                </div>
              </div>
              <dl className="admin-definition-list">
                <div><dt>邮箱</dt><dd>{overview.identity.email || "-"}</dd></div>
                <div><dt>用户 ID</dt><dd>{overview.identity.userId}</dd></div>
                <div><dt>创建时间</dt><dd>{formatDateTime(overview.identity.createdAt)}</dd></div>
                <div><dt>最近睡眠日期</dt><dd>{formatDate(overview.latestSleep.sessionDate)}</dd></div>
              </dl>
            </div>

            <div className="admin-panel subtle">
              <div className="admin-panel-header">
                <div>
                  <h3>当前待处理事项</h3>
                  <p>先看风险、量表完整度，再决定讲报告、建议还是高级运维。</p>
                </div>
              </div>
              <div className="admin-stack compact">
                {scenarioInfo.scenarioLabel ? (
                  <div className="admin-inline-row between">
                    <span className="admin-table-secondary">演示场景</span>
                    <AdminPill tone="info">{scenarioInfo.scenarioLabel}</AdminPill>
                  </div>
                ) : null}
                <div className="admin-inline-row between">
                  <span className="admin-table-secondary">量表状态</span>
                  <AdminPill tone={baselineCompletedCount && baselineCompletedCount > 0 ? "info" : "warning"}>
                    {formatBaselineStatus(baselineCompletedCount, baselineFreshnessUntil)}
                  </AdminPill>
                </div>
                <div className="admin-inline-row between">
                  <span className="admin-table-secondary">最新作业</span>
                  <span>{overview.latestJob.status ? formatStatusLabel(overview.latestJob.status) : "暂无作业"}</span>
                </div>
                <div className="admin-inline-row between">
                  <span className="admin-table-secondary">最近报告</span>
                  <span>{medical.latestReport ? `${formatReportTypeLabel(medical.latestReport.reportType)} / ${formatRiskLabel(medical.latestReport.riskLevel)}` : "暂无报告"}</span>
                </div>
                {overview.latestFailedJob ? (
                  <div className="admin-alert danger">
                    最近失败作业：{overview.latestFailedJob.jobId}
                    <div className="admin-table-secondary">{overview.latestFailedJob.errorMessage || "请转到系统运维查看详情。"}</div>
                  </div>
                ) : null}
              </div>
            </div>
          </section>
        </AdminSectionCard>

        <AdminSectionCard title="关键证据" actions={<span id="evidence" className="admin-table-secondary">统一查看睡眠、量表、医检与问诊</span>}>
          <section className="admin-grid three-up">
            <div className="admin-panel subtle">
              <div className="admin-panel-header"><div><h3>睡眠证据</h3><p>最近一晚与近 7 天趋势。</p></div></div>
              {latestSleepRecord ? (
                <div className="admin-stack compact">
                  <div className="admin-inline-row between"><span>总睡眠</span><strong>{formatMinutes(latestSleepRecord.totalSleepMinutes)}</strong></div>
                  <div className="admin-inline-row between"><span>恢复分</span><strong>{formatScore(latestSleepRecord.recoveryScore)}</strong></div>
                  <div className="admin-inline-row between"><span>异常分</span><strong>{formatScore(latestSleepRecord.anomalyScore)}</strong></div>
                  <div className="admin-inline-row between"><span>模型版本</span><span>{latestSleepRecord.modelVersion || "-"}</span></div>
                  {trendPreview.length > 0 ? (
                    <div className="admin-stack compact">
                      <span className="admin-table-secondary">近 7 天恢复分</span>
                      <div className="admin-pill-row wrap">
                        {trendPreview.map((item) => (
                          <AdminPill key={item.date} tone="info">{`${formatDate(item.date)} ${Math.round(item.recoveryScore)}`}</AdminPill>
                        ))}
                      </div>
                    </div>
                  ) : null}
                </div>
              ) : (
                <EmptyState title="暂无睡眠证据" description="该患者尚未上传可用的睡眠会话。" />
              )}
            </div>

            <div className="admin-panel subtle">
              <div className="admin-panel-header"><div><h3>量表与问诊</h3><p>优先判断基线完整度和最近问诊风险。</p></div></div>
              <div className="admin-stack compact">
                <div className="admin-inline-row between"><span>量表完成数</span><strong>{baselineCompletedCount ?? 0}</strong></div>
                <div className="admin-inline-row between"><span>基线有效期</span><span>{baselineFreshnessUntil ? formatDate(baselineFreshnessUntil) : "-"}</span></div>
                <div className="admin-inline-row between"><span>最近问诊风险</span><strong>{latestInquiry ? formatRiskLabel(readString(latestInquiry.risk_level)) : "-"}</strong></div>
                {latestInquiryRedFlags.length > 0 ? (
                  <div className="admin-stack compact">
                    <span className="admin-table-secondary">红旗线索</span>
                    <div className="admin-pill-row wrap">
                      {latestInquiryRedFlags.slice(0, 4).map((flag) => (
                        <AdminPill key={flag} tone="danger">{flag}</AdminPill>
                      ))}
                    </div>
                  </div>
                ) : null}
              </div>
            </div>

            <div className="admin-panel subtle">
              <div className="admin-panel-header"><div><h3>医检证据</h3><p>最近报告与异常指标。</p></div></div>
              {medical.latestReport ? (
                <div className="admin-stack compact">
                  <div className="admin-inline-row between"><span>报告类型</span><strong>{formatReportTypeLabel(medical.latestReport.reportType)}</strong></div>
                  <div className="admin-inline-row between"><span>解析状态</span><strong>{formatParseStatusLabel(medical.latestReport.parseStatus)}</strong></div>
                  <div className="admin-inline-row between"><span>风险等级</span><strong>{formatRiskLabel(medical.latestReport.riskLevel)}</strong></div>
                  <div className="admin-inline-row between"><span>异常指标数</span><strong>{String(medical.latestMetrics.filter((item) => item.isAbnormal).length)}</strong></div>
                </div>
              ) : (
                <EmptyState title="暂无医检证据" description="患者尚未上传医疗报告。" />
              )}
            </div>
          </section>
        </AdminSectionCard>

        <AdminSectionCard title="建议与干预" actions={<span id="recommendations" className="admin-table-secondary">先讲建议内容，再按需展开技术细节</span>}>
          {traces.items.length > 0 ? (
            <div className="admin-table-wrapper">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>时间</th>
                    <th>建议是什么</th>
                    <th>为什么给出</th>
                    <th>执行结果</th>
                    <th>技术详情</th>
                  </tr>
                </thead>
                <tbody>
                  {traces.items.map((item) => (
                    <tr key={item.id}>
                      <td>{formatDateTime(item.createdAt)}</td>
                      <td>
                        <div className="admin-stack compact">
                          <strong>{item.summary}</strong>
                          <div className="admin-pill-row wrap">
                            {item.scenarioLabel ? <AdminPill tone="info">{item.scenarioLabel}</AdminPill> : null}
                            <AdminPill tone="info">{item.recommendationMode ?? "-"}</AdminPill>
                          </div>
                          {item.nextStep ? <span className="admin-table-secondary">下一步：{item.nextStep}</span> : null}
                        </div>
                      </td>
                      <td>
                        <div className="admin-stack compact">
                          {item.reasons.length > 0 ? <span>{item.reasons.join("；")}</span> : <span>-</span>}
                          <AdminPill tone="info">{item.recommendationMode ?? "-"}</AdminPill>
                          {item.safetyGate ? <AdminPill tone={toPillTone(item.safetyGate, "risk")}>{item.safetyGate}</AdminPill> : null}
                        </div>
                      </td>
                      <td>
                        <div className="admin-stack compact">
                          <AdminPill tone={toPillTone(item.riskLevel, "risk")}>{formatRiskLabel(item.riskLevel)}</AdminPill>
                          <span className="admin-table-secondary">{formatExecutionFeedback(item.executionCount, item.avgEffectScore)}</span>
                        </div>
                      </td>
                      <td>
                        <details className="admin-disclosure-card">
                          <summary>
                            <span>查看技术详情</span>
                            <AdminPill tone={item.isFallback ? "warning" : "success"}>
                              {item.isFallback ? "Fallback" : "Primary"}
                            </AdminPill>
                          </summary>
                          <div className="admin-disclosure-content">
                            <div className="admin-stack compact">
                              <span>trace：{item.traceId ?? item.id}</span>
                              <span>provider：{item.providerId ?? "-"}</span>
                              <span>profile：{item.profileCode ?? item.modelVersion ?? "-"}</span>
                              <span>source：{item.configSource ?? "-"}</span>
                            </div>
                          </div>
                        </details>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <EmptyState title="暂无建议轨迹" description="该患者近 30 天还没有可用的建议闭环记录。" />
          )}
        </AdminSectionCard>

        <AdminSectionCard
          title="结果回写"
          actions={<span id="interventions" className="admin-table-secondary">人工干预任务、执行结果与回写证据</span>}
        >
          {noticeSaved ? <div className="admin-alert success">干预任务已保存，患者工作台已刷新。</div> : null}
          {noticeError ? <div className="admin-alert danger">{noticeError}</div> : null}

          {compose ? (
            <form action={saveTaskAction} className="admin-form-grid card-grid">
              <label>
                <span>任务日期</span>
                <input type="datetime-local" name="taskDate" required />
              </label>
              <label>
                <span>来源类型</span>
                <select name="sourceType" defaultValue="RULE_ENGINE">
                  <option value="RULE_ENGINE">规则引擎</option>
                  <option value="ML_ENGINE">模型引擎</option>
                  <option value="MANUAL">人工录入</option>
                </select>
              </label>
              <label>
                <span>触发原因</span>
                <input type="text" name="triggerReason" placeholder="例如：恢复分偏低" />
              </label>
              <label>
                <span>身体部位</span>
                <select name="bodyZone" defaultValue="LIMB">
                  <option value="HEAD">头部</option>
                  <option value="CHEST">胸部</option>
                  <option value="BACK">背部</option>
                  <option value="ABDOMEN">腹部</option>
                  <option value="LIMB">四肢</option>
                </select>
              </label>
              <label>
                <span>方案类型</span>
                <select name="protocolType" defaultValue="LOW_ACTIVITY">
                  <option value="LOW_ACTIVITY">低强度方案</option>
                  <option value="HIGH_ACTIVITY">高强度方案</option>
                  <option value="BREATHING">呼吸训练</option>
                  <option value="RELAXATION">放松方案</option>
                  <option value="RECOVERY">恢复方案</option>
                </select>
              </label>
              <label>
                <span>时长（秒）</span>
                <input type="number" name="durationSec" min="10" step="10" defaultValue="180" />
              </label>
              <label>
                <span>计划开始</span>
                <input type="datetime-local" name="plannedAt" />
              </label>
              <label>
                <span>任务状态</span>
                <select name="status" defaultValue="PENDING">
                  <option value="PENDING">待处理</option>
                  <option value="RUNNING">运行中</option>
                  <option value="COMPLETED">已完成</option>
                </select>
              </label>
              <div className="admin-form-actions full-span">
                <button type="submit" className="admin-primary-button">保存任务</button>
                <Link href={`/patients/${userId}#interventions` as Route} className="admin-secondary-button link-button">取消</Link>
              </div>
            </form>
          ) : null}

          <section className="admin-grid two-up">
            <div className="admin-panel subtle">
              <div className="admin-panel-header"><div><h3>任务列表</h3><p>人工与系统任务统一查看。</p></div></div>
              {interventions.tasks.length > 0 ? (
                <div className="admin-table-wrapper">
                  <table className="admin-table compact-table">
                    <thead>
                      <tr>
                        <th>计划时间</th>
                        <th>方案</th>
                        <th>来源</th>
                        <th>状态</th>
                      </tr>
                    </thead>
                    <tbody>
                      {interventions.tasks.slice(0, 12).map((task) => (
                        <tr key={task.taskId}>
                          <td>{formatDateTime(task.plannedAt ?? task.taskDate)}</td>
                          <td>
                            <div className="admin-stack compact">
                              <strong>{formatProtocolTypeLabel(task.protocolType)}</strong>
                              <span className="admin-table-secondary">{formatBodyZoneLabel(task.bodyZone)} / {formatSeconds(task.durationSec)}</span>
                            </div>
                          </td>
                          <td>
                            <div className="admin-stack compact">
                              <span>{formatSourceTypeLabel(task.sourceType)}</span>
                              <span className="admin-table-secondary">{task.triggerReason ?? "-"}</span>
                            </div>
                          </td>
                          <td><AdminPill tone={toPillTone(task.status)}>{formatStatusLabel(task.status)}</AdminPill></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <EmptyState title="暂无干预任务" description="可从这里补录人工任务或等待系统生成。" />
              )}
            </div>

            <div className="admin-panel subtle">
              <div className="admin-panel-header"><div><h3>执行记录</h3><p>回看完成率、压力变化和效果分。</p></div></div>
              {interventions.executions.length > 0 ? (
                <div className="admin-table-wrapper">
                  <table className="admin-table compact-table">
                    <thead>
                      <tr>
                        <th>结束时间</th>
                        <th>任务</th>
                        <th>效果</th>
                        <th>完成方式</th>
                      </tr>
                    </thead>
                    <tbody>
                      {interventions.executions.slice(0, 12).map((execution) => (
                        <tr key={execution.executionId}>
                          <td>{formatDateTime(execution.endedAt ?? execution.startedAt)}</td>
                          <td>
                            <div className="admin-stack compact">
                              <strong>{execution.taskId}</strong>
                              <span className="admin-table-secondary">耗时 {formatSeconds(execution.elapsedSec)}</span>
                            </div>
                          </td>
                          <td>
                            <div className="admin-stack compact">
                              <span>效果分 {formatScore(execution.effectScore)}</span>
                              <span className="admin-table-secondary">压力 {formatScore(execution.beforeStress)} → {formatScore(execution.afterStress)}</span>
                            </div>
                          </td>
                          <td>{execution.completionType || "-"}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <EmptyState title="暂无执行记录" description="患者完成干预后，这里会汇总执行回看。" />
              )}
            </div>
          </section>
        </AdminSectionCard>

        <AdminSectionCard title="报告与问诊" actions={<span id="reports" className="admin-table-secondary">医检、OCR 和结构化问诊统一查看</span>}>
          <section className="admin-grid two-up">
            <div className="admin-panel subtle">
              <div className="admin-panel-header"><div><h3>报告与指标</h3><p>最近报告、风险和异常指标。</p></div></div>
              {medical.reports.length > 0 ? (
                <div className="admin-table-wrapper">
                  <table className="admin-table compact-table">
                    <thead>
                      <tr>
                        <th>报告日期</th>
                        <th>类型</th>
                        <th>解析状态</th>
                        <th>风险</th>
                      </tr>
                    </thead>
                    <tbody>
                      {medical.reports.slice(0, 10).map((report) => (
                        <tr key={report.reportId}>
                          <td>{formatDate(report.reportDate)}</td>
                          <td>{formatReportTypeLabel(report.reportType)}</td>
                          <td>{formatParseStatusLabel(report.parseStatus)}</td>
                          <td><AdminPill tone={toPillTone(report.riskLevel, "risk")}>{formatRiskLabel(report.riskLevel)}</AdminPill></td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <EmptyState title="暂无报告" description="导入医检报告后，这里会显示 OCR 和结构化结果。" />
              )}

              {medical.latestMetrics.length > 0 ? (
                <div className="admin-table-wrapper">
                  <table className="admin-table compact-table">
                    <thead>
                      <tr>
                        <th>指标</th>
                        <th>值</th>
                        <th>参考范围</th>
                        <th>状态</th>
                      </tr>
                    </thead>
                    <tbody>
                      {medical.latestMetrics.slice(0, 8).map((metric) => (
                        <tr key={metric.metricCode}>
                          <td>{metric.metricName}</td>
                          <td>{`${metric.metricValue}${metric.unit ? ` ${metric.unit}` : ""}`}</td>
                          <td>{metric.refLow != null || metric.refHigh != null ? `${metric.refLow ?? "-"} - ${metric.refHigh ?? "-"}` : "-"}</td>
                          <td>
                            {metric.isAbnormal ? <AdminPill tone="warning">异常</AdminPill> : <AdminPill tone="success">正常</AdminPill>}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : null}
            </div>

            <div className="admin-panel subtle">
              <div className="admin-panel-header"><div><h3>最近问诊摘要</h3><p>优先查看风险、摘要和是否存在红旗。</p></div></div>
              {latestInquiry ? (
                <div className="admin-stack compact">
                  <div className="admin-inline-row between"><span>问诊时间</span><strong>{formatDateTime(Date.parse(readString(latestInquiry.assessed_at)))}</strong></div>
                  <div className="admin-inline-row between"><span>风险等级</span><strong>{formatRiskLabel(readString(latestInquiry.risk_level))}</strong></div>
                  {readString(latestInquiry.doctor_summary) ? <div className="admin-alert info">{readString(latestInquiry.doctor_summary)}</div> : null}
                  {latestInquiryRedFlags.length > 0 ? (
                    <div className="admin-stack compact">
                      <span className="admin-table-secondary">红旗线索</span>
                      <div className="admin-pill-row wrap">
                        {latestInquiryRedFlags.map((flag) => (
                          <AdminPill key={flag} tone="danger">{flag}</AdminPill>
                        ))}
                      </div>
                    </div>
                  ) : (
                    <div className="admin-table-secondary">最近问诊未记录红旗线索。</div>
                  )}
                </div>
              ) : (
                <EmptyState title="暂无问诊摘要" description="患者完成 AI 问诊后，这里会显示结构化摘要。" />
              )}
            </div>
          </section>
        </AdminSectionCard>

        <AdminSectionCard title="时间线" actions={<span id="timeline" className="admin-table-secondary">汇总睡眠、作业、干预、报告和审计事件</span>}>
          <div className="admin-pill-row wrap">
            <Link href={`/patients/${userId}#timeline` as Route} className={`admin-chip ${timelineTypes.length === 0 ? "is-active" : ""}`}>全部</Link>
            {timelineTypeOptions.map((type) => {
              const href = `/patients/${userId}?types=${type}#timeline` as Route;
              const active = timelineTypes.includes(type);
              return (
                <Link key={type} href={href} className={`admin-chip ${active ? "is-active" : ""}`}>
                  {formatTimelineTypeLabel(type)}
                </Link>
              );
            })}
          </div>

          {timeline.events.length > 0 ? (
            <div className="admin-timeline">
              {timeline.events.slice(0, 40).map((event) => (
                <article key={event.id} className={`admin-timeline-item tone-${event.tone}`}>
                  <div className="admin-timeline-dot" />
                  <div className="admin-timeline-body">
                    <div className="admin-inline-row between">
                      <strong>{event.title}</strong>
                      <span className="admin-table-secondary">{formatDateTime(event.occurredAt)}</span>
                    </div>
                    <div className="admin-table-secondary">{formatTimelineTypeLabel(event.type)}</div>
                    <p>{event.description}</p>
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <EmptyState title="暂无时间线事件" description="当前筛选下没有可展示的事件。" />
          )}
        </AdminSectionCard>
      </div>
    </AdminShell>
  );
}
