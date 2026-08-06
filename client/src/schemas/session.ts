/**
 * 회차(StudySession) 스키마 — 목록/생성/상세. API.md #10, #11, #12.
 */
import { z } from "zod";
import { AttendanceStatusSchema } from "./common";

export const SessionListItemSchema = z.object({
  id: z.number(),
  sessionDate: z.string(),
  title: z.string(),
  checkedIn: z.boolean(),
  totalFine: z.number(),
  presentCount: z.number(),
  lateCount: z.number(),
  absentCount: z.number(),
});
export type SessionListItem = z.infer<typeof SessionListItemSchema>;

export const SessionListSchema = z.array(SessionListItemSchema);

export const SessionCreateFormSchema = z.object({
  sessionDate: z.string().min(1, "날짜를 선택하세요"),
  title: z
    .string()
    .min(1, "제목을 입력하세요")
    .max(100, "100자 이내로 입력하세요"),
});
export type SessionCreateForm = z.infer<typeof SessionCreateFormSchema>;

export const SessionAttendanceItemSchema = z.object({
  memberId: z.number(),
  memberName: z.string(),
  status: AttendanceStatusSchema.nullable(),
  fineAmount: z.number(),
});
export type SessionAttendanceItem = z.infer<typeof SessionAttendanceItemSchema>;

export const SessionDetailSchema = z.object({
  id: z.number(),
  sessionDate: z.string(),
  title: z.string(),
  totalFine: z.number(),
  attendances: z.array(SessionAttendanceItemSchema),
});
export type SessionDetail = z.infer<typeof SessionDetailSchema>;
