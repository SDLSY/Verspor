import type { ReactNode } from "react";
import {
  formatHealthStatusLabel,
  formatRiskLabel,
  formatRuntimeLabel,
  formatSleepQualityLabel,
  formatStatusLabel,
} from "@/lib/admin-labels";

const dateFormatter = new Intl.DateTimeFormat("zh-CN", {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});

const dateTimeFormatter = new Intl.DateTimeFormat("zh-CN", {
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
});

type PillTone = "neutral" | "success" | "warning" | "danger" | "info";

type StatCardProps = {
  label: string;
  value: string;
  detail?: string;
  tone?: PillTone;
};

type PillProps = {
  tone?: PillTone;
  children: ReactNode;
};

type EmptyStateProps = {
  title: string;
  description: string;
  action?: ReactNode;
};

type SectionCardProps = {
  title: string;
  description?: string;
  actions?: ReactNode;
  children: ReactNode;
};

export function formatDate(value: number | null | undefined): string {
  if (!value) {
    return "-";
  }
  return dateFormatter.format(new Date(value));
}

export function formatDateTime(value: number | null | undefined): string {
  if (!value) {
    return "-";
  }
  return dateTimeFormatter.format(new Date(value));
}

export function formatScore(value: number | null | undefined, suffix = ""): string {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return "-";
  }
  return `${Math.round(value)}${suffix}`;
}

export function formatMinutes(value: number | null | undefined): string {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return "-";
  }
  return `${Math.round(value)} 分钟`;
}

export function formatSeconds(value: number | null | undefined): string {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return "-";
  }
  return `${Math.round(value)} 秒`;
}

export {
  formatHealthStatusLabel,
  formatRiskLabel,
  formatRuntimeLabel,
  formatSleepQualityLabel,
  formatStatusLabel,
};

export function toPillTone(
  value: string | null | undefined,
  type: "status" | "risk" = "status"
): PillTone {
  const normalized = (value ?? "").trim().toUpperCase();
  if (type === "risk") {
    if (normalized === "HIGH" || normalized === "CRITICAL") return "danger";
    if (normalized === "MEDIUM" || normalized === "MODERATE") return "warning";
    if (normalized === "LOW") return "success";
    return "neutral";
  }
  if (normalized === "SUCCEEDED" || normalized === "COMPLETED" || normalized === "PARSED" || normalized === "ACTIVE") {
    return "success";
  }
  if (normalized === "FAILED" || normalized === "ERROR") {
    return "danger";
  }
  if (normalized === "PENDING" || normalized === "RUNNING" || normalized === "QUEUED") {
    return "warning";
  }
  return "info";
}

export function AdminStatCard({ label, value, detail, tone = "info" }: StatCardProps) {
  return (
    <article className={`admin-stat-card tone-${tone}`}>
      <p className="admin-stat-label">{label}</p>
      <p className="admin-stat-value">{value}</p>
      {detail ? <p className="admin-stat-detail">{detail}</p> : null}
    </article>
  );
}

export function AdminPill({ tone = "neutral", children }: PillProps) {
  return <span className={`admin-pill tone-${tone}`}>{children}</span>;
}

export function EmptyState({ title, description, action }: EmptyStateProps) {
  return (
    <div className="admin-empty-state">
      <h3>{title}</h3>
      <p>{description}</p>
      {action ? <div className="admin-empty-action">{action}</div> : null}
    </div>
  );
}

export function AdminSectionCard({ title, description, actions, children }: SectionCardProps) {
  return (
    <section className="admin-panel">
      <div className="admin-panel-header split">
        <div>
          <h3>{title}</h3>
          {description ? <p>{description}</p> : null}
        </div>
        {actions ? <div className="admin-page-actions">{actions}</div> : null}
      </div>
      {children}
    </section>
  );
}
