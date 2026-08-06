/**
 * 출석 체크 화면 (ORGANIZER 전용, 이 프로젝트의 얼굴) — SPEC O-3, O-4.
 * 상태 선택 즉시 벌금을 미리보기 하되(단가는 GET study-room 값), 저장 후에는 서버 응답의
 * fineAmount 로 교체한다. 최종 진실은 항상 서버다(PLAN.md C5).
 */
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router";
import { CheckCheck } from "lucide-react";
import {
  useSessionDetailQuery,
  useSaveAttendancesMutation,
} from "../api/sessions";
import { useStudyRoomQuery } from "../api/studyRoom";
import type { AttendanceStatus } from "../schemas/common";
import { StatusBadge } from "../components/StatusBadge";
import { SkeletonRows } from "../components/Skeleton";
import { EmptyState } from "../components/EmptyState";
import { useToast } from "../hooks/useToast";
import { extractErrorMessage } from "../api/errors";
import { formatFine, formatSessionDate } from "../lib/format";

const STATUS_OPTIONS: { value: AttendanceStatus; label: string }[] = [
  { value: "PRESENT", label: "정상" },
  { value: "LATE", label: "지각" },
  { value: "ABSENT", label: "결석" },
];

export default function SessionCheckIn() {
  const params = useParams<{ id: string }>();
  const sessionId = Number(params.id);
  const navigate = useNavigate();
  const { showError, showSuccess } = useToast();

  const sessionQuery = useSessionDetailQuery(sessionId);
  const roomQuery = useStudyRoomQuery();
  const saveMutation = useSaveAttendancesMutation(sessionId);

  const [statusMap, setStatusMap] = useState<Record<number, AttendanceStatus>>(
    {},
  );

  // 최초 로드 시에만 서버 상태로 초기화한다. 저장 후 재조회되더라도 이미 선택된 값을
  // 덮어쓰지 않아야 "저장 → 화면에 그대로 남아있음"이 자연스럽다.
  useEffect(() => {
    if (!sessionQuery.data) return;
    setStatusMap((prev) => {
      if (Object.keys(prev).length > 0) return prev;
      const initial: Record<number, AttendanceStatus> = {};
      for (const attendance of sessionQuery.data.attendances) {
        initial[attendance.memberId] = attendance.status ?? "PRESENT";
      }
      return initial;
    });
  }, [sessionQuery.data]);

  if (!Number.isFinite(sessionId)) {
    return <EmptyState title="잘못된 회차입니다" />;
  }
  if (sessionQuery.isPending || roomQuery.isPending) {
    return <SkeletonRows rows={5} />;
  }
  if (sessionQuery.isError || !sessionQuery.data) {
    return (
      <EmptyState
        title="회차를 찾을 수 없습니다"
        description="목록으로 돌아가 다시 시도하세요"
      />
    );
  }

  const session = sessionQuery.data;
  const room = roomQuery.data;

  /** 이미 저장된 상태와 로컬 선택이 같으면 서버가 확정한 fineAmount를, 다르면(=미저장 변경) 현재 단가로 계산한 미리보기를 쓴다. */
  function fineFor(memberId: number, status: AttendanceStatus): number {
    const saved = session.attendances.find((a) => a.memberId === memberId);
    if (saved && saved.status === status) return saved.fineAmount;
    if (!room) return 0;
    if (status === "PRESENT") return 0;
    return status === "LATE" ? room.lateFineAmount : room.absentFineAmount;
  }

  const previewTotal = session.attendances.reduce(
    (sum, a) => sum + fineFor(a.memberId, statusMap[a.memberId] ?? "PRESENT"),
    0,
  );

  function markAllPresent() {
    const next: Record<number, AttendanceStatus> = {};
    for (const attendance of session.attendances)
      next[attendance.memberId] = "PRESENT";
    setStatusMap(next);
  }

  async function handleSave() {
    try {
      await saveMutation.mutateAsync({
        attendances: session.attendances.map((attendance) => ({
          memberId: attendance.memberId,
          status: statusMap[attendance.memberId] ?? "PRESENT",
        })),
      });
      showSuccess("출석을 저장했습니다");
    } catch (error) {
      showError(extractErrorMessage(error, "저장에 실패했습니다"));
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <div>
          <button
            type="button"
            className="text-sm text-[var(--text-muted)]"
            onClick={() => navigate("/sessions")}
          >
            ← 회차 목록
          </button>
          <h1 className="mt-1 text-xl text-[var(--text-strong)]">{session.title}</h1>
          <p className="text-sm text-[var(--text-muted)]">
            {formatSessionDate(session.sessionDate)}
          </p>
        </div>
        <button
          type="button"
          className="btn btn-secondary"
          onClick={markAllPresent}
        >
          <CheckCheck size={16} aria-hidden />
          전원 정상
        </button>
      </div>

      <div className="card grid grid-cols-1 gap-4 p-6 sm:grid-cols-[7fr_5fr]">
        <div>
          <p className="text-sm font-medium text-[var(--text-muted)]">
            벌금 기준
          </p>
          <p className="mt-1 text-sm text-[var(--text-strong)]">
            지각 {room ? formatFine(room.lateFineAmount) : "-"} · 결석{" "}
            {room ? formatFine(room.absentFineAmount) : "-"}
          </p>
        </div>
        <div className="sm:text-right">
          <p className="text-sm font-medium text-[var(--text-muted)]">
            예상 정산 합계
          </p>
          <p className="mt-1 text-2xl font-bold text-[var(--accent)]">
            {formatFine(previewTotal)}
          </p>
        </div>
      </div>

      <div className="card divide-y divide-[var(--border)]">
        {session.attendances.map((attendance) => {
          const current = statusMap[attendance.memberId] ?? "PRESENT";
          return (
            <div
              key={attendance.memberId}
              className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-center sm:justify-between"
            >
              <div className="flex items-center gap-3">
                <span className="text-sm font-medium text-[var(--text-strong)]">
                  {attendance.memberName}
                </span>
                <StatusBadge status={current} />
              </div>
              <div className="flex items-center gap-4">
                <div
                  className="flex gap-2"
                  role="group"
                  aria-label={`${attendance.memberName} 출석 상태 선택`}
                >
                  {STATUS_OPTIONS.map((option) => (
                    <button
                      key={option.value}
                      type="button"
                      className="toggle-btn"
                      aria-pressed={current === option.value}
                      onClick={() =>
                        setStatusMap((prev) => ({
                          ...prev,
                          [attendance.memberId]: option.value,
                        }))
                      }
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
                <span className="w-20 text-right text-sm font-semibold text-[var(--text-strong)]">
                  {formatFine(fineFor(attendance.memberId, current))}
                </span>
              </div>
            </div>
          );
        })}
      </div>

      <div className="flex justify-end">
        <button
          type="button"
          className="btn btn-primary"
          onClick={handleSave}
          disabled={saveMutation.isPending}
        >
          {saveMutation.isPending ? "저장 중…" : "출석 저장"}
        </button>
      </div>
    </div>
  );
}
