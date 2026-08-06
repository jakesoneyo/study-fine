/**
 * 스터디룸(벌금 단가) 조회/수정 스키마. API.md #4, #5.
 */
import { z } from "zod";

export const StudyRoomSchema = z.object({
  id: z.number(),
  name: z.string(),
  lateFineAmount: z.number(),
  absentFineAmount: z.number(),
});
export type StudyRoom = z.infer<typeof StudyRoomSchema>;

/** 설정 화면 폼 검증 — 0 이상 정수, 서버 `@Max(1000000)` 과 동일 상한. */
export const StudyRoomUpdateFormSchema = z.object({
  lateFineAmount: z
    .number({ error: "숫자를 입력하세요" })
    .int("정수만 입력 가능합니다")
    .min(0, "0 이상이어야 합니다")
    .max(1_000_000, "100만원을 넘을 수 없습니다"),
  absentFineAmount: z
    .number({ error: "숫자를 입력하세요" })
    .int("정수만 입력 가능합니다")
    .min(0, "0 이상이어야 합니다")
    .max(1_000_000, "100만원을 넘을 수 없습니다"),
});
export type StudyRoomUpdateForm = z.infer<typeof StudyRoomUpdateFormSchema>;
