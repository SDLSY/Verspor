import type { Route } from "next";
import Link from "next/link";
import { AdminShell } from "@/components/admin-shell";
import { AdminPill, AdminSectionCard, AdminStatCard, EmptyState, formatDateTime } from "@/components/admin-ui";
import { requireAdminPage } from "@/lib/admin-auth";
import { formatAuditActionLabel, formatResourceTypeLabel } from "@/lib/admin-labels";
import { getAdminAudit } from "@/lib/admin-system";

type SearchParams = Record<string, string | string[] | undefined>;

function pickFirst(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

function pickNumber(value: string | string[] | undefined): number | undefined {
  const raw = pickFirst(value);
  if (!raw) {
    return undefined;
  }
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function buildHref(current: SearchParams, updates: Record<string, string | undefined>): Route {
  const params = new URLSearchParams();
  Object.entries(current).forEach(([key, value]) => {
    if (Array.isArray(value)) {
      value.forEach((entry) => {
        if (entry) params.append(key, entry);
      });
      return;
    }
    if (value) params.set(key, value);
  });

  Object.entries(updates).forEach(([key, value]) => {
    params.delete(key);
    if (value) params.set(key, value);
  });

  const query = params.toString();
  return (query ? `/system/audit?${query}` : "/system/audit") as Route;
}

export default async function AuditPage({
  searchParams,
}: {
  searchParams?: Promise<SearchParams>;
}) {
  await requireAdminPage();
  const resolved = (await searchParams) ?? {};
  const startValue = pickFirst(resolved.startAt);
  const endValue = pickFirst(resolved.endAt);
  const data = await getAdminAudit({
    page: pickNumber(resolved.page),
    pageSize: pickNumber(resolved.pageSize),
    actor: pickFirst(resolved.actor) || undefined,
    action: pickFirst(resolved.action) || undefined,
    resourceType: pickFirst(resolved.resourceType) || undefined,
    startAt: startValue ? Date.parse(startValue) : undefined,
    endAt: endValue ? Date.parse(endValue) : undefined,
  });

  const actorCount = new Set(data.items.map((item) => item.actor).filter(Boolean)).size;
  const actionCount = new Set(data.items.map((item) => item.action).filter(Boolean)).size;
  const resourceTypeCount = new Set(data.items.map((item) => item.resourceType).filter(Boolean)).size;

  return (
    <AdminShell section="system" activePath="/system/audit" title="审计记录">
      <section className="admin-stat-grid four-up">
        <AdminStatCard label="当前页记录数" value={String(data.items.length)} detail={`总量 ${data.pagination.total}`} tone="info" />
        <AdminStatCard label="操作人数" value={String(actorCount)} detail="基于当前筛选结果" tone="success" />
        <AdminStatCard label="动作类型" value={String(actionCount)} detail="用于观察运维集中度" tone="warning" />
        <AdminStatCard label="资源类型" value={String(resourceTypeCount)} detail="覆盖模型、任务、配置与认证" tone="info" />
      </section>

      <AdminSectionCard title="筛选条件" description="按操作者、动作、资源类型和时间窗口查看审计流水。">
        <form method="get" className="admin-filter-grid compact">
          <label className="admin-field">
            <span>操作者</span>
            <input className="admin-input" name="actor" defaultValue={data.filters.actor} />
          </label>
          <label className="admin-field">
            <span>动作</span>
            <input className="admin-input" name="action" defaultValue={data.filters.action} />
          </label>
          <label className="admin-field">
            <span>资源类型</span>
            <input className="admin-input" name="resourceType" defaultValue={data.filters.resourceType} />
          </label>
          <label className="admin-field">
            <span>开始时间</span>
            <input className="admin-input" name="startAt" type="datetime-local" defaultValue={startValue} />
          </label>
          <label className="admin-field">
            <span>结束时间</span>
            <input className="admin-input" name="endAt" type="datetime-local" defaultValue={endValue} />
          </label>
          <div className="admin-button-row left-align">
            <button className="admin-primary-button" type="submit">应用筛选</button>
            <Link href={"/system/audit" as Route} className="admin-secondary-button link-button">
              重置
            </Link>
          </div>
        </form>
      </AdminSectionCard>

      <AdminSectionCard
        title="审计流水"
        description={`第 ${data.pagination.page} / ${data.pagination.totalPages} 页，共 ${data.pagination.total} 条记录。`}
      >
        {data.items.length === 0 ? (
          <EmptyState title="暂无审计记录" description="当前筛选条件下没有匹配事件。" />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>操作者</th>
                  <th>动作</th>
                  <th>资源</th>
                  <th>时间</th>
                  <th>元数据</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <div className="admin-table-primary">{item.actor}</div>
                      <div className="admin-table-secondary">{item.userId ?? "系统"}</div>
                    </td>
                    <td>
                      <AdminPill tone="info">{formatAuditActionLabel(item.action)}</AdminPill>
                    </td>
                    <td>
                      <div className="admin-table-primary">{formatResourceTypeLabel(item.resourceType)}</div>
                      <div className="admin-table-secondary">{item.resourceId ?? "-"}</div>
                    </td>
                    <td>{formatDateTime(item.createdAt)}</td>
                    <td className="admin-cell-break">
                      <pre className="admin-code-block">{JSON.stringify(item.metadata, null, 2)}</pre>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <div className="admin-pagination">
          <Link
            href={buildHref(resolved, {
              page: data.pagination.page > 1 ? String(data.pagination.page - 1) : undefined,
            })}
            className={`admin-secondary-button link-button${data.pagination.page <= 1 ? " is-disabled" : ""}`}
          >
            上一页
          </Link>
          <span>
            第 {data.pagination.page} 页，共 {data.pagination.totalPages} 页
          </span>
          <Link
            href={buildHref(resolved, {
              page: data.pagination.page < data.pagination.totalPages ? String(data.pagination.page + 1) : undefined,
            })}
            className={`admin-secondary-button link-button${data.pagination.page >= data.pagination.totalPages ? " is-disabled" : ""}`}
          >
            下一页
          </Link>
        </div>
      </AdminSectionCard>
    </AdminShell>
  );
}
