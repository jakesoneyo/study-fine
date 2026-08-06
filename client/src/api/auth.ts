/**
 * 인증 API 훅 — 로그인, 세션 복구(GET /api/auth/me). API.md #2, #3.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "./client";
import {
  AuthMemberSchema,
  LoginResponseSchema,
  type LoginRequest,
} from "../schemas/auth";
import { useAuthStore } from "../stores/authStore";

export function useLoginMutation() {
  const login = useAuthStore((state) => state.login);
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: LoginRequest) => {
      const { data } = await apiClient.post("/api/auth/login", request);
      return LoginResponseSchema.parse(data);
    },
    onSuccess: (data) => {
      login(data.accessToken, data.member);
      queryClient.clear();
    },
  });
}

/**
 * 새로고침 시 세션 복구용. 토큰이 있을 때만 호출하고, 401이면 axios 인터셉터가
 * 이미 로그아웃 + 로그인 리다이렉트를 처리하므로 이 훅은 로딩 상태만 신경 쓰면 된다.
 */
export function useMeQuery() {
  const token = useAuthStore((state) => state.token);
  return useQuery({
    queryKey: ["auth", "me"],
    queryFn: async () => {
      const { data } = await apiClient.get("/api/auth/me");
      return AuthMemberSchema.parse(data);
    },
    enabled: !!token,
    retry: false,
    staleTime: Infinity,
  });
}
