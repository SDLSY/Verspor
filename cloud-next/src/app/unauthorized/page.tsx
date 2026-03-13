import { logout } from "@/app/login/actions";
import { requireSignedInPage } from "@/lib/admin-auth";

export default async function UnauthorizedPage() {
  const user = await requireSignedInPage();

  return (
    <main className="admin-standalone-page">
      <section className="admin-standalone-card">
        <p className="admin-kicker">需要后台权限</p>
        <h1 className="admin-page-title">当前账号已登录，但尚未加入后台白名单。</h1>
        <p className="admin-page-subtitle">
          当前账号：<strong>{user.email ?? user.id}</strong>
        </p>
        <p className="admin-page-subtitle">
          请先将该邮箱加入 <code>ADMIN_EMAIL_ALLOWLIST</code>，再进入控制台。
        </p>
        <form action={logout}>
          <button type="submit" className="admin-primary-button">
            退出登录
          </button>
        </form>
      </section>
    </main>
  );
}
