/** 목록이 비어 있을 때 안내 문구 + 선택적 액션 버튼을 보여주는 공용 컴포넌트. */
import type { ReactNode } from "react";

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description?: string;
  action?: ReactNode;
}) {
  return (
    <div className="flex flex-col items-center gap-3 rounded-[14px] border border-dashed border-[var(--border)] px-6 py-12 text-center">
      <p className="text-sm font-medium text-[var(--text-strong)]">{title}</p>
      {description && (
        <p className="text-sm text-[var(--text-muted)]">{description}</p>
      )}
      {action}
    </div>
  );
}
