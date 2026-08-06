/**
 * 로그인 화면 — 좌/우 비대칭 스플릿(DESIGN.md 시안 B). 좌측은 브랜드 카피, 우측은 로그인 폼.
 * 데모 로그인 버튼은 admin/admin 을 채워 넣고 동일한 정규 로그인 API를 호출한다(우회 없음).
 */
import { useState, type FormEvent } from "react";
import {
  Navigate,
  useLocation,
  useNavigate,
  type Location,
} from "react-router";
import { LogIn, Sparkles } from "lucide-react";
import { useAuthStore } from "../stores/authStore";
import { useLoginMutation } from "../api/auth";
import { LoginRequestSchema } from "../schemas/auth";
import { extractErrorMessage } from "../api/errors";

export default function Login() {
  const token = useAuthStore((state) => state.token);
  const navigate = useNavigate();
  const location = useLocation();
  const loginMutation = useLoginMutation();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<{
    email?: string;
    password?: string;
  }>({});
  const [formError, setFormError] = useState<string | null>(null);

  if (token) {
    const redirectTo =
      (location.state as { from?: Location })?.from?.pathname ?? "/";
    return <Navigate to={redirectTo} replace />;
  }

  async function submit(email: string, password: string) {
    setFormError(null);
    const parsed = LoginRequestSchema.safeParse({ email, password });
    if (!parsed.success) {
      const issues = parsed.error.issues;
      setFieldErrors({
        email: issues.find((issue) => issue.path[0] === "email")?.message,
        password: issues.find((issue) => issue.path[0] === "password")?.message,
      });
      return;
    }
    setFieldErrors({});
    try {
      await loginMutation.mutateAsync(parsed.data);
      navigate("/", { replace: true });
    } catch (error) {
      setFormError(extractErrorMessage(error, "로그인에 실패했습니다"));
    }
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    void submit(email, password);
  }

  function handleDemoLogin() {
    setEmail("admin");
    setPassword("admin");
    void submit("admin", "admin");
  }

  return (
    <div className="grid min-h-screen grid-cols-1 lg:grid-cols-[3fr_2fr]">
      <section className="hidden flex-col justify-center bg-[var(--text-strong)] px-16 py-12 text-[var(--surface)] lg:flex">
        <div className="login-brand-copy max-w-md">
          <p className="mb-4 inline-flex items-center gap-2 text-sm font-semibold text-[var(--accent)]">
            <Sparkles size={16} aria-hidden />
            study-fine
          </p>
          <h1 className="mb-4 text-4xl font-bold leading-tight">
            스터디 벌금, 더 이상 카톡방과 엑셀에 맡기지 마세요
          </h1>
          <p className="text-base leading-relaxed text-[color-mix(in_srgb,var(--surface)_75%,transparent)]">
            출석을 체크하는 순간 벌금이 자동으로 확정 저장됩니다. 단가를 나중에
            바꿔도 지난 회차의 금액은 절대 바뀌지 않습니다.
          </p>
        </div>
      </section>

      <section className="flex flex-col justify-center px-6 py-12 sm:px-12">
        <div className="mx-auto w-full max-w-sm">
          <h2 className="mb-1 text-2xl font-bold text-[var(--text-strong)]">
            로그인
          </h2>
          <p className="mb-8 text-sm text-[var(--text-muted)]">
            스터디룸 계정으로 로그인하세요.
          </p>

          <form
            onSubmit={handleSubmit}
            noValidate
            className="flex flex-col gap-4"
          >
            <div>
              <label htmlFor="email" className="label">
                이메일
              </label>
              <input
                id="email"
                name="email"
                type="text"
                autoComplete="username"
                className="input"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
                aria-invalid={!!fieldErrors.email}
                aria-describedby={fieldErrors.email ? "email-error" : undefined}
              />
              {fieldErrors.email && (
                <p id="email-error" className="field-error">
                  {fieldErrors.email}
                </p>
              )}
            </div>

            <div>
              <label htmlFor="password" className="label">
                비밀번호
              </label>
              <input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                className="input"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                aria-invalid={!!fieldErrors.password}
                aria-describedby={
                  fieldErrors.password ? "password-error" : undefined
                }
              />
              {fieldErrors.password && (
                <p id="password-error" className="field-error">
                  {fieldErrors.password}
                </p>
              )}
            </div>

            {formError && (
              <p role="alert" className="field-error">
                {formError}
              </p>
            )}

            <button
              type="submit"
              className="btn btn-primary mt-2"
              disabled={loginMutation.isPending}
            >
              <LogIn size={16} aria-hidden />
              {loginMutation.isPending ? "로그인 중…" : "로그인"}
            </button>
          </form>

          <div className="mt-6 border-t border-[var(--border)] pt-6">
            <button
              type="button"
              className="btn btn-secondary w-full"
              onClick={handleDemoLogin}
              disabled={loginMutation.isPending}
            >
              회원가입 없이 둘러보기
            </button>
            <p className="mt-2 text-center text-xs text-[var(--text-muted)]">
              회원가입 없이 체험해 볼 수 있습니다.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}
