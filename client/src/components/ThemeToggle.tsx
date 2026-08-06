/**
 * 라이트/다크 수동 전환 버튼. 기본은 시스템 설정(prefers-color-scheme)을 따르고,
 * 여기서 누르면 `<html data-theme>` 로 명시적으로 덮어써 localStorage에 기억한다.
 */
import { useEffect, useState } from "react";
import { Moon, Sun } from "lucide-react";

type Theme = "light" | "dark";

function getInitialTheme(): Theme {
  const stored = localStorage.getItem("study-fine-theme");
  if (stored === "light" || stored === "dark") return stored;
  return window.matchMedia("(prefers-color-scheme: dark)").matches
    ? "dark"
    : "light";
}

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(getInitialTheme);

  useEffect(() => {
    document.documentElement.setAttribute("data-theme", theme);
    localStorage.setItem("study-fine-theme", theme);
  }, [theme]);

  return (
    <button
      type="button"
      className="btn btn-ghost"
      aria-label={theme === "dark" ? "라이트 모드로 전환" : "다크 모드로 전환"}
      onClick={() => setTheme((prev) => (prev === "dark" ? "light" : "dark"))}
    >
      {theme === "dark" ? (
        <Sun size={16} aria-hidden />
      ) : (
        <Moon size={16} aria-hidden />
      )}
    </button>
  );
}
