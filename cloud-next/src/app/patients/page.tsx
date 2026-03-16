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
import { adminDemoScenarios } from "@/lib/admin-story";
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
    scenarioCode: pickFirst(resolved.scenarioCode) || undefined,
    demoOnly: pickBoolean(resolved.demoOnly),
    pendingOnly: pickBoolean(resolved.pendingOnly),
    failedOnly: pickBoolean(resolved.failedOnly),
    recentSleepOnly: pickBoolean(resolved.recentSleepOnly),
    recentSleepDays: pickNumber(resolved.recentSleepDays),
  });

  const demoPatients = data.items.filter((item) => item.isDemoUser);
  const priorityPatients = data.items.filter((item) => item.latestRiskLevel === "HIGH").slice(0, 4);

  return (
    <AdminShell
      section="patients"
      activePath="/patients"
      title="患者工作台"
      subtitle="先讲 demo 闭环患者，再下钻完整患者池和日常运营动作。"
      actions={
        <div className="admin-button-row">
          <Link href={"/story" as Route} className="admin-secondary-button link-button">
            返回闭环故事
          </Link>
          <Link href={"/patients?demoOnly=1" as Route} className="admin-primary-button link-button">
            只看演示患者
          </Link>
        </div>
      }
    >
      <section className="admin-stat-grid four-up">
        <AdminStatCard
          label="演示患者"
          value={String(data.summary.demoPatients)}
          detail="用于答辩与固定闭环演示"
          tone="info"
        />
        <AdminStatCard
          label="高风险患者"
          value={String(data.summary.highRiskPatients)}
          detail="优先进入人工处理视角"
          tone="danger"
        />
        <AdminStatCard
          label="待处理干预"
          value={String(data.summary.pendingInterventions)}
          detail="待执行或待跟进任务"
          tone="warning"
        />
        <AdminStatCard
          label="失败作业影响"
          value={String(data.summary.failedJobPatients)}
          detail="近 24 小时需要排查患者"
          tone="danger"
        />
      </section>

      <section className="admin-grid two-up">
        <AdminSectionCard title="闭环患者池" description="直接展示演示账号，对应每条故事线的讲解入口。">
          {demoPatients.length === 0 ? (
            <EmptyState title="暂无演示患者" description="请先 seed demo 账号，或检查当前筛选条件。" />
          ) : (
            <div className="admin-stack">
              {demoPatients.map((item) => (
                <article key={item.userId} className="admin-alert-card">
                  <div className="admin-button-row left-align">
                    <div className="admin-table-primary">{item.displayName}</div>
                    {item.scenarioLabel ? <AdminPill tone="info">{item.scenarioLabel}</AdminPill> : null}
                    <AdminPill tone={toPillTone(item.latestRiskLevel, "risk")}>
                      {formatRiskLabel(item.latestRiskLevel)}
                    </AdminPill>
                  </div>
                  <p>{item.storyStage ?? "演示场景"}</p>
                  <span>{item.actionSummary ?? "打开患者工作台，按证据 -> 建议 -> 回写顺序讲解。"}</span>
                  <div className="admin-button-row left-align">
                    <Link href={`/patients/${item.userId}` as Route} className="admin-inline-link">
                      打开工作台
                    </Link>
                    <Link href={item.recommendedPath as Route} className="admin-inline-link">
                      直达讲解入口
                    </Link>
                  </div>
                </article>
              ))}
            </div>
          )}
        </AdminSectionCard>

        <AdminSectionCard title="重点跟进患者" description="优先处理高风险、任务积压或失败作业影响患者。">
          {priorityPatients.length === 0 ? (
            <EmptyState title="暂无重点患者" description="当前筛选结果里没有高风险患者。" />
          ) : (
            <div className="admin-stack">
              {priorityPatients.map((item) => (
                <div key={item.userId} className="admin-alert-card danger">
                  <div className="admin-button-row left-align">
                    <div className="admin-table-primary">{item.displayName}</div>
                    <AdminPill tone={toPillTone(item.latestRiskLevel, "risk")}>
                      {formatRiskLabel(item.latestRiskLevel)}
                    </AdminPill>
                  </div>
                  <p>
                    恢复分 {formatScore(item.latestRecoveryScore)} / 待处理任务 {item.pendingInterventionCount} / 失败作业{" "}
                    {item.hasRecentFailedJob ? "是" : "否"}
                  </p>
                  <div className="admin-button-row left-align">
                    <Link href={`/patients/${item.userId}` as Route} className="admin-inline-link">
                      打开患者详情
                    </Link>
                  </div>
                </div>
              ))}
            </div>
          )}
        </AdminSectionCard>
      </section>

      <AdminSectionCard title="筛选条件" description="统一使用枚举和开关筛患者，不依赖自由输入才能操作。">
        <form method="get" className="admin-filter-grid compact">
          <label className="admin-field">
            <span>关键字</span>
            <input
              className="admin-input"
              name="q"
              defaultValue={data.filters.q}
              placeholder="邮箱、显示名或用户 ID"
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
            <span>演示场景</span>
            <select className="admin-select" name="scenarioCode" defaultValue={data.filters.scenarioCode}>
              <option value="ALL">全部</option>
              {adminDemoScenarios.map((scenario) => (
                <option key={scenario.code} value={scenario.code}>
                  {scenario.shortTitle}
                </option>
              ))}
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
            <input name="demoOnly" type="checkbox" value="1" defaultChecked={data.filters.demoOnly} />
            <span>只看演示患者</span>
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

      <AdminSectionCard
        title="完整患者池"
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
                    <th>场景</th>
                    <th>最近睡眠</th>
                    <th>恢复分</th>
                    <th>风险</th>
                    <th>待处理任务</th>
                    <th>讲解入口</th>
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
                        {item.scenarioLabel ? (
                          <div className="admin-stack compact">
                            <AdminPill tone="info">{item.scenarioLabel}</AdminPill>
                            <span className="admin-table-secondary">{item.storyStage ?? "-"}</span>
                          </div>
                        ) : (
                          <span className="admin-table-secondary">常规患者</span>
                        )}
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
                      <td>
                        <div className="admin-stack compact">
                          <span>{item.pendingInterventionCount}</span>
                          {item.latestJobStatus ? (
                            <AdminPill tone={toPillTone(item.latestJobStatus)}>
                              {formatStatusLabel(item.latestJobStatus)}
                            </AdminPill>
                          ) : null}
                        </div>
                      </td>
                      <td>
                        {item.scenarioLabel ? (
                          <div className="admin-stack compact">
                            <span className="admin-table-secondary">
                              {item.actionSummary ?? "打开详情后开始讲解"}
                            </span>
                            <Link href={item.recommendedPath as Route} className="admin-inline-link">
                              直达讲解入口
                            </Link>
                          </div>
                        ) : (
                          <span className="admin-table-secondary">查看详情后按证据顺序讲解</span>
                        )}
                      </td>
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
