/**
 * 내 출석 내역 API 훅 (MEMBER 대시보드). API.md #15.
 * id 파라미터가 없는 전용 경로라 남의 데이터를 지칭할 방법 자체가 없다(ARCHITECTURE.md §4).
 */
import { useQuery } from "@tanstack/react-query";
import { apiClient } from "./client";
import { MyAttendancesResponseSchema } from "../schemas/attendance";

export function useMeAttendancesQuery() {
  return useQuery({
    queryKey: ["me", "attendances"],
    queryFn: async () => {
      const { data } = await apiClient.get("/api/me/attendances");
      return MyAttendancesResponseSchema.parse(data);
    },
  });
}
