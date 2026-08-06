/** 토스트 컨텍스트 정의 + 훅. Provider 컴포넌트는 components/ToastProvider.tsx. */
import { createContext, useContext } from "react";

export interface ToastContextValue {
  showError: (message: string) => void;
  showSuccess: (message: string) => void;
}

export const ToastContext = createContext<ToastContextValue | null>(null);

export function useToast(): ToastContextValue {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast는 ToastProvider 내부에서만 사용할 수 있습니다");
  }
  return context;
}
