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

  return (
    <AdminShell
      section="system"
      activePath="/system/jobs"
      title="作业监控"
      actions={
        <form action={triggerWorkerAction} className="admin-inline-form">
          <input className="admin-input small" name="limit" type="number" min={1} max={100} defaultValue={20} />
          <button className="admin-primary-button" type="submit">立即执行 worker</button>
        </form>
      }
    >
      <section className="admin-stat-grid four-up">
        <AdminStatCard label="排队中" value={String(data.counts.queued ?? 0)} tone="warning" />
        <AdminStatCard label="运行中" value={String(data.counts.running ?? 0)} tone="info" />
        <AdminStatCard label="成功" value={String(data.counts.succeeded ?? 0)} tone="success" />
        <AdminStatCard label="失败" value={String(data.counts.failed ?? 0)} tone="danger" />
      </section>

      <section className="admin-stat-grid two-up">
        <AdminStatCard
          label="平均耗时"
          value={formatScore(data.latency.avgMs, " ms")}
          detail={`样本数 ${data.latency.samples}`}
          tone="info"
        />
        <AdminStatCard
          label="P95 耗时"
          value={formatScore(data.latency.p95Ms, " ms")}
          detail="基于已完成作业统计"
          tone="warning"
        />
      </section>

      <section className="admin-grid two-up">
        <AdminSectionCard title="作业筛选与触发" description="筛选不同状态作业，并手动触发 worker。">
          {ran ? <p className="admin-form-success">本次共处理 {ran} 条作业。</p> : null}
          {error ? <p className="admin-form-error">{error}</p> : null}
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
              <button className="admin-primary-button" type="submit">应用筛选</button>
              <Link href={"/system/jobs" as Route} className="admin-secondary-button link-button">
                重置
              </Link>
            </div>
          </form>
        </AdminSectionCard>

        <AdminSectionCard title="最近失败摘要" description="优先排查失败作业和受影响患者。">
          {data.recentFailed.length === 0 ? (
            <EmptyState title="暂无失败作业" description="最近窗口内没有失败记录。" />
          ) : (
            <div className="admin-stack">
              {data.recentFailed.map((item) => (
                <div className="admin-alert-card danger" key={item.jobId}>
                  <div className="admin-table-primary">{item.jobId}</div>
                  <div className="admin-table-secondary">{item.userEmail ?? item.userId}</div>
                  <p>{item.errorMessage ?? "未记录错误信息"}</p>
                  <span>{formatDateTime(item.createdAt)}</span>
                </div>
              ))}
            </div>
          )}
        </AdminSectionCard>
      </section>

      <AdminSectionCard
        title="最近作业"
        description={`第 ${data.pagination.page} / ${data.pagination.totalPages} 页，共 ${data.pagination.total} 条记录。`}
      >
        {data.recentJobs.length === 0 ? (
          <EmptyState title="暂无作业" description="当前筛选条件下没有匹配作业。" />
        ) : (
          <div className="admin-table-wrap compact">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>作业</th>
                  <th>状态</th>
                  <th>创建时间</th>
                  <th>完成时间</th>
                </tr>
              </thead>
              <tbody>
                {data.recentJobs.map((job) => (
                  <tr key={job.jobId}>
                    <td>
                      <div className="admin-table-primary">{job.jobId}</div>
                      <div className="admin-table-secondary">{job.userEmail ?? job.userId}</div>
                      <div className="admin-table-tertiary">睡眠记录 {job.sleepRecordId}</div>
                    </td>
                    <td>
                      <AdminPill tone={toPillTone(job.status)}>{formatStatusLabel(job.status)}</AdminPill>
                      <div className="admin-table-secondary">{job.modelVersion ?? "-"}</div>
                    </td>
                    <td>{formatDateTime(job.createdAt)}</td>
                    <td>{formatDateTime(job.finishedAt)}</td>
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
