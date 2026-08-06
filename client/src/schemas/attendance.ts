/**
 * 출석 체크 저장 요청 + 내 출석 내역 응답 스키마. API.md #14, #15.
 */
import { z } from "zod";
import { AttendanceStatusSchema, MemberRoleSchema } from "./common";

/** PUT 요청 바디 — fineAmount 는 절대 포함하지 않는다 (서버가 단가로 계산, 클라 조작 방지). */
export const AttendanceCheckInRequestSchema = z.object({
  attendances: z.array(
    z.object({
      memberId: z.number(),
      status: AttendanceStatusSchema,
    }),
  ),
});
export type AttendanceCheckInRequest = z.infer<
  typeof AttendanceCheckInRequestSchema
>;

export const MyAttendanceRecordSchema = z.object({
  sessionId: z.number(),
  sessionDate: z.string(),
  sessionTitle: z.string(),
  status: AttendanceStatusSchema,
  fineAmount: z.number(),
});
export type MyAttendanceRecord = z.infer<typeof MyAttendanceRecordSchema>;

export const MyAttendancesResponseSchema = z.object({
  member: z.object({
    id: z.number(),
    name: z.string(),
    role: MemberRoleSchema,
  }),
  accumulatedFine: z.number(),
  presentCount: z.number(),
  lateCount: z.number(),
  absentCount: z.number(),
  records: z.array(MyAttendanceRecordSchema),
});
export type MyAttendancesResponse = z.infer<typeof MyAttendancesResponseSchema>;
