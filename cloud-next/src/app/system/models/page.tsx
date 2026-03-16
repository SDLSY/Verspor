import { AdminShell } from "@/components/admin-shell";
import {
  AdminPill,
  AdminSectionCard,
  AdminStatCard,
  EmptyState,
  formatDateTime,
  formatHealthStatusLabel,
  formatRuntimeLabel,
  formatScore,
  toPillTone,
} from "@/components/admin-ui";
import { formatBooleanLabel } from "@/lib/admin-labels";
import { requireAdminPage } from "@/lib/admin-auth";
import { getAdminModels } from "@/lib/admin-system";
import { activateModelAction, registerModelAction } from "./actions";

type SearchParams = Record<string, string | string[] | undefined>;

function pickFirst(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

export default async function ModelsPage({
  searchParams,
}: {
  searchParams?: Promise<SearchParams>;
}) {
  await requireAdminPage();
  const resolved = (await searchParams) ?? {};
  const data = await getAdminModels();
  const activeModel = data.items.find((item) => item.isActive) ?? null;
  const error = pickFirst(resolved.error);
  const saved = pickFirst(resolved.saved) === "1";
  const activated = pickFirst(resolved.activated) === "1";
  const healthyCount = data.items.filter((item) => item.health.status === "ok").length;

  return (
    <AdminShell
      section="system"
      activePath="/system/models"
      title="高级运维 · 模型资产"
      subtitle="首屏只强调当前启用版本、健康状态和回退能力，高级配置下沉到后面。"
    >
      <section className="admin-stat-grid four-up">
        <AdminStatCard
          label="当前启用版本"
          value={activeModel?.version ?? "-"}
          detail={activeModel?.modelKind ?? "暂无启用模型"}
          tone="success"
        />
        <AdminStatCard
          label="运行时"
          value={activeModel ? formatRuntimeLabel(activeModel.runtimeType) : "-"}
          detail={activeModel?.inferenceEndpoint ?? activeModel?.artifactPath ?? "-"}
          tone="info"
        />
        <AdminStatCard
          label="可回退"
          value={activeModel ? formatBooleanLabel(activeModel.fallbackEnabled, "可回退", "关闭") : "-"}
          detail={`超时 ${activeModel?.inferenceTimeoutMs ?? 0} ms`}
          tone="warning"
        />
        <AdminStatCard
          label="健康检查正常"
          value={String(healthyCount)}
          detail={`总版本 ${data.items.length}`}
          tone="success"
        />
      </section>

      <section className="admin-grid two-up">
        <AdminSectionCard title="当前模型状态" description="先看现行版本是否稳定，再决定是否进入高级配置。">
          {activeModel ? (
            <div className="admin-stack">
              <div className="admin-alert-card">
                <div className="admin-button-row left-align">
                  <div className="admin-table-primary">{activeModel.version}</div>
                  <AdminPill tone={toPillTone(activeModel.health.status)}>
                    {formatHealthStatusLabel(activeModel.health.status)}
                  </AdminPill>
                </div>
                <p>
                  {formatRuntimeLabel(activeModel.runtimeType)} / 置信阈值{" "}
                  {formatScore(activeModel.confidenceThreshold ? activeModel.confidenceThreshold * 100 : 0, "%")}
                </p>
                <span>最近更新时间 {formatDateTime(activeModel.updatedAt)}</span>
              </div>
            </div>
          ) : (
            <EmptyState title="暂无启用模型" description="请先注册模型，再进行激活。" />
          )}
        </AdminSectionCard>

        <AdminSectionCard title="高级配置" description="注册、修改和切换模型都保留，但不放在后台首屏。">
          {saved ? <p className="admin-form-success">模型记录已保存。</p> : null}
          {activated ? <p className="admin-form-success">模型版本已激活。</p> : null}
          {error ? <p className="admin-form-error">{error}</p> : null}
          <details className="admin-disclosure-card">
            <summary>
              <span>打开模型注册与更新表单</span>
              <AdminPill tone="warning">高级操作</AdminPill>
            </summary>
            <div className="admin-disclosure-content">
              <form action={registerModelAction} className="admin-form-grid">
                <label className="admin-field">
                  <span>模型类型</span>
                  <input className="admin-input" name="modelKind" defaultValue="sleep-multimodal" />
                </label>
                <label className="admin-field">
                  <span>版本号</span>
                  <input className="admin-input" name="version" placeholder="mmt-v3-http" required />
                </label>
                <label className="admin-field">
                  <span>模型产物路径</span>
                  <input className="admin-input" name="artifactPath" placeholder="models/mmt-v3" />
                </label>
                <label className="admin-field">
                  <span>特征 Schema</span>
                  <input className="admin-input" name="featureSchemaVersion" defaultValue="v1" />
                </label>
                <label className="admin-field">
                  <span>运行时</span>
                  <select className="admin-select" name="runtimeType" defaultValue="http">
                    <option value="http">云端推理</option>
                    <option value="fallback">本地兜底</option>
                  </select>
                </label>
                <label className="admin-field">
                  <span>推理地址</span>
                  <input className="admin-input" name="inferenceEndpoint" placeholder="https://example.inference/" />
                </label>
                <label className="admin-field">
                  <span>置信阈值</span>
                  <input className="admin-input" name="confidenceThreshold" type="number" step="0.01" defaultValue="0.6" />
                </label>
                <label className="admin-field">
                  <span>超时（毫秒）</span>
                  <input className="admin-input" name="inferenceTimeoutMs" type="number" defaultValue="12000" />
                </label>
                <label className="admin-check">
                  <input name="fallbackEnabled" type="checkbox" defaultChecked />
                  <span>保留兜底能力</span>
                </label>
                <label className="admin-check">
                  <input name="activate" type="checkbox" />
                  <span>保存后立即激活</span>
                </label>
                <div className="admin-button-row left-align">
                  <button className="admin-primary-button" type="submit">
                    保存模型
                  </button>
                </div>
              </form>
            </div>
          </details>
        </AdminSectionCard>
      </section>

      <AdminSectionCard title="模型注册表" description="完整模型资产保留在后面，用于需要时排查与切换。">
        {data.items.length === 0 ? (
          <EmptyState title="暂无模型记录" description="请先注册模型，再进行激活。" />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>版本</th>
                  <th>运行时</th>
                  <th>推理入口</th>
                  <th>健康状态</th>
                  <th>更新时间</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((item) => (
                  <tr key={`${item.modelKind}:${item.version}`}>
                    <td>
                      <div className="admin-table-primary">{item.version}</div>
                      <div className="admin-table-secondary">{item.modelKind}</div>
                      {item.isActive ? <AdminPill tone="success">启用中</AdminPill> : null}
                    </td>
                    <td>
                      <div>{formatRuntimeLabel(item.runtimeType)}</div>
                      <div className="admin-table-secondary">
                        兜底：{formatBooleanLabel(item.fallbackEnabled, "开启", "关闭")}
                      </div>
                    </td>
                    <td className="admin-cell-break">{item.inferenceEndpoint ?? item.artifactPath ?? "-"}</td>
                    <td>
                      <AdminPill tone={toPillTone(item.health.status)}>{formatHealthStatusLabel(item.health.status)}</AdminPill>
                      <div className="admin-table-secondary">{item.health.detail ?? "-"}</div>
                    </td>
                    <td>{formatDateTime(item.updatedAt)}</td>
                    <td>
                      <form action={activateModelAction}>
                        <input type="hidden" name="modelKind" value={item.modelKind} />
                        <input type="hidden" name="version" value={item.version} />
                        <button className="admin-secondary-button" disabled={item.isActive} type="submit">
                          激活
                        </button>
                      </form>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </AdminSectionCard>
    </AdminShell>
  );
}
