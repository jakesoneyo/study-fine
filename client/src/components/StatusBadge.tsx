/**
 * 출석 상태 배지 — 색만으로 구분하지 않도록 아이콘 + 한글 텍스트를 항상 함께 표시한다
 * (PLAN.md C5 접근성 요구사항).
 */
import { CheckCircle2, Clock, XCircle } from "lucide-react";
import type { AttendanceStatus } from "../schemas/common";

const STATUS_META: Record<
  AttendanceStatus,
  { label: string; icon: typeof CheckCircle2; className: string }
> = {
  PRESENT: {
    label: "정상",
    icon: CheckCircle2,
    className:
      "bg-[var(--status-present-bg)] text-[var(--status-present-text)] border-[var(--status-present-border)]",
  },
  LATE: {
    label: "지각",
    icon: Clock,
    className:
      "bg-[var(--status-late-bg)] text-[var(--status-late-text)] border-[var(--status-late-border)]",
  },
  ABSENT: {
    label: "결석",
    icon: XCircle,
    className:
      "bg-[var(--status-absent-bg)] text-[var(--status-absent-text)] border-[var(--status-absent-border)]",
  },
};

export function StatusBadge({ status }: { status: AttendanceStatus | null }) {
  if (!status) {
    return (
      <span className="inline-flex items-center gap-1 rounded-full border border-[var(--border)] px-3 py-1 text-xs font-medium text-[var(--text-muted)]">
        미기록
      </span>
    );
  }
  const meta = STATUS_META[status];
  const Icon = meta.icon;
  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full border px-3 py-1 text-xs font-medium ${meta.className}`}
    >
      <Icon size={14} aria-hidden />
      {meta.label}
    </span>
  );
}
