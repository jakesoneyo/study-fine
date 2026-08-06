/**
 * 회차 목록 화면 (ORGANIZER 전용) — 생성/삭제, 출석 체크 화면으로 이동. SPEC O-2, O-3.
 */
import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { Plus, Trash2 } from "lucide-react";
import {
  useCreateSessionMutation,
  useDeleteSessionMutation,
  useSessionsQuery,
} from "../api/sessions";
import {
  SessionCreateFormSchema,
  type SessionListItem,
} from "../schemas/session";
import { Modal } from "../components/Modal";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { SkeletonRows } from "../components/Skeleton";
import { EmptyState } from "../components/EmptyState";
import { useToast } from "../hooks/useToast";
import { extractErrorMessage } from "../api/errors";
import { formatFine, formatSessionDate } from "../lib/format";

export default function Sessions() {
  const { data, isPending, isError } = useSessionsQuery();
  const [createOpen, setCreateOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<SessionListItem | null>(
    null,
  );
  const deleteMutation = useDeleteSessionMutation();
  const { showError, showSuccess } = useToast();

  async function handleDelete() {
    if (!deleteTarget) return;
    try {
      await deleteMutation.mutateAsync(deleteTarget.id);
      showSuccess("회차를 삭제했습니다");
    } catch (error) {
      showError(extractErrorMessage(error, "삭제에 실패했습니다"));
    } finally {
      setDeleteTarget(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl text-[var(--text-strong)]">회차 관리</h1>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => setCreateOpen(true)}
        >
          <Plus size={16} aria-hidden />
          회차 만들기
        </button>
      </div>

      {isPending && <SkeletonRows rows={4} />}
      {isError && <EmptyState title="회차 목록을 불러오지 못했습니다" />}
      {data && data.length === 0 && (
        <EmptyState
          title="아직 생성된 회차가 없습니다"
          description="회차 만들기 버튼으로 첫 회차를 등록하세요"
        />
      )}

      {data && data.length > 0 && (
        <div className="card divide-y divide-[var(--border)]">
          {data.map((session) => (
            <div
              key={session.id}
              className="flex items-center justify-between gap-4 px-5 py-4"
            >
              <Link to={`/sessions/${session.id}`} className="flex-1">
                <p className="text-sm font-medium text-[var(--text-strong)]">
                  {session.title}
                </p>
                <p className="text-xs text-[var(--text-muted)]">
                  {formatSessionDate(session.sessionDate)}
                </p>
              </Link>
              <div className="flex items-center gap-4">
                {!session.checkedIn && (
                  <span className="inline-flex rounded-full border border-[var(--status-late-border)] bg-[var(--status-late-bg)] px-2.5 py-0.5 text-xs font-medium text-[var(--status-late-text)]">
                    미체크
                  </span>
                )}
                <span className="w-24 text-right text-sm font-semibold text-[var(--text-strong)]">
                  {formatFine(session.totalFine)}
                </span>
                <button
                  type="button"
                  className="btn btn-ghost"
                  aria-label={`${session.title} 삭제`}
                  onClick={() => setDeleteTarget(session)}
                >
                  <Trash2 size={16} aria-hidden />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      <CreateSessionModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
      />
      <ConfirmDialog
        open={!!deleteTarget}
        title="회차 삭제"
        description={`"${deleteTarget?.title ?? ""}" 회차를 삭제하시겠습니까? 이 회차의 출석 기록도 함께 삭제됩니다.`}
        confirmLabel="삭제"
        danger
        onConfirm={handleDelete}
        onCancel={() => setDeleteTarget(null)}
      />
    </div>
  );
}

function CreateSessionModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const createMutation = useCreateSessionMutation();
  const { showError, showSuccess } = useToast();
  const [sessionDate, setSessionDate] = useState("");
  const [title, setTitle] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});

  function reset() {
    setSessionDate("");
    setTitle("");
    setErrors({});
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const parsed = SessionCreateFormSchema.safeParse({ sessionDate, title });
    if (!parsed.success) {
      const next: Record<string, string> = {};
      for (const issue of parsed.error.issues)
        next[String(issue.path[0])] = issue.message;
      setErrors(next);
      return;
    }
    setErrors({});
    try {
      await createMutation.mutateAsync(parsed.data);
      showSuccess("회차를 생성했습니다");
      reset();
      onClose();
    } catch (error) {
      showError(extractErrorMessage(error, "회차 생성에 실패했습니다"));
    }
  }

  return (
    <Modal
      open={open}
      title="회차 만들기"
      onClose={() => {
        reset();
        onClose();
      }}
    >
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
        <div>
          <label htmlFor="session-date" className="label">
            날짜
          </label>
          <input
            id="session-date"
            type="date"
            className="input"
            value={sessionDate}
            onChange={(e) => setSessionDate(e.target.value)}
          />
          {errors.sessionDate && (
            <p className="field-error">{errors.sessionDate}</p>
          )}
        </div>
        <div>
          <label htmlFor="session-title" className="label">
            제목
          </label>
          <input
            id="session-title"
            className="input"
            placeholder="예: 4주차 — DB 인덱스"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          {errors.title && <p className="field-error">{errors.title}</p>}
        </div>
        <div className="mt-2 flex justify-end gap-2">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => {
              reset();
              onClose();
            }}
          >
            취소
          </button>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={createMutation.isPending}
          >
            생성
          </button>
        </div>
      </form>
    </Modal>
  );
}
