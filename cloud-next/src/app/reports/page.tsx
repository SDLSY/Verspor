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
import { formatParseStatusLabel, formatReportTypeLabel, formatRiskLabel } from "@/lib/admin-labels";
import { listAdminReports } from "@/lib/admin-reports";

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

export default async function ReportsPage({
  searchParams,
}: {
  searchParams?: Promise<SearchParams>;
}) {
  await requireAdminPage();
  const resolved = (await searchParams) ?? {};
  const data = await listAdminReports({
    page: pickNumber(resolved.page),
    q: pickFirst(resolved.q) || undefined,
    riskLevel: pickFirst(resolved.riskLevel) || undefined,
    parseStatus: pickFirst(resolved.parseStatus) || undefined,
  });

  return (
    <AdminShell section="reports" activePath="/reports" title="报告工作台">
      <section className="admin-stat-grid four-up">
        <AdminStatCard label="报告总数" value={String(data.summary.totalReports)} tone="info" />
        <AdminStatCard label="待处理报告" value={String(data.summary.pendingReports)} tone="warning" />
        <AdminStatCard label="高风险报告" value={String(data.summary.highRiskReports)} tone="danger" />
        <AdminStatCard label="异常指标" value={String(data.summary.abnormalMetrics)} tone="success" />
      </section>

      <AdminSectionCard title="筛选条件" description="按患者、风险和解析状态筛选报告。">
        <form method="get" className="admin-filter-grid compact">
          <label className="admin-field">
            <span>关键字</span>
            <input className="admin-input" name="q" defaultValue={data.filters.q} placeholder="患者、报告 ID、问诊摘要" />
          </label>
          <label className="admin-field">
            <span>风险等级</span>
            <input className="admin-input" name="riskLevel" defaultValue={data.filters.riskLevel === "ALL" ? "" : data.filters.riskLevel} />
          </label>
          <label className="admin-field">
            <span>解析状态</span>
            <input className="admin-input" name="parseStatus" defaultValue={data.filters.parseStatus === "ALL" ? "" : data.filters.parseStatus} />
          </label>
          <div className="admin-button-row left-align">
            <button className="admin-primary-button" type="submit">应用筛选</button>
            <Link href={"/reports" as Route} className="admin-secondary-button link-button">
              重置
            </Link>
          </div>
        </form>
      </AdminSectionCard>

      <AdminSectionCard title="报告列表" description="从报告直接跳到患者工作台，追踪问诊和建议轨迹。">
        {data.items.length === 0 ? (
          <EmptyState title="暂无报告" description="当前筛选条件下没有匹配结果。" />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>患者</th>
                  <th>报告</th>
                  <th>解析状态</th>
                  <th>风险</th>
                  <th>异常指标</th>
                  <th>最近问诊摘要</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((item) => (
                  <tr key={item.reportId}>
                    <td>
                      <div className="admin-table-primary">{item.displayName}</div>
                      <div className="admin-table-secondary">{item.email || item.userId}</div>
                    </td>
                    <td>
                      <div className="admin-table-primary">{item.reportId}</div>
                      <div className="admin-table-secondary">
                        {formatReportTypeLabel(item.reportType)} / {formatDateTime(item.reportDate)}
                      </div>
                    </td>
                    <td>
                      <AdminPill tone={toPillTone(item.parseStatus)}>
                        {formatParseStatusLabel(item.parseStatus)}
                      </AdminPill>
                    </td>
                    <td>
                      <AdminPill tone={toPillTone(item.riskLevel, "risk")}>
                        {formatRiskLabel(item.riskLevel)}
                      </AdminPill>
                    </td>
                    <td>{item.abnormalMetricCount}</td>
                    <td className="admin-cell-break">{item.latestDoctorSummary ?? item.latestRecommendedDepartment ?? "-"}</td>
                    <td>
                      <Link href={`/patients/${item.userId}` as Route} className="admin-inline-link">
                        查看患者
                      </Link>
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
