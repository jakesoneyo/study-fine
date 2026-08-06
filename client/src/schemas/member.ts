/**
 * 멤버 관리 스키마 — 목록/생성/수정. API.md #6, #7, #8.
 */
import { z } from "zod";
import { MemberRoleSchema } from "./common";

export const MemberSummarySchema = z.object({
  id: z.number(),
  name: z.string(),
  email: z.string(),
  role: MemberRoleSchema,
  active: z.boolean(),
  accumulatedFine: z.number(),
  lateCount: z.number(),
  absentCount: z.number(),
});
export type MemberSummary = z.infer<typeof MemberSummarySchema>;

export const MemberListSchema = z.array(MemberSummarySchema);

export const MemberCreateFormSchema = z.object({
  name: z
    .string()
    .min(1, "이름을 입력하세요")
    .max(50, "50자 이내로 입력하세요"),
  email: z.email("올바른 이메일 형식이 아닙니다").max(255),
  password: z
    .string()
    .min(8, "8자 이상 입력하세요")
    .max(100, "100자 이내로 입력하세요"),
  role: MemberRoleSchema,
});
export type MemberCreateForm = z.infer<typeof MemberCreateFormSchema>;

export const MemberUpdateRequestSchema = z.object({
  name: z.string().min(1).max(50).optional(),
  role: MemberRoleSchema.optional(),
  active: z.boolean().optional(),
});
export type MemberUpdateRequest = z.infer<typeof MemberUpdateRequestSchema>;
