/**
 * 인증 상태 저장소 (Zustand) — 토큰과 현재 로그인 멤버만 보관한다.
 * 서버에서 온 도메인 데이터(멤버 목록, 회차 등)는 절대 여기 두지 않는다 — TanStack Query가 소유.
 * localStorage로 영속화해 새로고침 후에도 로그인이 유지되게 한다.
 */
import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { AuthMember } from "../schemas/auth";

interface AuthState {
  token: string | null;
  member: AuthMember | null;
  login: (token: string, member: AuthMember) => void;
  logout: () => void;
  /** /api/auth/me 로 세션을 복구할 때 최신 멤버 정보만 갱신(토큰은 그대로 둔다). */
  setMember: (member: AuthMember) => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      member: null,
      login: (token, member) => set({ token, member }),
      logout: () => set({ token: null, member: null }),
      setMember: (member) => set({ member }),
    }),
    { name: "study-fine-auth" },
  ),
);

/** axios 인터셉터 등 React 훅 밖에서 최신 토큰이 필요할 때 사용 (컴포넌트 리렌더 유발 안 함). */
export function getStoredToken(): string | null {
  return useAuthStore.getState().token;
}
