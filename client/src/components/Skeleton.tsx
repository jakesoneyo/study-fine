/** 로딩 상태 공용 스켈레톤. prefers-reduced-motion에서는 pulse 애니메이션이 자동 제거된다(index.css). */
export function Skeleton({ className = "" }: { className?: string }) {
  return (
    <div
      className={`animate-pulse rounded-[10px] bg-[var(--skeleton)] ${className}`}
      aria-hidden
    />
  );
}

export function SkeletonRows({ rows = 3 }: { rows?: number }) {
  return (
    <div className="flex flex-col gap-2" role="status" aria-label="불러오는 중">
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} className="h-12 w-full" />
      ))}
    </div>
  );
}
