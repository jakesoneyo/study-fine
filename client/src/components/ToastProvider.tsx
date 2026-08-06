/**
 * 전역 에러/성공 토스트. mutation onError에서 서버 ProblemDetail.title을 그대로 띄우는 용도.
 * 화면 우하단에 쌓이고 4초 후 자동 소멸.
 */
import { useCallback, useState, type ReactNode } from "react";
import { AlertCircle, CheckCircle2 } from "lucide-react";
import { ToastContext, type ToastContextValue } from "../hooks/useToast";

interface ToastItem {
  id: number;
  message: string;
  variant: "error" | "success";
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const push = useCallback((message: string, variant: ToastItem["variant"]) => {
    const id = Date.now() + Math.random();
    setToasts((prev) => [...prev, { id, message, variant }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((toast) => toast.id !== id));
    }, 4000);
  }, []);

  const value: ToastContextValue = {
    showError: (message) => push(message, "error"),
    showSuccess: (message) => push(message, "success"),
  };

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="fixed bottom-6 right-6 z-50 flex w-full max-w-sm flex-col gap-2"
        role="region"
        aria-live="polite"
        aria-label="알림"
      >
        {toasts.map((toast) => (
          <div
            key={toast.id}
            role={toast.variant === "error" ? "alert" : "status"}
            className={`flex items-start gap-2 rounded-[14px] border px-4 py-3 text-sm shadow-lg backdrop-blur ${
              toast.variant === "error"
                ? "border-[var(--status-absent-border)] bg-[var(--status-absent-bg)] text-[var(--status-absent-text)]"
                : "border-[var(--status-present-border)] bg-[var(--status-present-bg)] text-[var(--status-present-text)]"
            }`}
          >
            {toast.variant === "error" ? (
              <AlertCircle size={18} aria-hidden className="mt-0.5 shrink-0" />
            ) : (
              <CheckCircle2 size={18} aria-hidden className="mt-0.5 shrink-0" />
            )}
            <span>{toast.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
