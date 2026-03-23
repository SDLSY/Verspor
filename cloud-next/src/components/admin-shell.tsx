import type { ReactNode } from "react";
import type { Route } from "next";
import Link from "next/link";
import { logout } from "@/app/login/actions";
import { adminBrand, adminNavGroups } from "@/lib/admin-copy";

type AdminSection = "dashboard" | "story" | "recommendations" | "patients" | "reports" | "system";

type AdminShellProps = {
  section: AdminSection;
  activePath: string;
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  children: ReactNode;
};

const sectionLabels: Record<AdminSection, string> = {
  dashboard: "驾驶舱",
  story: "闭环故事",
  recommendations: "推荐策略",
  patients: "患者运营",
  reports: "报告与问诊",
  system: "高级运维",
};

function isActivePath(activePath: string, href: string): boolean {
  if (href === "/patients") {
    return activePath === href || activePath.startsWith("/patients/");
  }
  return activePath === href;
}

export function AdminShell({
  section,
  activePath,
  title,
  subtitle,
  actions,
  children,
}: AdminShellProps) {
  return (
    <main className="admin-app">
      <aside className="admin-sidebar">
        <div className="admin-brand-block">
          <p className="admin-kicker">{adminBrand.kicker}</p>
          <h1 className="admin-brand-title">{adminBrand.title}</h1>
          <p className="admin-brand-copy">{adminBrand.description}</p>
        </div>
        <div className="admin-frontdoor">
          <a className="admin-ghost-button admin-frontdoor-link" href="/" target="_top">
            返回前台
          </a>
        </div>

        <nav className="admin-nav" aria-label="后台导航">
          {adminNavGroups.map((group) => (
            <div key={group.title} className="admin-nav-group">
              <p className="admin-nav-title">{group.title}</p>
              {group.items.map((item) => (
                <Link
                  key={item.href}
                  href={item.href as Route}
                  className={`admin-nav-link${isActivePath(activePath, item.href) ? " is-active" : ""}`}
                  title={item.description}
                >
                  {item.label}
                </Link>
              ))}
            </div>
          ))}
        </nav>

        <form action={logout} className="admin-sidebar-footer">
          <button type="submit" className="admin-ghost-button">
            退出登录
          </button>
        </form>
      </aside>

      <div className="admin-main">
        <header className="admin-topbar">
          <div>
            <p className="admin-section-label">{sectionLabels[section]}</p>
            <h2 className="admin-page-title">{title}</h2>
            {subtitle ? <p className="admin-page-subtitle">{subtitle}</p> : null}
          </div>
          {actions ? <div className="admin-page-actions">{actions}</div> : null}
        </header>

        <section className="admin-content">{children}</section>
      </div>
    </main>
  );
}
