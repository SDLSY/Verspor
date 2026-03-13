import { AdminShell } from "@/components/admin-shell";
import { AdminPill, AdminSectionCard, AdminStatCard, EmptyState, formatDateTime } from "@/components/admin-ui";
import { requireAdminPage } from "@/lib/admin-auth";
import {
  listRecommendationModelProfiles,
  listRecommendationProfileVersions,
} from "@/lib/recommendation-model/admin";
import { SRM_V2_DEFAULT_PROFILE, SRM_V2_MODEL_CODE } from "@/lib/recommendation-model/srm-v2-config";
import {
  cloneRecommendationProfileAction,
  rollbackRecommendationProfileVersionAction,
  saveRecommendationProfileAction,
} from "./actions";
import { RecommendationProfileEditor } from "./profile-editor";

type SearchParams = Record<string, string | string[] | undefined>;

function pickFirst(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

export default async function RecommendationProfilesPage({
  searchParams,
}: {
  searchParams?: Promise<SearchParams>;
}) {
  await requireAdminPage();
  const resolved = (await searchParams) ?? {};
  const modelCode = pickFirst(resolved.modelCode) || SRM_V2_MODEL_CODE;
  const profileCode = pickFirst(resolved.profileCode) || undefined;
  const status = pickFirst(resolved.status) || undefined;
  const error = pickFirst(resolved.error);
  const saved = pickFirst(resolved.saved) === "1";
  const cloned = pickFirst(resolved.cloned) === "1";
  const rolledBack = pickFirst(resolved.rolledBack) === "1";

  const profiles = await listRecommendationModelProfiles({
    modelCode,
    profileCode,
    status: status ? (status as "draft" | "active" | "archived") : undefined,
  });
  const versionsByProfile = new Map(
    await Promise.all(
      profiles.map(
        async (profile): Promise<[string, Awaited<ReturnType<typeof listRecommendationProfileVersions>>]> => [
          profile.profileCode,
          await listRecommendationProfileVersions(profile.modelCode, profile.profileCode),
        ]
      )
    )
  );
  const activeProfile = profiles.find((item) => item.status === "active") ?? null;
  const draftCount = profiles.filter((item) => item.status === "draft").length;
  const archivedCount = profiles.filter((item) => item.status === "archived").length;

  return (
    <AdminShell
      section="recommendations"
      activePath="/recommendations/profiles"
      title="策略配置中心"
      subtitle="以可视化方式管理 SRM_V2 的阈值、权重、安全门控和模式优先级。"
    >
      <section className="admin-stat-grid four-up">
        <AdminStatCard label="配置总数" value={String(profiles.length)} tone="info" />
        <AdminStatCard
          label="当前激活配置"
          value={activeProfile?.profileCode ?? "-"}
          detail={activeProfile ? `更新于 ${formatDateTime(Date.parse(activeProfile.updatedAt))}` : "尚未配置"}
          tone="success"
        />
        <AdminStatCard label="草稿配置" value={String(draftCount)} detail={`归档 ${archivedCount}`} tone="warning" />
        <AdminStatCard label="模型代码" value={SRM_V2_MODEL_CODE} detail="数据库配置优先，默认配置作为回退" tone="info" />
      </section>

      <AdminSectionCard title="筛选与状态" description="筛选现有 profile，并查看保存、复制、回滚或校验错误结果。">
        {saved ? <p className="admin-form-success">策略配置已保存。</p> : null}
        {cloned ? <p className="admin-form-success">策略配置已复制为新草稿。</p> : null}
        {rolledBack ? <p className="admin-form-success">已回滚到所选历史版本。</p> : null}
        {error ? <p className="admin-form-error">{error}</p> : null}
        <form method="get" className="admin-filter-grid compact">
          <label className="admin-field">
            <span>模型代码</span>
            <input className="admin-input" name="modelCode" defaultValue={modelCode} />
          </label>
          <label className="admin-field">
            <span>Profile Code</span>
            <input className="admin-input" name="profileCode" defaultValue={profileCode} />
          </label>
          <label className="admin-field">
            <span>状态</span>
            <select className="admin-select" name="status" defaultValue={status}>
              <option value="">全部</option>
              <option value="draft">草稿</option>
              <option value="active">激活</option>
              <option value="archived">归档</option>
            </select>
          </label>
          <div className="admin-button-row left-align">
            <button className="admin-primary-button" type="submit">应用筛选</button>
          </div>
        </form>
      </AdminSectionCard>

      <section className="admin-grid two-up">
        <AdminSectionCard
          title="新建可视化配置"
          description="通过分组卡片直接编辑策略，而不是手工维护整块 JSON。"
        >
          <form action={saveRecommendationProfileAction} className="admin-form-stack">
            <RecommendationProfileEditor
              modelCode={SRM_V2_MODEL_CODE}
              initialProfileCode=""
              initialStatus="draft"
              initialDescription="新的 SRM_V2 草稿配置"
              initialThresholds={SRM_V2_DEFAULT_PROFILE.thresholds}
              initialWeights={SRM_V2_DEFAULT_PROFILE.weights}
              initialGateRules={SRM_V2_DEFAULT_PROFILE.gateRules}
              initialModePriorities={SRM_V2_DEFAULT_PROFILE.modePriorities}
              initialConfidenceFormula={SRM_V2_DEFAULT_PROFILE.confidenceFormula}
              submitLabel="保存配置"
            />
          </form>
        </AdminSectionCard>

        <AdminSectionCard
          title="复制现有配置"
          description="先从已运行的 profile 复制，再做小范围实验，避免直接改动线上配置。"
        >
          {profiles.length === 0 ? (
            <EmptyState title="暂无可复制的配置" description="请先创建一个基础 profile。" />
          ) : (
            <form action={cloneRecommendationProfileAction} className="admin-form-stack">
              <input type="hidden" name="modelCode" value={SRM_V2_MODEL_CODE} />
              <label className="admin-field">
                <span>源 Profile</span>
                <select className="admin-select" name="sourceProfileCode" defaultValue={activeProfile?.profileCode ?? profiles[0].profileCode}>
                  {profiles.map((item) => (
                    <option key={item.id} value={item.profileCode}>
                      {item.profileCode} / {item.status}
                    </option>
                  ))}
                </select>
              </label>
              <label className="admin-field">
                <span>新 Profile Code</span>
                <input className="admin-input" name="newProfileCode" placeholder="adult_cn_experiment_b" required />
              </label>
              <div className="admin-button-row left-align">
                <button className="admin-secondary-button" type="submit">复制为草稿</button>
              </div>
            </form>
          )}
        </AdminSectionCard>
      </section>

      <AdminSectionCard title="现有配置列表" description="每个 profile 都可展开为可视化编辑器，并查看版本历史与回滚入口。">
        {profiles.length === 0 ? (
          <EmptyState title="暂无配置" description="当前筛选条件下没有可显示的 profile。" />
        ) : (
          <div className="admin-stack">
            {profiles.map((profile) => {
              const versions = versionsByProfile.get(profile.profileCode) ?? [];
              return (
                <details key={profile.id} className="admin-disclosure-card admin-profile-card">
                  <summary>
                    <div>
                      <div className="admin-table-primary">{profile.profileCode}</div>
                      <div className="admin-table-secondary">
                        {profile.modelCode} / 更新于 {formatDateTime(Date.parse(profile.updatedAt))}
                      </div>
                    </div>
                    <div className="admin-page-actions">
                      <AdminPill tone={profile.status === "active" ? "success" : profile.status === "draft" ? "warning" : "neutral"}>
                        {profile.status}
                      </AdminPill>
                      <AdminPill tone={profile.source === "database" ? "info" : "neutral"}>{profile.source}</AdminPill>
                    </div>
                  </summary>
                  <div className="admin-disclosure-content">
                    <form action={saveRecommendationProfileAction} className="admin-form-stack">
                      <RecommendationProfileEditor
                        modelCode={profile.modelCode}
                        initialProfileCode={profile.profileCode}
                        initialStatus={profile.status}
                        initialDescription={profile.description ?? ""}
                        initialThresholds={profile.thresholds}
                        initialWeights={profile.weights}
                        initialGateRules={profile.gateRules}
                        initialModePriorities={profile.modePriorities}
                        initialConfidenceFormula={profile.confidenceFormula}
                        submitLabel="保存修改"
                        lockedProfileCode
                      />
                    </form>

                    <div className="admin-profile-version-block">
                      <div className="admin-editor-panel-header">
                        <div>
                          <h4>版本历史</h4>
                          <p>记录最近的策略快照，用于回溯改动与一键回滚。</p>
                        </div>
                      </div>
                      {versions.length === 0 ? (
                        <EmptyState title="暂无版本记录" description="保存、复制或回滚后，这里会显示版本快照。" />
                      ) : (
                        <div className="admin-version-stack">
                          {versions.map((version) => (
                            <article key={version.id} className="admin-version-card">
                              <div className="admin-version-card-main">
                                <div>
                                  <div className="admin-table-primary">{formatDateTime(Date.parse(version.createdAt))}</div>
                                  <div className="admin-table-secondary">{version.actor} / {version.action}</div>
                                </div>
                                <div className="admin-page-actions">
                                  {version.snapshot ? (
                                    <AdminPill
                                      tone={
                                        version.snapshot.status === "active"
                                          ? "success"
                                          : version.snapshot.status === "draft"
                                            ? "warning"
                                            : "neutral"
                                      }
                                    >
                                      {version.snapshot.status}
                                    </AdminPill>
                                  ) : null}
                                  {version.sourceProfileCode ? <AdminPill tone="info">来源 {version.sourceProfileCode}</AdminPill> : null}
                                </div>
                              </div>
                              <div className="admin-version-metrics">
                                <span>描述：{version.snapshot?.description || "未填写"}</span>
                                <span>阈值 {Object.keys(version.snapshot?.thresholds ?? {}).length} 项</span>
                                <span>权重 {Object.keys(version.snapshot?.weights ?? {}).length} 项</span>
                              </div>
                              {version.note ? <p className="admin-table-secondary">备注：{version.note}</p> : null}
                              <form action={rollbackRecommendationProfileVersionAction}>
                                <input type="hidden" name="versionId" value={version.id} />
                                <div className="admin-button-row left-align">
                                  <button className="admin-secondary-button" type="submit">回滚到该版本</button>
                                </div>
                              </form>
                            </article>
                          ))}
                        </div>
                      )}
                    </div>
                  </div>
                </details>
              );
            })}
          </div>
        )}
      </AdminSectionCard>
    </AdminShell>
  );
}
