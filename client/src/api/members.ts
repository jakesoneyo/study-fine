/**
 * 멤버 관리 API 훅 — 목록/생성/수정. API.md #6, #7, #8.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "./client";
import {
  MemberListSchema,
  MemberSummarySchema,
  type MemberCreateForm,
  type MemberUpdateRequest,
} from "../schemas/member";

export function membersKey(includeInactive: boolean) {
  return ["members", { includeInactive }] as const;
}

export function useMembersQuery(includeInactive: boolean) {
  return useQuery({
    queryKey: membersKey(includeInactive),
    queryFn: async () => {
      const { data } = await apiClient.get("/api/members", {
        params: { includeInactive },
      });
      return MemberListSchema.parse(data);
    },
  });
}

export function useCreateMemberMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: MemberCreateForm) => {
      const { data } = await apiClient.post("/api/members", request);
      return MemberSummarySchema.parse(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["members"] });
    },
  });
}

export function useUpdateMemberMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      id,
      request,
    }: {
      id: number;
      request: MemberUpdateRequest;
    }) => {
      const { data } = await apiClient.patch(`/api/members/${id}`, request);
      return MemberSummarySchema.parse(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["members"] });
    },
  });
}
