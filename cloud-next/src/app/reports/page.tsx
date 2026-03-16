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

function buildQueueStage(item: Awaited<ReturnType<typeof listAdminReports>>["items"][number]): string {
  const parseStatus = item.parseStatus.toUpperCase();
  const riskLevel = (item.riskLevel ?? "").toUpperCase();
  if (parseStatus === "PENDING") {
    return "待解析";
  }
  if ((riskLevel === "HIGH" || riskLevel === "CRITICAL") && !item.latestDoctorSummary) {
    return "待问诊";
  }
  if (item.latestDoctorSummary) {
    return "已形成建议";
  }
  if (riskLevel === "HIGH" || riskLevel === "CRITICAL") {
    return "高风险";
  }
  return "已完成解析";
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

  const pendingQueue = data.items.filter((item) => buildQueueStage(item) === "待解析").length;
  const highRiskQueue = data.items.filter((item) => {
    const risk = (item.riskLevel ?? "").toUpperCase();
    return risk === "HIGH" || risk === "CRITICAL";
  }).length;
  const pendingInquiry = data.items.filter((item) => buildQueueStage(item) === "待问诊").length;
  const advisedQueue = data.items.filter((item) => buildQueueStage(item) === "已形成建议").length;

  return (
    <AdminShell
      section="reports"
      activePath="/reports"
      title="报告与问诊"
      subtitle="按待处理队列查看报告、问诊和干预承接，不先暴露底层解析细节。"
    >
      <section className="admin-stat-grid four-up">
        <AdminStatCard label="待解析" value={String(pendingQueue)} tone="warning" />
        <AdminStatCard label="高风险" value={String(highRiskQueue)} tone="danger" />
        <AdminStatCard label="待问诊" value={String(pendingInquiry)} tone="info" />
        <AdminStatCard label="已形成建议" value={String(advisedQueue)} tone="success" />
      </section>

      <AdminSectionCard
        title="筛选条件"
        description="用固定选项筛报告状态，避免现场演示时靠手输文本。"
      >
        <form method="get" className="admin-filter-grid compact">
          <label className="admin-field">
            <span>关键字</span>
            <input
              className="admin-input"
              name="q"
              defaultValue={data.filters.q}
              placeholder="患者、报告 ID、问诊摘要"
            />
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
            <span>解析状态</span>
            <select className="admin-select" name="parseStatus" defaultValue={data.filters.parseStatus}>
              <option value="ALL">全部</option>
              <option value="PENDING">待处理</option>
              <option value="PARSED">已解析</option>
              <option value="FAILED">失败</option>
            </select>
          </label>
          <div className="admin-button-row left-align">
            <button className="admin-primary-button" type="submit">
              应用筛选
            </button>
            <Link href={"/reports" as Route} className="admin-secondary-button link-button">
              重置
            </Link>
          </div>
        </form>
      </AdminSectionCard>

      <AdminSectionCard
        title="待处理队列"
        description="先回答这份报告现在卡在哪一步，再决定跳到哪个患者工作台。"
      >
        {data.items.length === 0 ? (
          <EmptyState title="暂无报告" description="当前筛选条件下没有匹配结果。" />
        ) : (
          <div className="admin-table-wrap">
            <table className="admin-table">
              <thead>
                <tr>
                  <th>患者</th>
                  <th>场景</th>
                  <th>当前进展</th>
                  <th>报告</th>
                  <th>关键异常</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                {data.items.map((item) => {
                  const queueStage = buildQueueStage(item);
                  return (
                    <tr key={item.reportId}>
                      <td>
                        <div className="admin-table-primary">{item.displayName}</div>
                        <div className="admin-table-secondary">{item.email || item.userId}</div>
                      </td>
                      <td>
                        {item.scenarioLabel ? (
                          <AdminPill tone="info">{item.scenarioLabel}</AdminPill>
                        ) : (
                          <span className="admin-table-secondary">常规患者</span>
                        )}
                      </td>
                      <td>
                        <div className="admin-stack compact">
                          <AdminPill
                            tone={
                              queueStage === "待解析"
                                ? "warning"
                                : queueStage === "已形成建议"
                                  ? "success"
                                  : "info"
                            }
                          >
                            {queueStage}
                          </AdminPill>
                          <span className="admin-table-secondary">
                            {item.latestDoctorSummary ?? item.latestRecommendedDepartment ?? "建议先查看患者详情"}
                          </span>
                        </div>
                      </td>
                      <td>
                        <div className="admin-stack compact">
                          <div className="admin-table-primary">{item.reportId}</div>
                          <div className="admin-table-secondary">
                            {formatReportTypeLabel(item.reportType)} / {formatDateTime(item.reportDate)}
                          </div>
                          <div className="admin-pill-row wrap">
                            <AdminPill tone={toPillTone(item.parseStatus)}>
                              {formatParseStatusLabel(item.parseStatus)}
                            </AdminPill>
                            <AdminPill tone={toPillTone(item.riskLevel, "risk")}>
                              {formatRiskLabel(item.riskLevel)}
                            </AdminPill>
                          </div>
                        </div>
                      </td>
                      <td>{item.abnormalMetricCount}</td>
                      <td>
                        <Link href={`/patients/${item.userId}` as Route} className="admin-inline-link">
                          查看患者工作台
                        </Link>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </AdminSectionCard>
    </AdminShell>
  );
}
