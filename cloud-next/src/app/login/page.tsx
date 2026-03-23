import { login, loginWithDemoAccount, signup } from "./actions";
import ResetPasswordRedirector from "./reset-password-redirector";

type SearchParams = Record<string, string | string[] | undefined>;
const COPY = {
  title: "\u540E\u53F0\u767B\u5F55",
  subtitle:
    "\u767B\u5F55\u540E\u53EF\u8FDB\u5165\u7AEF\u4E91\u95ED\u73AF\u6F14\u793A\u540E\u53F0\uFF0C\u67E5\u770B\u60A3\u8005\u5DE5\u4F5C\u53F0\u3001\u62A5\u544A\u95EE\u8BCA\u3001\u5EFA\u8BAE\u6548\u679C\u548C\u9AD8\u7EA7\u8FD0\u7EF4\u9875\u9762\u3002",
  email: "\u90AE\u7BB1",
  password: "\u5BC6\u7801",
  login: "\u767B\u5F55",
  signup: "\u521B\u5EFA\u8D26\u53F7",
  demoLogin: "\u4F7F\u7528\u6F14\u793A\u8D26\u53F7\u76F4\u63A5\u767B\u5F55",
};

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
          <p className="admin-kicker">ChangGeng Ring Demo Console</p>
          <h1 className="admin-page-title">{COPY.title}</h1>
          <p className="admin-page-subtitle">{COPY.subtitle}</p>
        </div>

        <form className="admin-form-stack">
          <label className="admin-field">
            <span>{COPY.email}</span>
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
            <span>{COPY.password}</span>
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
              {COPY.login}
            </button>
            <button formAction={signup} className="admin-secondary-button" type="submit">
              {COPY.signup}
            </button>
          </div>
          <button
            formAction={loginWithDemoAccount}
            className="admin-secondary-button"
            formNoValidate
            style={{ width: "100%" }}
            type="submit"
          >
            {COPY.demoLogin}
          </button>
        </form>
      </section>
    </main>
  );
}
