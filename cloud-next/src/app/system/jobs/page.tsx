import type { Route } from "next";
import Link from "next/link";
import { AdminShell } from "@/components/admin-shell";
import {
  AdminPill,
  AdminSectionCard,
  AdminStatCard,
  EmptyState,
  formatDateTime,
  formatScore,
  formatStatusLabel,
  toPillTone,
} from "@/components/admin-ui";
import { requireAdminPage } from "@/lib/admin-auth";
import { getAdminJobs } from "@/lib/admin-system";
import { triggerWorkerAction } from "./actions";

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
  return (query ? `/system/jobs?${query}` : "/system/jobs") as Route;
}

export default async function JobsPage({
  searchParams,
}: {
  searchParams?: Promise<SearchParams>;
}) {
  await requireAdminPage();
  const resolved = (await searchParams) ?? {};
  const data = await getAdminJobs({
    page: pickNumber(resolved.page),
    pageSize: pickNumber(resolved.pageSize),
    status: pickFirst(resolved.status) || undefined,
  });
  const error = pickFirst(resolved.error);
  const ran = pickFirst(resolved.ran);
  const affectedUsers = new Set(data.recentFailed.map((item) => item.userId).filter(Boolean)).size;

  return (
    <AdminShell
      section="system"
      activePath="/system/jobs"
      title="高级运维 · 作业监控"
      subtitle="先回答失败作业是否影响当前演示和运营，再决定要不要手动触发 worker。"
    >
      <section className="admin-stat-grid four-up">
        <AdminStatCard label="排队中" value={String(data.counts.queued ?? 0)} tone="warning" />
        <AdminStatCard label="运行中" value={String(data.counts.running ?? 0)} tone="info" />
        <AdminStatCard
          label="失败"
          value={String(data.counts.failed ?? 0)}
          detail={`影响 ${affectedUsers} 位患者`}
          tone="danger"
        />
        <AdminStatCard
          label="P95 耗时"
          value={formatScore(data.latency.p95Ms, " ms")}
          detail={`样本 ${data.latency.samples}`}
          tone="warning"
        />
      </section>

      <section className="admin-grid two-up">
        <AdminSectionCard title="当前影响摘要" description="先看失败是否真的影响当前演示，再决定是否进入高级操作。">
          {data.recentFailed.length === 0 ? (
            <EmptyState title="近期无失败作业" description="当前没有需要人工介入的失败记录。" />
          ) : (
            <div className="admin-stack">
              {data.recentFailed.map((item) => (
                <div className="admin-alert-card danger" key={item.jobId}>
                  <div className="admin-table-primary">{item.userEmail ?? item.userId}</div>
                  <p>{item.errorMessage ?? "未记录错误信息"}</p>
                  <span>{formatDateTime(item.createdAt)}</span>
                </div>
              ))}
            </div>
          )}
        </AdminSectionCard>

        <AdminSectionCard title="高级操作" description="手动触发 worker 会改动远端状态，默认收进风险操作区。">
          {ran ? <p className="admin-form-success">本次共处理 {ran} 条作业。</p> : null}
          {error ? <p className="admin-form-error">{error}</p> : null}
          <form action={triggerWorkerAction} className="admin-form-stack">
            <label className="admin-field">
              <span>处理上限</span>
              <input className="admin-input small" name="limit" type="number" min={1} max={100} defaultValue={20} />
            </label>
            <label className="admin-check">
              <input name="confirmRisk" type="checkbox" value="1" />
              <span>我确认这是一次高级运维操作，允许手动触发 worker</span>
            </label>
            <div className="admin-button-row left-align">
              <button className="admin-primary-button" type="submit">
                立即执行 worker
              </button>
              <Link href={"/system/models" as Route} className="admin-secondary-button link-button">
                查看模型状态
              </Link>
            </div>
          </form>
        </AdminSectionCard>
      </section>

      <AdminSectionCard title="筛选与最近作业" description="完整作业列表保留，但放在影响摘要之后。">
        <form method="get" className="admin-filter-grid compact">
          <label className="admin-field">
            <span>作业状态</span>
            <select className="admin-select" name="status" defaultValue={data.filters.status}>
              <option value="ALL">全部</option>
              <option value="QUEUED">排队中</option>
              <option value="RUNNING">运行中</option>
              <option value="SUCCEEDED">成功</option>
              <option value="FAILED">失败</option>
            </select>
          </label>
          <div className="admin-button-row left-align">
            <button className="admin-primary-button" type="submit">
              应用筛选
            </button>
            <Link href={"/system/jobs" as Route} className="admin-secondary-button link-button">
              重置
            </Link>
          </div>
        </form>

        {data.recentJobs.length === 0 ? (
          <EmptyState title="暂无作业" description="当前筛选条件下没有匹配作业。" />
        ) : (
          <>
            <div className="admin-table-wrap compact">
              <table className="admin-table">
                <thead>
                  <tr>
                    <th>患者</th>
                    <th>作业</th>
                    <th>状态</th>
                    <th>时间</th>
                  </tr>
                </thead>
                <tbody>
                  {data.recentJobs.map((job) => (
                    <tr key={job.jobId}>
                      <td>
                        <div className="admin-table-primary">{job.userEmail ?? job.userId}</div>
                        <div className="admin-table-secondary">睡眠记录 {job.sleepRecordId}</div>
                      </td>
                      <td>
                        <div className="admin-stack compact">
                          <span>{job.jobId}</span>
                          <span className="admin-table-secondary">{job.modelVersion ?? "-"}</span>
                        </div>
                      </td>
                      <td>
                        <AdminPill tone={toPillTone(job.status)}>{formatStatusLabel(job.status)}</AdminPill>
                      </td>
                      <td>
                        <div className="admin-stack compact">
                          <span>{formatDateTime(job.createdAt)}</span>
                          <span className="admin-table-secondary">{formatDateTime(job.finishedAt)}</span>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
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
                className={`admin-secondary-button link-button${
                  data.pagination.page >= data.pagination.totalPages ? " is-disabled" : ""
                }`}
              >
                下一页
              </Link>
            </div>
          </>
        )}
      </AdminSectionCard>
    </AdminShell>
  );
}
