/**
 * axios 에러에서 서버의 ProblemDetail(RFC 9457) title/detail을 뽑아 사용자에게 보여줄
 * 한 줄 메시지로 변환한다. 백엔드 에러 포맷 계약은 API.md §0 참고.
 */
import { isAxiosError } from "axios";
import { ProblemDetailSchema } from "../schemas/common";

export function extractErrorMessage(
  error: unknown,
  fallback = "요청 처리 중 오류가 발생했습니다",
): string {
  if (isAxiosError(error)) {
    const parsed = ProblemDetailSchema.safeParse(error.response?.data);
    if (parsed.success) {
      return parsed.data.detail ?? parsed.data.title ?? fallback;
    }
  }
  return fallback;
}
