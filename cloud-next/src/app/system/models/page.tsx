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
    <AdminShell section="system" activePath="/system/models" title="模型资产">
      <section className="admin-stat-grid four-up">
        <AdminStatCard label="模型版本总数" value={String(data.items.length)} detail="来自 model_registry" tone="info" />
        <AdminStatCard
          label="当前启用版本"
          value={activeModel?.version ?? "-"}
          detail={activeModel ? formatRuntimeLabel(activeModel.runtimeType) : "暂无启用模型"}
          tone="success"
        />
        <AdminStatCard
          label="兜底开关"
          value={activeModel ? formatBooleanLabel(activeModel.fallbackEnabled, "开启", "关闭") : "-"}
          detail={`超时 ${activeModel?.inferenceTimeoutMs ?? 0} ms`}
          tone="warning"
        />
        <AdminStatCard label="健康检查正常" value={String(healthyCount)} detail="基于 /health 可达性" tone="success" />
      </section>

      <section className="admin-grid two-up">
        <AdminSectionCard title="注册或更新模型" description="新增模型记录，或以相同版本号覆盖更新运行参数。">
          {saved ? <p className="admin-form-success">模型记录已保存。</p> : null}
          {activated ? <p className="admin-form-success">模型版本已激活。</p> : null}
          {error ? <p className="admin-form-error">{error}</p> : null}
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
              <button className="admin-primary-button" type="submit">保存模型</button>
            </div>
          </form>
        </AdminSectionCard>

        <AdminSectionCard title="模型注册表" description="查看当前所有版本、健康状态和激活入口。">
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
                    <th>阈值</th>
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
                      <td>{formatScore(item.confidenceThreshold ? item.confidenceThreshold * 100 : 0, "%")}</td>
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
      </section>
    </AdminShell>
  );
}
