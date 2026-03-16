import type { Route } from "next";
import Link from "next/link";
import { AdminShell } from "@/components/admin-shell";
import {
  AdminPill,
  AdminSectionCard,
  AdminStatCard,
  EmptyState,
  formatDateTime,
  formatRiskLabel,
  formatScore,
  toPillTone,
} from "@/components/admin-ui";
import { requireAdminPage } from "@/lib/admin-auth";
import { listAdminRecommendationTraces } from "@/lib/admin-recommendations";

type SearchParams = Record<string, string | string[] | undefined>;

function pickFirst(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

function pickNumber(value: string | string[] | undefined): number | undefined {
  const raw = pickFirst(value);
  if (!raw) return undefined;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function renderExecutionSummary(
  executionCount: number,
  avgEffectScore: number | null | undefined,
  avgStressDrop: number | null | undefined
): string {
  if (executionCount <= 0) {
    return "尚无执行回写，适合在患者工作台现场补一次演示动作。";
  }
  return `执行 ${executionCount} 次 / 平均效果 ${formatScore(avgEffectScore)} / 平均压力下降 ${formatScore(avgStressDrop)}`;
}

export default async function RecommendationsPage({
  searchParams,
}: {
  searchParams?: Promise<SearchParams>;
}) {
  await requireAdminPage();
  const resolved = (await searchParams) ?? {};
  const data = await listAdminRecommendationTraces({
    page: pickNumber(resolved.page),
    q: pickFirst(resolved.q) || undefined,
    userId: pickFirst(resolved.userId) || undefined,
    traceType: pickFirst(resolved.traceType) || undefined,
    recommendationMode: pickFirst(resolved.recommendationMode) || undefined,
    configSource: pickFirst(resolved.configSource) || undefined,
    days: pickNumber(resolved.days),
  });

  const topStory = data.items[0] ?? null;

  return (
    <AdminShell
      section="recommendations"
      activePath="/recommendations"
      title="建议与效果"
      subtitle="先看建议内容、执行结果和效果，再按需展开 trace、provider 和 profile 等技术细节。"
      actions={
        <div className="admin-button-row">
          <Link href={"/recommendations/effects" as Route} className="admin-primary-button link-button">
            打开效果闭环
          </Link>
          <Link href={"/recommendations/profiles" as Route} className="admin-secondary-button link-button">
            策略配置
          </Link>
        </div>
      }
    >
      <section className="admin-stat-grid four-up">
        <AdminStatCard label="建议总数" value={String(data.summary.totalTraces)} tone="info" />
        <AdminStatCard label="高风险建议" value={String(data.summary.highRiskTraces)} tone="danger" />
        <AdminStatCard label="Fallback 建议" value={String(data.summary.fallbackTraces)} tone="warning" />
        <AdminStatCard
          label="主导模式"
          value={data.summary.topRecommendationMode ?? "-"}
          detail={`配置来源 ${data.summary.configSources.join(" / ") || "default"}`}
          tone="success"
        />
      </section>

      <section className="admin-grid two-up">
        <AdminSectionCard title="当前最适合讲的一条建议" description="优先讲一条完整闭环，不先讲 trace。">
          {topStory ? (
            <article className="admin-alert-card">
              <div className="admin-button-row left-align">
                {topStory.scenarioLabel ? <AdminPill tone="info">{topStory.scenarioLabel}</AdminPill> : null}
                <AdminPill tone={toPillTone(topStory.riskLevel, "risk")}>
                  {formatRiskLabel(topStory.riskLevel)}
                </AdminPill>
                {topStory.recommendationMode ? <AdminPill tone="info">{topStory.recommendationMode}</AdminPill> : null}
              </div>
              <p className="admin-table-primary">{topStory.summary}</p>
              <p>{topStory.reasons.join("；") || "本次建议由系统综合当前证据生成。"}</p>
              <span>{renderExecutionSummary(topStory.executionCount, topStory.avgEffectScore, topStory.avgStressDrop)}</span>
            </article>
          ) : (
            <EmptyState title="暂无建议闭环" description="当前筛选条件下没有可以讲解的建议记录。" />
          )}
        </AdminSectionCard>

        <AdminSectionCard title="筛选条件" description="保留搜索，但主要用枚举筛选，不靠自由输入猜字段。">
          <form method="get" className="admin-filter-grid compact">
            <label className="admin-field">
              <span>关键字</span>
              <input className="admin-input" name="q" defaultValue={data.filters.q} placeholder="患者、摘要、场景" />
            </label>
            <label className="admin-field">
              <span>轨迹类型</span>
              <select className="admin-select" name="traceType" defaultValue={data.filters.traceType}>
                <option value="">全部</option>
                {data.facets.traceTypes.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>
            <label className="admin-field">
              <span>建议模式</span>
              <select
                className="admin-select"
                name="recommendationMode"
                defaultValue={data.filters.recommendationMode}
              >
                <option value="">全部</option>
                {data.facets.recommendationModes.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>
            <label className="admin-field">
              <span>配置来源</span>
              <select className="admin-select" name="configSource" defaultValue={data.filters.configSource}>
                <option value="">全部</option>
                {data.facets.configSources.map((item) => (
                  <option key={item} value={item}>
                    {item}
                  </option>
                ))}
              </select>
            </label>
            <label className="admin-field">
              <span>患者 ID</span>
              <input className="admin-input" name="userId" defaultValue={data.filters.userId} />
            </label>
            <label className="admin-field">
              <span>时间窗口（天）</span>
              <input className="admin-input" name="days" type="number" min={1} max={90} defaultValue={data.filters.days} />
            </label>
            <div className="admin-button-row left-align">
              <button className="admin-primary-button" type="submit">
                应用筛选
              </button>
              <Link href={"/recommendations" as Route} className="admin-secondary-button link-button">
                重置
              </Link>
            </div>
          </form>
        </AdminSectionCard>
      </section>

      <AdminSectionCard title="建议闭环列表" description="主区只讲建议内容、原因和效果，技术元信息折叠起来。">
        {data.items.length === 0 ? (
          <EmptyState title="没有匹配的建议闭环" description="请调整筛选条件后重试。" />
        ) : (
          <div className="admin-stack">
            {data.items.map((item) => (
              <article key={item.id} className="admin-alert-card">
                <div className="admin-button-row left-align">
                  {item.scenarioLabel ? <AdminPill tone="info">{item.scenarioLabel}</AdminPill> : null}
                  {item.riskLevel ? (
                    <AdminPill tone={toPillTone(item.riskLevel, "risk")}>{formatRiskLabel(item.riskLevel)}</AdminPill>
                  ) : null}
                  {item.recommendationMode ? <AdminPill tone="info">{item.recommendationMode}</AdminPill> : null}
                </div>
                <div className="admin-story-card-top compact">
                  <div>
                    <div className="admin-table-primary">{item.summary}</div>
                    <div className="admin-table-secondary">
                      {item.displayName} / {item.email || item.userId}
                    </div>
                  </div>
                  <span className="admin-table-secondary">{formatDateTime(item.createdAt)}</span>
                </div>
                {item.reasons.length > 0 ? (
                  <ul className="admin-bullet-list">
                    {item.reasons.map((reason) => (
                      <li key={reason}>{reason}</li>
                    ))}
                  </ul>
                ) : null}
                {item.nextStep ? <p>下一步：{item.nextStep}</p> : null}
                <div className="admin-story-metrics">
                  {item.executionCount > 0 ? (
                    <>
                      <span>执行 {item.executionCount} 次</span>
                      <span>平均效果 {formatScore(item.avgEffectScore)}</span>
                      <span>平均压力下降 {formatScore(item.avgStressDrop)}</span>
                    </>
                  ) : (
                    <span>尚无执行回写，建议到患者工作台补一次干预动作。</span>
                  )}
                </div>
                <details className="admin-disclosure-card">
                  <summary>
                    <span>技术详情</span>
                    <AdminPill tone={item.isFallback ? "warning" : "success"}>
                      {item.isFallback ? "Fallback" : "Primary"}
                    </AdminPill>
                  </summary>
                  <div className="admin-disclosure-content">
                    <div className="admin-stack compact">
                      <span>traceId：{item.traceId ?? item.id}</span>
                      <span>provider：{item.providerId ?? "-"}</span>
                      <span>profile：{item.profileCode ?? item.modelVersion ?? "-"}</span>
                      <span>source：{item.configSource ?? "-"}</span>
                    </div>
                  </div>
                </details>
              </article>
            ))}
          </div>
        )}
      </AdminSectionCard>
    </AdminShell>
  );
}
