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
  formatRiskLabel,
  formatScore,
  formatStatusLabel,
  toPillTone,
} from "@/components/admin-ui";
import { requireAdminPage } from "@/lib/admin-auth";
import { listAdminPatients } from "@/lib/admin-patients";

type SearchParams = Record<string, string | string[] | undefined>;

function pickFirst(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

function pickBoolean(value: string | string[] | undefined): boolean {
  const current = pickFirst(value);
  return current === "1" || current === "true";
}

function pickNumber(value: string | string[] | undefined): number | undefined {
  const current = pickFirst(value);
  if (!current) {
    return undefined;
  }
  const parsed = Number(current);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function buildHref(current: SearchParams, updates: Record<string, string | undefined>): Route {
  const params = new URLSearchParams();
  Object.entries(current).forEach(([key, value]) => {
    if (Array.isArray(value)) {
      value.forEach((entry) => {
        if (entry) {
          params.append(key, entry);
        }
      });
      return;
    }
    if (value) {
      params.set(key, value);
    }
  });

  Object.entries(updates).forEach(([key, value]) => {
    params.delete(key);
    if (value) {
      params.set(key, value);
    }
  });

  const query = params.toString();
  return (query ? `/patients?${query}` : "/patients") as Route;
}

export default async function PatientsPage({
  searchParams,
}: {
  searchParams?: Promise<SearchParams>;
}) {
  await requireAdminPage();
  const resolved = (await searchParams) ?? {};
  const data = await listAdminPatients({
    page: pickNumber(resolved.page) ?? 1,
    pageSize: pickNumber(resolved.pageSize),
    q: pickFirst(resolved.q) || undefined,
    riskLevel: pickFirst(resolved.riskLevel) || undefined,
    pendingOnly: pickBoolean(resolved.pendingOnly),
    failedOnly: pickBoolean(resolved.failedOnly),
    recentSleepOnly: pickBoolean(resolved.recentSleepOnly),
    recentSleepDays: pickNumber(resolved.recentSleepDays),
  });

  const priorityPatients = data.items.filter((item) => item.latestRiskLevel === "HIGH").slice(0, 5);
  const recentActivityPatients = data.items.slice(0, 8);
  const reminders = [
    data.summary.pendingInterventions > 0
      ? `有 ${data.summary.pendingInterventions} 条待处理干预任务需要安排执行或回访。`
      : null,
    data.summary.staleSleepReports > 0
      ? `有 ${data.summary.staleSleepReports} 名患者最近有睡眠记录但缺少最新夜间报告。`
      : null,
    data.summary.failedJobPatients > 0
      ? `有 ${data.summary.failedJobPatients} 名患者最近受到失败作业影响，建议优先排查。`
      : null,
  ].filter(Boolean) as string[];

  return (
    <AdminShell
      section="patients"
      activePath="/patients"
      title="患者运营工作台"
      subtitle="围绕高风险、待处理干预、报告状态和最近建议异常组织患者运营主线。"
      actions={
        <Link href={"/recommendations" as Route} className="admin-secondary-button link-button">
          查看建议轨迹
        </Link>
      }
    >
      <section className="admin-stat-grid four-up">
        <AdminStatCard
          label="高风险患者"
          value={String(data.summary.highRiskPatients)}
          detail="基于恢复分、异常分和医疗风险的综合判断"
          tone="danger"
        />
        <AdminStatCard
          label="待处理干预"
          value={String(data.summary.pendingInterventions)}
          detail="待执行或待人工处理的任务总量"
          tone="warning"
        />
        <AdminStatCard
          label="待刷新夜间报告"
          value={String(data.summary.staleSleepReports)}
          detail="最近有睡眠记录但未生成最新报告"
          tone="info"
        />
        <AdminStatCard
          label="受失败作业影响"
          value={String(data.summary.failedJobPatients)}
          detail="近 24 小时内受推理失败影响的患者数"
          tone="danger"
        />
      </section>

      <AdminSectionCard title="筛选条件" description="按风险、待处理状态和最近睡眠活跃度筛选患者。">
        <form method="get" className="admin-filter-grid">
          <label className="admin-field">
            <span>关键字</span>
            <input className="admin-input" name="q" defaultValue={data.filters.q} placeholder="邮箱、展示名或用户 ID" />
          </label>

          <label className="admin-field">
            <span>风险等级</span>
            <select className="admin-select" name="riskLevel" defaultValue={data.filters.riskLevel}>
              <option value="ALL">全部</option>
              <option value="HIGH">高</option>
              <option value="MEDIUM">中</option>
              <option value="LOW">低</option>
            </select>
          </label>

          <label className="admin-field">
            <span>最近睡眠窗口（天）</span>
            <input
              className="admin-input"
              name="recentSleepDays"
              type="number"
              min={1}
              max={30}
              defaultValue={data.filters.recentSleepDays}
            />
          </label>

          <label className="admin-check">
            <input name="pendingOnly" type="checkbox" value="1" defaultChecked={data.filters.pendingOnly} />
            <span>只看待处理干预</span>
          </label>

          <label className="admin-check">
            <input name="failedOnly" type="checkbox" value="1" defaultChecked={data.filters.failedOnly} />
            <span>只看失败作业影响</span>
          </label>

          <label className="admin-check">
            <input
              name="recentSleepOnly"
              type="checkbox"
              value="1"
              defaultChecked={data.filters.recentSleepOnly}
            />
            <span>只看最近有睡眠记录</span>
          </label>

          <div className="admin-button-row left-align">
            <button className="admin-primary-button" type="submit">
              应用筛选
            </button>
            <Link href={"/patients" as Route} className="admin-secondary-button link-button">
              重置
            </Link>
          </div>
        </form>
      </AdminSectionCard>

      <section className="admin-grid two-up">
        <AdminSectionCard
          title="患者池"
          description={`共 ${data.pagination.total} 位患者，第 ${data.pagination.page} / ${data.pagination.totalPages} 页。`}
        >
          {data.items.length === 0 ? (
            <EmptyState title="没有匹配的患者" description="当前筛选条件下没有结果，请调整筛选条件。" />
          ) : (
            <>
              <div className="admin-table-wrap">
                <table className="admin-table">
                  <thead>
                    <tr>
                      <th>患者</th>
                      <th>最近睡眠</th>
                      <th>恢复分</th>
                      <th>风险</th>
                      <th>待处理任务</th>
                      <th>最新作业</th>
                      <th>异常指标</th>
                      <th>操作</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.items.map((item) => (
                      <tr key={item.userId}>
                        <td>
                          <div className="admin-table-primary">{item.displayName}</div>
                          <div className="admin-table-secondary">{item.email || item.userId}</div>
                          <div className="admin-table-tertiary">{item.userId}</div>
                        </td>
                        <td>
                          <div className="admin-table-primary">{formatDate(item.latestSleepDate)}</div>
                          <div className="admin-table-secondary">最近活跃：{formatDateTime(item.lastActiveAt)}</div>
                        </td>
                        <td>{formatScore(item.latestRecoveryScore)}</td>
                        <td>
                          <AdminPill tone={toPillTone(item.latestRiskLevel, "risk")}>
                            {formatRiskLabel(item.latestRiskLevel)}
                          </AdminPill>
                        </td>
                        <td>{item.pendingInterventionCount}</td>
                        <td>
                          {item.latestJobStatus ? (
                            <AdminPill tone={toPillTone(item.latestJobStatus)}>
                              {formatStatusLabel(item.latestJobStatus)}
                            </AdminPill>
                          ) : (
                            "-"
                          )}
                        </td>
                        <td>{item.latestAbnormalMetricCount}</td>
                        <td>
                          <div className="admin-action-links">
                            <Link href={`/patients/${item.userId}` as Route} className="admin-inline-link">
                              查看详情
                            </Link>
                            <Link href={`/patients/${item.userId}?compose=1#interventions` as Route} className="admin-inline-link">
                              创建任务
                            </Link>
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
                  第 {data.pagination.page} / {data.pagination.totalPages} 页
                </span>
                <Link
                  href={buildHref(resolved, {
                    page:
                      data.pagination.page < data.pagination.totalPages
                        ? String(data.pagination.page + 1)
                        : undefined,
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

        <div className="admin-stack">
          <AdminSectionCard title="重点跟进患者" description="优先处理高风险、异常指标高或任务积压的患者。">
            {priorityPatients.length === 0 ? (
              <EmptyState title="暂无高风险患者" description="当前筛选结果里没有高风险患者。" />
            ) : (
              <div className="admin-stack">
                {priorityPatients.map((item) => (
                  <div key={item.userId} className="admin-alert-card danger">
                    <div className="admin-table-primary">{item.displayName}</div>
                    <div className="admin-table-secondary">{item.email || item.userId}</div>
                    <p>
                      风险 {formatRiskLabel(item.latestRiskLevel)} / 恢复分 {formatScore(item.latestRecoveryScore)} /
                      待处理任务 {item.pendingInterventionCount}
                    </p>
                    <div className="admin-action-links">
                      <Link href={`/patients/${item.userId}` as Route} className="admin-inline-link">
                        打开工作台
                      </Link>
                      <Link href={`/patients/${item.userId}?compose=1#interventions` as Route} className="admin-inline-link">
                        直接建任务
                      </Link>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </AdminSectionCard>

          <AdminSectionCard title="运营提醒" description="把待补量表、报告滞后和作业失败作为首屏提醒。">
            {reminders.length === 0 ? (
              <EmptyState title="暂无运营提醒" description="当前患者池没有需要立即处理的运营事项。" />
            ) : (
              <ul className="admin-bullet-list">
                {reminders.map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            )}
          </AdminSectionCard>

          <AdminSectionCard title="最近活跃患者" description="优先查看最近有数据更新的患者，适合快速回顾。">
            {recentActivityPatients.length === 0 ? (
              <EmptyState title="暂无活跃患者" description="当前筛选下没有可用患者。" />
            ) : (
              <div className="admin-stack">
                {recentActivityPatients.map((item) => (
                  <div key={item.userId} className="admin-alert-card">
                    <div className="admin-table-primary">{item.displayName}</div>
                    <div className="admin-table-secondary">{formatDateTime(item.lastActiveAt)}</div>
                    <p>
                      最近睡眠：{formatDate(item.latestSleepDate)} / 最近报告：{formatDateTime(item.latestReportAt)}
                    </p>
                  </div>
                ))}
              </div>
            )}
          </AdminSectionCard>
        </div>
      </section>
    </AdminShell>
  );
}
