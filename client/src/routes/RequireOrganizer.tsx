/**
 * 운영자 전용 라우트 가드 — MEMBER 로그인 상태에서는 운영자 메뉴/화면 자체가 렌더링되지 않는다.
 * UX 편의일 뿐 실제 보안 경계는 서버 @PreAuthorize 다 (ARCHITECTURE.md §7).
 */
import { Navigate, Outlet } from "react-router";
import { useAuthStore } from "../stores/authStore";

export function RequireOrganizer() {
  const member = useAuthStore((state) => state.member);

  if (member?.role !== "ORGANIZER") {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
