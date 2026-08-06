/** 금액·날짜 표시 포맷 — 화면 전체에서 동일한 표기를 쓰기 위한 공용 헬퍼. */

export function formatFine(amount: number): string {
  return `${amount.toLocaleString("ko-KR")}원`;
}

export function formatSessionDate(isoDate: string): string {
  const date = new Date(`${isoDate}T00:00:00`);
  return date.toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "long",
    day: "numeric",
    weekday: "short",
  });
}
