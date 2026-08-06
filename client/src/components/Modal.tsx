/** 생성/수정 폼용 공용 모달. 네이티브 <dialog>로 포커스 트랩·ESC 닫기를 위임한다. */
import { useEffect, useRef, type ReactNode } from "react";

interface ModalProps {
  open: boolean;
  title: string;
  onClose: () => void;
  children: ReactNode;
}

export function Modal({ open, title, onClose, children }: ModalProps) {
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
      className="card w-full max-w-md p-6 backdrop:bg-black/40"
      onCancel={onClose}
      onClose={onClose}
      aria-label={title}
    >
      <h2 className="mb-4 text-base font-semibold text-[var(--text-strong)]">
        {title}
      </h2>
      {children}
    </dialog>
  );
}
