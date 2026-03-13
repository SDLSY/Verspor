export type AdminNavItem = {
  href: string;
  label: string;
  section: "dashboard" | "recommendations" | "patients" | "reports" | "system";
  description?: string;
};

export const adminNavGroups: Array<{
  title: string;
  items: AdminNavItem[];
}> = [
  {
    title: "驾驶舱",
    items: [
      {
        href: "/dashboard",
        label: "总览驾驶舱",
        section: "dashboard",
        description: "查看高风险患者、推荐运行和系统状态",
      },
    ],
  },
  {
    title: "推荐策略",
    items: [
      {
        href: "/recommendations",
        label: "建议轨迹",
        section: "recommendations",
        description: "查看解释、模式分布和最近建议结果",
      },
      {
        href: "/recommendations/profiles",
        label: "策略配置",
        section: "recommendations",
        description: "管理 SRM_V2 阈值、权重、门控和模式优先级",
      },
      {
        href: "/recommendations/effects",
        label: "效果闭环",
        section: "recommendations",
        description: "追踪建议执行归因、配置效果和模式表现",
      },
    ],
  },
  {
    title: "患者运营",
    items: [
      {
        href: "/patients",
        label: "患者工作台",
        section: "patients",
        description: "查看高风险患者、待处理事项和个体运营详情",
      },
    ],
  },
  {
    title: "报告与问诊",
    items: [
      {
        href: "/reports",
        label: "报告工作台",
        section: "reports",
        description: "集中查看 OCR、医检、解析风险和待确认报告",
      },
    ],
  },
  {
    title: "系统运维",
    items: [
      {
        href: "/system/models",
        label: "模型资产",
        section: "system",
        description: "管理推理模型、provider 和当前生效版本",
      },
      {
        href: "/system/jobs",
        label: "作业监控",
        section: "system",
        description: "查看队列、失败作业、延迟和 worker 状态",
      },
      {
        href: "/system/audit",
        label: "审计记录",
        section: "system",
        description: "查看配置修改、人工操作和系统审计流水",
      },
    ],
  },
];

export const adminBrand = {
  kicker: "VesperO Console",
  title: "模型运维与患者运营后台",
  description:
    "围绕推荐策略、效果闭环、患者运营、报告处理和系统运维构建的一体化控制台。",
};
