import { login, signup } from "./actions";
import ResetPasswordRedirector from "./reset-password-redirector";

type SearchParams = Record<string, string | string[] | undefined>;

function pickFirst(value: string | string[] | undefined): string {
  return Array.isArray(value) ? value[0] ?? "" : value ?? "";
}

export default async function LoginPage({
  searchParams,
}: {
  searchParams?: Promise<SearchParams>;
}) {
  const resolved = (await searchParams) ?? {};
  const message = pickFirst(resolved.message);

  return (
    <main className="admin-standalone-page">
      <ResetPasswordRedirector />
      <section className="admin-login-card">
        <div>
          <p className="admin-kicker">NeuroSleep</p>
          <h1 className="admin-page-title">临床运营登录</h1>
          <p className="admin-page-subtitle">登录后即可进入模型运维台、推荐策略中心和患者运营工作台。</p>
        </div>

        <form className="admin-form-stack">
          <label className="admin-field">
            <span>邮箱</span>
            <input
              className="admin-input"
              id="email"
              name="email"
              type="email"
              autoComplete="email"
              required
            />
          </label>

          <label className="admin-field">
            <span>密码</span>
            <input
              className="admin-input"
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
            />
          </label>

          {message ? <p className="admin-form-error">{message}</p> : null}

          <div className="admin-button-row">
            <button formAction={login} className="admin-primary-button" type="submit">
              登录
            </button>
            <button formAction={signup} className="admin-secondary-button" type="submit">
              创建账号
            </button>
          </div>
        </form>
      </section>
    </main>
  );
}
