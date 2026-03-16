import type { Route } from "next";
import Link from "next/link";
import { AdminShell } from "@/components/admin-shell";
import { AdminPill, AdminSectionCard, AdminStatCard } from "@/components/admin-ui";
import { requireAdminPage } from "@/lib/admin-auth";
import { getAdminDashboardSummary } from "@/lib/admin-dashboard";

export default async function StoryPage() {
  await requireAdminPage();
  const data = await getAdminDashboardSummary();

  return (
    <AdminShell
      section="story"
      activePath="/story"
      title="闭环故事"
      subtitle="把五条演示闭环按固定讲解顺序排好，先讲用户价值，再展开运营和技术证据。"
      actions={
        <div className="admin-button-row">
          <Link href={"/dashboard" as Route} className="admin-secondary-button link-button">
            返回驾驶舱
          </Link>
          <Link href={"/patients?demoOnly=1" as Route} className="admin-primary-button link-button">
            打开演示患者池
          </Link>
        </div>
      }
    >
      <section className="admin-stat-grid three-up">
        <AdminStatCard
          label="闭环账号"
          value={String(data.patientSummary.demoPatients)}
          detail="用于答辩与内部演示"
          tone="info"
        />
        <AdminStatCard
          label="高风险闭环"
          value={String(data.patientSummary.highRiskPatients)}
          detail="优先讲异常与处理动作"
          tone="danger"
        />
        <AdminStatCard
          label="待处理动作"
          value={String(data.patientSummary.pendingInterventions)}
          detail="覆盖报告、任务与作业影响"
          tone="warning"
        />
      </section>

      <AdminSectionCard
        title="推荐讲解顺序"
        description="建议从通用体验讲到高风险运维，避免一开始就陷进技术细节。"
      >
        <div className="admin-story-grid">
          {data.storyCards.map((story, index) => (
            <article key={story.scenarioCode} className="admin-story-card">
              <div className="admin-story-card-top">
                <div>
                  <p className="admin-story-index">{`0${index + 1}`}</p>
                  <h3>{story.scenarioLabel}</h3>
                  <p>{story.storyStage}</p>
                </div>
                <AdminPill tone={story.patientCount > 0 ? "success" : "warning"}>
                  {story.patientCount > 0 ? `${story.patientCount} 个演示账号` : "待补数据"}
                </AdminPill>
              </div>
              <p className="admin-story-summary">{story.summary}</p>
              <dl className="admin-definition-list compact">
                <div>
                  <dt>推荐讲法</dt>
                  <dd>{story.actionSummary}</dd>
                </div>
                <div>
                  <dt>可见证据点</dt>
                  <dd>{story.evidenceCount}</dd>
                </div>
              </dl>
              <div className="admin-button-row left-align">
                <Link href={story.recommendedPath as Route} className="admin-primary-button link-button">
                  开始讲解
                </Link>
              </div>
            </article>
          ))}
        </div>
      </AdminSectionCard>
    </AdminShell>
  );
}
