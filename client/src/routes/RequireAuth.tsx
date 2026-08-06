/**
 * 로그인 가드 — 토큰이 없으면 로그인 페이지로 보낸다.
 * 토큰이 위조/만료된 경우는 axios 401 인터셉터가 처리하므로 여기서는 존재 여부만 본다.
 */
import { Navigate, Outlet, useLocation } from "react-router";
import { useAuthStore } from "../stores/authStore";

export function RequireAuth() {
  const token = useAuthStore((state) => state.token);
  const location = useLocation();

  if (!token) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }
  return <Outlet />;
}
