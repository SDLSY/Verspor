export type AdminNavItem = {
  href: string;
  label: string;
  section: "dashboard" | "story" | "recommendations" | "patients" | "reports" | "system";
  description?: string;
};

export const adminNavGroups: Array<{
  title: string;
  items: AdminNavItem[];
}> = [
  {
    title: "演示入口",
    items: [
      {
        href: "/dashboard",
        label: "总览驾驶舱",
        section: "dashboard",
        description: "先看五条闭环主线和整体演示状态。",
      },
      {
        href: "/story",
        label: "闭环故事",
        section: "story",
        description: "按演示账号逐条讲解端云闭环，不先掉进技术细节。",
      },
    ],
  },
  {
    title: "运营工作台",
    items: [
      {
        href: "/patients",
        label: "患者工作台",
        section: "patients",
        description: "查看 demo 患者、高风险患者和个体工作台。",
      },
      {
        href: "/reports",
        label: "报告与问诊",
        section: "reports",
        description: "按待处理队列查看报告、问诊和干预承接。",
      },
      {
        href: "/recommendations",
        label: "建议与效果",
        section: "recommendations",
        description: "查看建议原因、执行结果和效果闭环。",
      },
    ],
  },
  {
    title: "高级运维",
    items: [
      {
        href: "/system/models",
        label: "模型资产",
        section: "system",
        description: "查看当前启用版本、健康状态和高级配置。",
      },
      {
        href: "/system/jobs",
        label: "作业监控",
        section: "system",
        description: "查看失败作业、影响范围和手动触发入口。",
      },
      {
        href: "/system/audit",
        label: "审计记录",
        section: "system",
        description: "查看配置修改、人工操作和系统审计流水。",
      },
    ],
  },
];

export const adminBrand = {
  kicker: "ChangGeng Ring Demo Console",
  title: "端云闭环演示与运营后台",
  description: "先讲清恢复、报告、药食、干预和高风险运维闭环，再下钻运营动作与技术诊断。",
};
