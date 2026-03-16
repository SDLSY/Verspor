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
      subtitle="先用五条闭环讲清产品，再进入患者、报告、建议和高级运维。"
      actions={
        <div className="admin-button-row">
          <Link href={"/story" as Route} className="admin-primary-button link-button">
            进入闭环故事
          </Link>
          <Link href={"/patients?demoOnly=1" as Route} className="admin-secondary-button link-button">
            查看演示患者池
          </Link>
        </div>
      }
    >
      <AdminSectionCard
        title="五条演示主线"
        description="后台第一屏直接对应 demo 账号矩阵，避免一开始掉进技术术语。"
      >
        <div className="admin-story-grid">
          {data.storyCards.map((story) => (
            <article key={story.scenarioCode} className="admin-story-card featured">
              <div className="admin-story-card-top">
                <div>
                  <h3>{story.scenarioLabel}</h3>
                  <p>{story.storyStage}</p>
                </div>
                <AdminPill tone={story.patientCount > 0 ? "success" : "warning"}>
                  {story.patientCount > 0 ? `${story.patientCount} 个样本` : "暂无样本"}
                </AdminPill>
              </div>
              <p className="admin-story-summary">{story.summary}</p>
              <div className="admin-story-metrics">
                <span>证据点 {story.evidenceCount}</span>
                <span>{story.actionSummary}</span>
              </div>
              <div className="admin-button-row left-align">
                <Link href={story.recommendedPath as Route} className="admin-primary-button link-button">
                  直接开始
                </Link>
              </div>
            </article>
          ))}
        </div>
      </AdminSectionCard>

      <section className="admin-stat-grid four-up">
        <AdminStatCard
          label="演示患者"
          value={String(data.patientSummary.demoPatients)}
          detail={`总患者 ${data.patientSummary.totalPatients}`}
          tone="info"
        />
        <AdminStatCard
          label="高风险患者"
          value={String(data.patientSummary.highRiskPatients)}
          detail={`待处理任务 ${data.patientSummary.pendingInterventions}`}
          tone="danger"
        />
        <AdminStatCard
          label="待处理报告"
          value={String(data.reportSummary.pendingReports)}
          detail={`高风险 ${data.reportSummary.highRiskReports} 份`}
          tone="warning"
        />
        <AdminStatCard
          label="建议闭环"
          value={formatScore((data.recommendationSummary.attributionRate ?? 0) * 100, "%")}
          detail={`平均效果 ${formatScore(data.recommendationSummary.avgEffectScore)}`}
          tone="success"
        />
      </section>

      <section className="admin-grid two-up">
        <AdminSectionCard
          title="优先讲这几位患者"
          description="优先展示 demo 患者和高风险患者，快速落到具体工作台。"
          actions={
            <Link href={"/patients" as Route} className="admin-secondary-button link-button">
              打开患者工作台
            </Link>
          }
        >
          {data.patientSummary.items.length === 0 ? (
            <EmptyState title="暂无可讲患者" description="当前没有满足演示条件的患者。" />
          ) : (
            <div className="admin-stack">
              {data.patientSummary.items.slice(0, 6).map((item) => (
                <Link key={item.userId} href={`/patients/${item.userId}` as Route} className="admin-alert-card">
                  <div className="admin-button-row left-align">
                    <div className="admin-table-primary">{item.displayName}</div>
                    {item.scenarioLabel ? <AdminPill tone="info">{item.scenarioLabel}</AdminPill> : null}
                    <AdminPill tone={toPillTone(item.latestRiskLevel, "risk")}>
                      {formatRiskLabel(item.latestRiskLevel)}
                    </AdminPill>
                  </div>
                  <p>{item.actionSummary ?? item.email ?? item.userId}</p>
                  <span>
                    恢复分 {formatScore(item.latestRecoveryScore)} / 待处理任务 {item.pendingInterventionCount}
                  </span>
                </Link>
              ))}
            </div>
          )}
        </AdminSectionCard>

        <AdminSectionCard
          title="后台当前状态"
          description="技术信息保留，但只作为第二层信息，不抢闭环主线。"
          actions={
            <div className="admin-button-row">
              <Link href={"/recommendations" as Route} className="admin-secondary-button link-button">
                建议与效果
              </Link>
              <Link href={"/system/jobs" as Route} className="admin-secondary-button link-button">
                高级运维
              </Link>
            </div>
          }
        >
          <dl className="admin-definition-list">
            <div>
              <dt>当前策略配置</dt>
              <dd>{data.recommendationSummary.activeProfileCode}</dd>
            </div>
            <div>
              <dt>主导建议模式</dt>
              <dd>{data.recommendationSummary.topRecommendationMode ?? "-"}</dd>
            </div>
            <div>
              <dt>当前启用模型</dt>
              <dd>{data.systemSummary.activeModelVersion ?? "-"}</dd>
            </div>
            <div>
              <dt>排队 / 失败作业</dt>
              <dd>
                {data.systemSummary.queueJobs} / {data.systemSummary.failedJobs}
              </dd>
            </div>
          </dl>

          {data.systemSummary.latestFailedJobs.length > 0 ? (
            <div className="admin-stack">
              {data.systemSummary.latestFailedJobs.slice(0, 2).map((item) => (
                <div key={item.jobId} className="admin-alert-card danger">
                  <div className="admin-table-primary">{item.userEmail ?? item.userId}</div>
                  <p>{item.errorMessage ?? "未记录错误信息"}</p>
                  <span>{formatDateTime(item.createdAt)}</span>
                </div>
              ))}
            </div>
          ) : null}
        </AdminSectionCard>
      </section>
    </AdminShell>
  );
}
