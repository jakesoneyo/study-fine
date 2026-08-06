/**
 * 로그인 후 공용 앱 셸 — 사이드바 내비게이션 + 헤더(현재 사용자·테마·로그아웃) + 페이지 콘텐츠.
 * 마운트 시 GET /api/auth/me 로 세션을 백그라운드 검증/갱신한다(새로고침 세션 복구).
 * MEMBER 로그인 시 운영자 메뉴 자체를 렌더링하지 않는다.
 */
import { useEffect } from "react";
import { NavLink, Outlet, useNavigate } from "react-router";
import {
  LayoutDashboard,
  LogOut,
  Settings2,
  Users,
  CalendarDays,
} from "lucide-react";
import { useAuthStore } from "../stores/authStore";
import { useMeQuery } from "../api/auth";
import { ThemeToggle } from "./ThemeToggle";

const NAV_ITEMS = [
  { to: "/", label: "대시보드", icon: LayoutDashboard, organizerOnly: false },
  { to: "/members", label: "멤버 관리", icon: Users, organizerOnly: true },
  {
    to: "/sessions",
    label: "회차 관리",
    icon: CalendarDays,
    organizerOnly: true,
  },
  { to: "/settings", label: "설정", icon: Settings2, organizerOnly: true },
] as const;

export function AppLayout() {
  const member = useAuthStore((state) => state.member);
  const logout = useAuthStore((state) => state.logout);
  const setMember = useAuthStore((state) => state.setMember);
  const navigate = useNavigate();
  const { data: freshMember } = useMeQuery();

  useEffect(() => {
    if (freshMember) setMember(freshMember);
  }, [freshMember, setMember]);

  const isOrganizer = member?.role === "ORGANIZER";

  function handleLogout() {
    logout();
    navigate("/login", { replace: true });
  }

  return (
    <div className="flex min-h-screen bg-[var(--bg)]">
      <aside className="hidden w-60 shrink-0 flex-col gap-1 border-r border-[var(--border)] bg-[var(--surface)] p-4 sm:flex">
        <div className="mb-4 px-2">
          <p className="text-sm font-bold text-[var(--accent)]">study-fine</p>
          <p className="text-xs text-[var(--text-muted)]">
            스터디 출석·벌금 관리
          </p>
        </div>
        <nav className="flex flex-1 flex-col gap-1" aria-label="주요 메뉴">
          {NAV_ITEMS.filter((item) => !item.organizerOnly || isOrganizer).map(
            (item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === "/"}
                className={({ isActive }) =>
                  `flex items-center gap-2 rounded-[10px] px-3 py-2 text-sm font-medium transition-colors ${
                    isActive
                      ? "bg-[var(--accent-bg)] text-[var(--accent)]"
                      : "text-[var(--text-muted)] hover:bg-[var(--surface-muted)] hover:text-[var(--text-strong)]"
                  }`
                }
              >
                <item.icon size={18} aria-hidden />
                {item.label}
              </NavLink>
            ),
          )}
        </nav>
      </aside>

      <div className="flex min-h-screen flex-1 flex-col">
        <header className="flex items-center justify-between border-b border-[var(--border)] bg-[var(--surface)] px-6 py-3">
          <div>
            <p className="text-sm font-semibold text-[var(--text-strong)]">
              {member?.name}
            </p>
            <p className="text-xs text-[var(--text-muted)]">
              {isOrganizer ? "운영자" : "멤버"}
            </p>
          </div>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <button
              type="button"
              className="btn btn-secondary"
              onClick={handleLogout}
            >
              <LogOut size={16} aria-hidden />
              로그아웃
            </button>
          </div>
        </header>
        <main className="flex-1 px-6 py-8">
          <div className="mx-auto flex w-full max-w-5xl flex-col gap-8">
            <Outlet />
          </div>
        </main>
      </div>
    </div>
  );
}
