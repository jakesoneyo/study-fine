/**
 * 파괴적 작업(비활성화·삭제) 전 확인 다이얼로그. 네이티브 <dialog>로 포커스 트랩·ESC 닫기를
 * 브라우저에 위임한다(커스텀 모달 구현 대신 표준 기능 우선 — ponytail).
 */
import { useEffect, useRef } from "react";

interface ConfirmDialogProps {
  open: boolean;
  title: string;
  description: string;
  confirmLabel?: string;
  danger?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}

export function ConfirmDialog({
  open,
  title,
  description,
  confirmLabel = "확인",
  danger = false,
  onConfirm,
  onCancel,
}: ConfirmDialogProps) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);

  return (
    <dialog
      ref={ref}
      className="card w-full max-w-sm p-6 backdrop:bg-black/40"
      onCancel={onCancel}
      onClose={onCancel}
    >
      <h2 className="text-base font-semibold text-[var(--text-strong)]">
        {title}
      </h2>
      <p className="mt-2 text-sm text-[var(--text-muted)]">{description}</p>
      <div className="mt-6 flex justify-end gap-2">
        <button type="button" className="btn btn-secondary" onClick={onCancel}>
          취소
        </button>
        <button
          type="button"
          className={danger ? "btn btn-danger" : "btn btn-primary"}
          onClick={onConfirm}
        >
          {confirmLabel}
        </button>
      </div>
    </dialog>
  );
}
