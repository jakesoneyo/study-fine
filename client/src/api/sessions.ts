/**
 * 회차 + 출석 체크 API 훅. API.md #10, #11, #12, #13, #14.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "./client";
import {
  SessionDetailSchema,
  SessionListItemSchema,
  SessionListSchema,
  type SessionCreateForm,
} from "../schemas/session";
import type { AttendanceCheckInRequest } from "../schemas/attendance";

export const sessionsKey = ["sessions"] as const;
export function sessionDetailKey(id: number) {
  return ["sessions", id] as const;
}

export function useSessionsQuery() {
  return useQuery({
    queryKey: sessionsKey,
    queryFn: async () => {
      const { data } = await apiClient.get("/api/sessions");
      return SessionListSchema.parse(data);
    },
  });
}

export function useCreateSessionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: SessionCreateForm) => {
      const { data } = await apiClient.post("/api/sessions", request);
      return SessionListItemSchema.parse(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: sessionsKey });
    },
  });
}

export function useDeleteSessionMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`/api/sessions/${id}`);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: sessionsKey });
    },
  });
}

export function useSessionDetailQuery(id: number) {
  return useQuery({
    queryKey: sessionDetailKey(id),
    queryFn: async () => {
      const { data } = await apiClient.get(`/api/sessions/${id}`);
      return SessionDetailSchema.parse(data);
    },
  });
}

export function useSaveAttendancesMutation(id: number) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: AttendanceCheckInRequest) => {
      const { data } = await apiClient.put(
        `/api/sessions/${id}/attendances`,
        request,
      );
      return SessionDetailSchema.parse(data);
    },
    onSuccess: () => {
      // 저장 결과가 목록의 벌금 합계·체크 배지·멤버 누적 벌금에도 영향을 준다.
      queryClient.invalidateQueries({ queryKey: sessionDetailKey(id) });
      queryClient.invalidateQueries({ queryKey: sessionsKey });
      queryClient.invalidateQueries({ queryKey: ["members"] });
    },
  });
}
