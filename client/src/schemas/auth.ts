/**
 * 인증 관련 Zod 스키마 — 로그인 요청 폼 검증 + 로그인/me 응답 파싱.
 * 서버의 `@LoginEmail` 과 동일하게, 로그인 폼에서만 `admin` 리터럴이 이메일 형식 검증을 우회한다
 * (CLAUDE.md 데모 계정 규정 — 이 예외는 로그인 스키마 밖으로 절대 새어나가지 않는다).
 */
import { z } from "zod";
import { MemberRoleSchema } from "./common";

export const LoginRequestSchema = z.object({
  email: z
    .string()
    .min(1, "이메일을 입력하세요")
    .refine(
      (value) => value === "admin" || z.email().safeParse(value).success,
      {
        message: "올바른 이메일 형식이 아닙니다",
      },
    ),
  password: z.string().min(1, "비밀번호를 입력하세요"),
});
export type LoginRequest = z.infer<typeof LoginRequestSchema>;

export const AuthMemberSchema = z.object({
  id: z.number(),
  name: z.string(),
  email: z.string(),
  role: MemberRoleSchema,
});
export type AuthMember = z.infer<typeof AuthMemberSchema>;

export const LoginResponseSchema = z.object({
  accessToken: z.string(),
  expiresIn: z.number(),
  member: AuthMemberSchema,
});
export type LoginResponse = z.infer<typeof LoginResponseSchema>;
