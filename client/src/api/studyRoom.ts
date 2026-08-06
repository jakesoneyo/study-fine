/**
 * 스터디룸(벌금 단가) API 훅. API.md #4, #5.
 */
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { apiClient } from "./client";
import {
  StudyRoomSchema,
  type StudyRoomUpdateForm,
} from "../schemas/studyRoom";

export const studyRoomKey = ["study-room"] as const;

export function useStudyRoomQuery() {
  return useQuery({
    queryKey: studyRoomKey,
    queryFn: async () => {
      const { data } = await apiClient.get("/api/study-room");
      return StudyRoomSchema.parse(data);
    },
  });
}

export function useUpdateStudyRoomMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (request: Partial<StudyRoomUpdateForm>) => {
      const { data } = await apiClient.patch("/api/study-room", request);
      return StudyRoomSchema.parse(data);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: studyRoomKey });
    },
  });
}
