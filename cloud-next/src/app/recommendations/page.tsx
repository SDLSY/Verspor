import type { Route } from "next";
import Link from "next/link";
import { AdminShell } from "@/components/admin-shell";
import {
  AdminPill,
  AdminSectionCard,
  AdminStatCard,
  EmptyState,
  formatDateTime,
  toPillTone,
} from "@/components/admin-ui";
import { requireAdminPage } from "@/lib/admin-auth";
import { formatRiskLabel } from "@/lib/admin-labels";
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

  return (
    <AdminShell
      section="recommendations"
      activePath="/recommendations"
      title="建议轨迹"
      actions={
        <div className="admin-button-row">
          <Link href={"/recommendations/profiles" as Route} className="admin-secondary-button link-button">
            策略配置
          </Link>
          <Link href={"/recommendations/effects" as Route} className="admin-primary-button link-button">
            效果闭环
          </Link>
        </div>
      }
    >
      <section className="admin-stat-grid four-up">
        <AdminStatCard label="轨迹总数" value={String(data.summary.totalTraces)} tone="info" />
        <AdminStatCard label="高风险轨迹" value={String(data.summary.highRiskTraces)} tone="danger" />
        <AdminStatCard label="Fallback 轨迹" value={String(data.summary.fallbackTraces)} tone="warning" />
        <AdminStatCard
          label="主导模式"
          value={data.summary.topRecommendationMode ?? "-"}
          detail={`配置来源 ${data.summary.configSources.join(" / ") || "default"}`}
          tone="success"
        />
      </section>

      <AdminSectionCard title="筛选条件" description="按患者、模式、配置来源和时间窗口筛选建议轨迹。">
        <form method="get" className="admin-filter-grid compact">
          <label className="admin-field">
            <span>关键字</span>
            <input className="admin-input" name="q" defaultValue={data.filters.q} placeholder="患者、traceId、provider" />
          </label>
          <label className="admin-field">
            <span>患者 ID</span>
            <input className="admin-input" name="userId" defaultValue={data.filters.userId} />
          </label>
          <label className="admin-field">
            <span>轨迹类型</span>
            <input className="admin-input" name="traceType" defaultValue={data.filters.traceType} placeholder="DAILY_PRESCRIPTION" />
          </label>
          <label className="admin-field">
            <span>建议模式</span>
            <input className="admin-input" name="recommendationMode" defaultValue={data.filters.recommendationMode} placeholder="SLEEP_PREP" />
          </label>
          <label className="admin-field">
            <span>配置来源</span>
            <input className="admin-input" name="configSource" defaultValue={data.filters.configSource} placeholder="database" />
          </label>
          <label className="admin-field">
            <span>时间窗口（天）</span>
            <input className="admin-input" name="days" type="number" min={1} max={90} defaultValue={data.filters.days} />
          </label>
          <div className="admin-button-row left-align">
            <button className="admin-primary-button" type="submit">应用筛选</button>
            <Link href={"/recommendations" as Route} className="admin-secondary-button link-button">
              重置
            </Link>
          </div>
        </form>
      </AdminSectionCard>

      <AdminSectionCard title="最近建议轨迹" description="直接查看建议摘要、解释原因、traceId 和生效配置。">
        {data.items.length === 0 ? (
          <EmptyState title="没有匹配的建议轨迹" description="请调整筛选条件后重试。" />
        ) : (
          <div className="admin-stack">
            {data.items.map((item) => (
              <article key={item.id} className="admin-alert-card">
                <div className="admin-button-row left-align">
                  <div className="admin-table-primary">{item.summary}</div>
                  {item.riskLevel ? (
                    <AdminPill tone={toPillTone(item.riskLevel, "risk")}>{formatRiskLabel(item.riskLevel)}</AdminPill>
                  ) : null}
                  {item.recommendationMode ? <AdminPill tone="info">{item.recommendationMode}</AdminPill> : null}
                  {item.isFallback ? <AdminPill tone="warning">Fallback</AdminPill> : null}
                </div>
                <p>
                  {item.displayName} / {item.email || item.userId} / {item.traceType}
                </p>
                <p>
                  traceId：{item.traceId ?? "-"} / provider：{item.providerId ?? "-"} / profile：{item.profileCode ?? "-"} / source：{item.configSource ?? "-"}
                </p>
                {item.reasons.length > 0 ? (
                  <ul className="admin-bullet-list">
                    {item.reasons.map((reason) => (
                      <li key={reason}>{reason}</li>
                    ))}
                  </ul>
                ) : null}
                {item.nextStep ? <p>下一步：{item.nextStep}</p> : null}
                <span>{formatDateTime(item.createdAt)}</span>
              </article>
            ))}
          </div>
        )}
      </AdminSectionCard>
    </AdminShell>
  );
}
