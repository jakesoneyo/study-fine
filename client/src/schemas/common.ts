/**
 * 여러 도메인 스키마가 공유하는 값 — 역할·출석 상태 enum, 에러 응답(ProblemDetail) 형태.
 * API.md §0 에 정의된 RFC 9457 에러 포맷을 그대로 따른다.
 */
import { z } from "zod";

export const MemberRoleSchema = z.enum(["ORGANIZER", "MEMBER"]);
export type MemberRole = z.infer<typeof MemberRoleSchema>;

export const AttendanceStatusSchema = z.enum(["PRESENT", "LATE", "ABSENT"]);
export type AttendanceStatus = z.infer<typeof AttendanceStatusSchema>;

export const ProblemDetailSchema = z.object({
  type: z.string().optional(),
  title: z.string().optional(),
  status: z.number().optional(),
  detail: z.string().optional(),
  instance: z.string().optional(),
  errors: z
    .array(z.object({ field: z.string(), message: z.string() }))
    .optional(),
});
export type ProblemDetail = z.infer<typeof ProblemDetailSchema>;
