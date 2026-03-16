import { AdminShell } from "@/components/admin-shell";
import { AdminSectionCard, AdminStatCard, EmptyState, formatScore } from "@/components/admin-ui";
import { requireAdminPage } from "@/lib/admin-auth";
import { getAdminRecommendationEffects } from "@/lib/admin-recommendations";

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

export default async function RecommendationEffectsPage({
  searchParams,
}: {
  searchParams?: Promise<SearchParams>;
}) {
  await requireAdminPage();
  const resolved = (await searchParams) ?? {};
  const data = await getAdminRecommendationEffects({
    days: pickNumber(resolved.days),
    profileCode: pickFirst(resolved.profileCode) || undefined,
    recommendationMode: pickFirst(resolved.recommendationMode) || undefined,
    userId: pickFirst(resolved.userId) || undefined,
  });
  const topMode = data.byRecommendationMode[0] ?? null;
  const topProfile = data.byModelProfile[0] ?? null;
  const topUser = data.byUser[0] ?? null;

  return (
    <AdminShell
      section="recommendations"
      activePath="/recommendations/effects"
      title="效果闭环"
      subtitle="用一段讲解摘要先说明最近哪种建议最有效，再展开统计表。"
    >
      <section className="admin-stat-grid four-up">
        <AdminStatCard label="数据来源" value={data.source} tone="info" />
        <AdminStatCard label="执行总数" value={String(data.totalExecutions)} tone="info" />
        <AdminStatCard
          label="归因率"
          value={formatScore(data.attributionRate * 100, "%")}
          detail={`已归因 ${data.attributedExecutions}`}
          tone="success"
        />
        <AdminStatCard
          label="平均效果"
          value={formatScore(data.avgEffectScore)}
          detail={`平均压力下降 ${formatScore(data.avgStressDrop)}`}
          tone="warning"
        />
      </section>

      <AdminSectionCard title="讲解摘要" description="用于答辩时先给结论，再展示后面的统计表。">
        {data.totalExecutions === 0 ? (
          <EmptyState title="暂无闭环效果数据" description="当前筛选窗口内没有可展示的执行结果。" />
        ) : (
          <div className="admin-grid three-up">
            <article className="admin-alert-card">
              <div className="admin-table-primary">当前最稳的建议模式</div>
              <p>{topMode ? `${topMode.recommendationMode}，平均效果 ${formatScore(topMode.avgEffectScore)}` : "暂无数据"}</p>
            </article>
            <article className="admin-alert-card">
              <div className="admin-table-primary">当前最强的策略配置</div>
              <p>{topProfile ? `${topProfile.profileCode} / ${topProfile.configSource}` : "暂无数据"}</p>
            </article>
            <article className="admin-alert-card">
              <div className="admin-table-primary">最值得讲的患者样本</div>
              <p>{topUser ? `${topUser.displayName}，执行 ${topUser.executionCount} 次` : "暂无数据"}</p>
            </article>
          </div>
        )}
      </AdminSectionCard>

      <AdminSectionCard title="筛选条件" description="按时间窗口、策略配置、建议模式和患者查看闭环效果。">
        <form method="get" className="admin-filter-grid compact">
          <label className="admin-field">
            <span>时间窗口（天）</span>
            <input className="admin-input" name="days" type="number" min={1} max={90} defaultValue={data.days} />
          </label>
          <label className="admin-field">
            <span>策略配置</span>
            <input className="admin-input" name="profileCode" defaultValue={pickFirst(resolved.profileCode)} />
          </label>
          <label className="admin-field">
            <span>建议模式</span>
            <input className="admin-input" name="recommendationMode" defaultValue={pickFirst(resolved.recommendationMode)} />
          </label>
          <label className="admin-field">
            <span>患者 ID</span>
            <input className="admin-input" name="userId" defaultValue={pickFirst(resolved.userId)} />
          </label>
          <div className="admin-button-row left-align">
            <button className="admin-primary-button" type="submit">应用筛选</button>
          </div>
        </form>
      </AdminSectionCard>

      <section className="admin-grid two-up">
        <AdminSectionCard title="按建议模式" description="观察不同模式的执行量和平均效果。">
          {data.byRecommendationMode.length === 0 ? (
            <EmptyState title="暂无归因数据" description="当前条件下没有可展示的闭环结果。" />
          ) : (
            <div className="admin-table-wrap compact">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>建议模式</th>
                    <th>执行数</th>
                    <th>平均效果</th>
                    <th>平均压力下降</th>
                  </tr>
                </thead>
                <tbody>
                  {data.byRecommendationMode.map((item) => (
                    <tr key={item.recommendationMode}>
                      <td>{item.recommendationMode}</td>
                      <td>{item.executionCount}</td>
                      <td>{formatScore(item.avgEffectScore)}</td>
                      <td>{formatScore(item.avgStressDrop)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </AdminSectionCard>

        <AdminSectionCard title="按策略配置" description="比较不同 profile 和配置来源的执行表现。">
          {data.byModelProfile.length === 0 ? (
            <EmptyState title="暂无策略效果数据" description="当前条件下没有 profile 维度的统计结果。" />
          ) : (
            <div className="admin-table-wrap compact">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>Profile</th>
                    <th>配置来源</th>
                    <th>执行数</th>
                    <th>平均效果</th>
                  </tr>
                </thead>
                <tbody>
                  {data.byModelProfile.map((item) => (
                    <tr key={`${item.profileCode}:${item.configSource}`}>
                      <td>{item.profileCode}</td>
                      <td>{item.configSource}</td>
                      <td>{item.executionCount}</td>
                      <td>{formatScore(item.avgEffectScore)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </AdminSectionCard>
      </section>
    </AdminShell>
  );
}
