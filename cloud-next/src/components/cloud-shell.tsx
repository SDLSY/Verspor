import Link from "next/link";
import type { Route } from "next";
import type { ReactNode } from "react";

type CloudShellProps = {
  active: "overview" | "audit" | "inference" | "analytics";
  title: string;
  subtitle: string;
  children: ReactNode;
};

const navItems: Array<{ key: CloudShellProps["active"]; href: Route; label: string }> = [
  { key: "overview", href: "/", label: "终端设备概览" },
  { key: "analytics", href: "/analytics" as Route, label: "群体健康分析" },
  { key: "inference", href: "/inference", label: "推理引擎控制台" },
  { key: "audit", href: "/audit" as Route, label: "系统审计日志" },
];

export function CloudShell({ active, title, subtitle, children }: CloudShellProps) {
  return (
    <main className="cloud-page">
      <header className="cloud-header">
        <p className="cloud-kicker">NeuroSleep Cloud Platform</p>
        <h1>{title}</h1>
        <p className="cloud-subtitle">{subtitle}</p>
        <nav className="cloud-nav" aria-label="Cloud sections">
          {navItems.map((item) => (
            <Link
              key={item.key}
              href={item.href}
              className={`cloud-nav-link${item.key === active ? " is-active" : ""}`}
            >
              {item.label}
            </Link>
          ))}
        </nav>
      </header>

      <section className="cloud-content">{children}</section>
    </main>
  );
}
