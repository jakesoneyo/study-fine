/**
 * axios 인스턴스 — 모든 API 호출의 단일 출처.
 * 요청 인터셉터가 저장된 토큰을 Authorization 헤더에 싣고, 응답 인터셉터는 401을 받으면
 * 즉시 로그아웃 처리한다(토큰이 만료·위조됐다는 뜻이므로 더 쓸 수 없다).
 */
import axios from "axios";
import { getStoredToken, useAuthStore } from "../stores/authStore";

export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080",
});

apiClient.interceptors.request.use((config) => {
  const token = getStoredToken();
  if (token) {
    config.headers.set("Authorization", `Bearer ${token}`);
  }
  return config;
});

apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // 로그인 요청 자체의 401(자격증명 불일치)은 로그인 페이지가 폼 에러로 처리한다 —
      // 로그아웃 상태에서 로그인 시도 중이므로 이미 토큰이 없어 여기서 할 일이 없다.
      const isLoginRequest = error.config?.url?.includes("/api/auth/login");
      if (!isLoginRequest) {
        useAuthStore.getState().logout();
        if (window.location.pathname !== "/login") {
          window.location.href = "/login";
        }
      }
    }
    return Promise.reject(error);
  },
);
