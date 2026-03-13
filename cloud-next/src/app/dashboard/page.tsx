import type { Route } from "next";
import Link from "next/link";
import { AdminShell } from "@/components/admin-shell";
import {
  AdminPill,
  AdminSectionCard,
  AdminStatCard,
  EmptyState,
  formatDateTime,
  formatRiskLabel,
  formatScore,
  toPillTone,
} from "@/components/admin-ui";
import { requireAdminPage } from "@/lib/admin-auth";
import { getAdminDashboardSummary } from "@/lib/admin-dashboard";

export default async function DashboardPage() {
  await requireAdminPage();
  const data = await getAdminDashboardSummary();

  return (
    <AdminShell
      section="dashboard"
      activePath="/dashboard"
      title="总览驾驶舱"
      actions={
        <div className="admin-button-row">
          <Link href={"/patients" as Route} className="admin-secondary-button link-button">
            打开患者工作台
          </Link>
          <Link href={"/recommendations/profiles" as Route} className="admin-primary-button link-button">
            管理 SRM_V2
          </Link>
        </div>
      }
    >
      <section className="admin-stat-grid four-up">
        <AdminStatCard
          label="高风险患者"
          value={String(data.patientSummary.highRiskPatients)}
          detail={`患者总数 ${data.patientSummary.totalPatients}`}
          tone="danger"
        />
        <AdminStatCard
          label="最近建议轨迹"
          value={String(data.recommendationSummary.recentTraceCount)}
          detail={`Fallback ${data.recommendationSummary.fallbackTraceCount} 条`}
          tone="info"
        />
        <AdminStatCard
          label="效果归因率"
          value={formatScore((data.recommendationSummary.attributionRate ?? 0) * 100, "%")}
          detail={`平均效果 ${formatScore(data.recommendationSummary.avgEffectScore)}`}
          tone="success"
        />
        <AdminStatCard
          label="待处理报告"
          value={String(data.reportSummary.pendingReports)}
          detail={`高风险 ${data.reportSummary.highRiskReports} 份`}
          tone="warning"
        />
      </section>

      <section className="admin-grid two-up">
        <AdminSectionCard title="重点患者" description="优先处理高风险、待补量表和任务积压患者。">
          {data.patientSummary.items.length === 0 ? (
            <EmptyState title="暂无重点患者" description="当前没有需要立即处理的患者。" />
          ) : (
            <div className="admin-stack">
              {data.patientSummary.items.map((item) => (
                <Link key={item.userId} href={`/patients/${item.userId}` as Route} className="admin-alert-card">
                  <div className="admin-button-row left-align">
                    <div className="admin-table-primary">{item.displayName}</div>
                    <AdminPill tone={toPillTone(item.latestRiskLevel, "risk")}>
                      {formatRiskLabel(item.latestRiskLevel)}
                    </AdminPill>
                  </div>
                  <p>{item.email || item.userId}</p>
                  <span>
                    恢复分 {formatScore(item.latestRecoveryScore)} / 待处理任务 {item.pendingInterventionCount}
                  </span>
                </Link>
              ))}
            </div>
          )}
        </AdminSectionCard>

        <AdminSectionCard
          title="推荐引擎摘要"
          description="把配置、主导模式和闭环结果集中在首页查看。"
          actions={
            <div className="admin-button-row">
              <Link href={"/recommendations" as Route} className="admin-secondary-button link-button">
                建议轨迹
              </Link>
              <Link href={"/recommendations/effects" as Route} className="admin-secondary-button link-button">
                效果闭环
              </Link>
            </div>
          }
        >
          <dl className="admin-definition-list">
            <div>
              <dt>活动配置</dt>
              <dd>{data.recommendationSummary.activeProfileCode}</dd>
            </div>
            <div>
              <dt>配置来源</dt>
              <dd>{data.recommendationSummary.configSource}</dd>
            </div>
            <div>
              <dt>主导模式</dt>
              <dd>{data.recommendationSummary.topRecommendationMode ?? "-"}</dd>
            </div>
            <div>
              <dt>平均执行效果</dt>
              <dd>{formatScore(data.recommendationSummary.avgEffectScore)}</dd>
            </div>
          </dl>
        </AdminSectionCard>
      </section>

      <section className="admin-grid two-up">
        <AdminSectionCard
          title="报告处理状态"
          description="集中查看 OCR、风险报告和异常指标数量。"
          actions={
            <Link href={"/reports" as Route} className="admin-secondary-button link-button">
              打开报告工作台
            </Link>
          }
        >
          <div className="admin-stat-grid three-up">
            <AdminStatCard label="报告总数" value={String(data.reportSummary.totalReports)} tone="info" />
            <AdminStatCard label="高风险报告" value={String(data.reportSummary.highRiskReports)} tone="danger" />
            <AdminStatCard label="异常指标" value={String(data.reportSummary.abnormalMetrics)} tone="warning" />
          </div>
        </AdminSectionCard>

        <AdminSectionCard
          title="模型与作业状态"
          description="查看模型版本、队列压力和最近失败作业。"
          actions={
            <div className="admin-button-row">
              <Link href={"/system/models" as Route} className="admin-secondary-button link-button">
                模型资产
              </Link>
              <Link href={"/system/jobs" as Route} className="admin-secondary-button link-button">
                作业监控
              </Link>
            </div>
          }
        >
          <dl className="admin-definition-list">
            <div>
              <dt>启用模型</dt>
              <dd>{data.systemSummary.activeModelVersion ?? "-"}</dd>
            </div>
            <div>
              <dt>注册模型数</dt>
              <dd>{data.systemSummary.registeredModels}</dd>
            </div>
            <div>
              <dt>排队 / 失败</dt>
              <dd>
                {data.systemSummary.queueJobs} / {data.systemSummary.failedJobs}
              </dd>
            </div>
          </dl>

          {data.systemSummary.latestFailedJobs.length > 0 ? (
            <div className="admin-stack">
              {data.systemSummary.latestFailedJobs.slice(0, 3).map((item) => (
                <div key={item.jobId} className="admin-alert-card danger">
                  <div className="admin-table-primary">{item.jobId}</div>
                  <p>{item.errorMessage ?? "未记录错误信息"}</p>
                  <span>{formatDateTime(item.createdAt)}</span>
                </div>
              ))}
            </div>
          ) : (
            <EmptyState title="近期无失败作业" description="当前没有需要人工介入的失败记录。" />
          )}
        </AdminSectionCard>
      </section>
    </AdminShell>
  );
}
