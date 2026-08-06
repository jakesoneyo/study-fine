/**
 * 설정 화면 (ORGANIZER 전용) — 벌금 단가 수정. SPEC O-5.
 * 변경은 이후 출석 체크부터만 적용되고 과거 기록은 불변임을 명시적으로 안내한다(벌금 스냅샷 원칙).
 */
import { useEffect, useState, type FormEvent } from "react";
import { Info } from "lucide-react";
import {
  useStudyRoomQuery,
  useUpdateStudyRoomMutation,
} from "../api/studyRoom";
import { StudyRoomUpdateFormSchema } from "../schemas/studyRoom";
import { SkeletonRows } from "../components/Skeleton";
import { EmptyState } from "../components/EmptyState";
import { useToast } from "../hooks/useToast";
import { extractErrorMessage } from "../api/errors";

export default function Settings() {
  const { data, isPending, isError } = useStudyRoomQuery();
  const updateMutation = useUpdateStudyRoomMutation();
  const { showError, showSuccess } = useToast();

  const [lateFineAmount, setLateFineAmount] = useState("");
  const [absentFineAmount, setAbsentFineAmount] = useState("");
  const [errors, setErrors] = useState<{
    lateFineAmount?: string;
    absentFineAmount?: string;
  }>({});

  useEffect(() => {
    if (!data) return;
    setLateFineAmount(String(data.lateFineAmount));
    setAbsentFineAmount(String(data.absentFineAmount));
  }, [data]);

  if (isPending) return <SkeletonRows rows={3} />;
  if (isError || !data)
    return <EmptyState title="설정을 불러오지 못했습니다" />;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const parsed = StudyRoomUpdateFormSchema.safeParse({
      lateFineAmount: Number(lateFineAmount),
      absentFineAmount: Number(absentFineAmount),
    });
    if (!parsed.success) {
      const next: typeof errors = {};
      for (const issue of parsed.error.issues) {
        if (issue.path[0] === "lateFineAmount")
          next.lateFineAmount = issue.message;
        if (issue.path[0] === "absentFineAmount")
          next.absentFineAmount = issue.message;
      }
      setErrors(next);
      return;
    }
    setErrors({});
    try {
      await updateMutation.mutateAsync(parsed.data);
      showSuccess("벌금 단가를 수정했습니다");
    } catch (error) {
      showError(extractErrorMessage(error, "수정에 실패했습니다"));
    }
  }

  return (
    <div className="flex max-w-md flex-col gap-6">
      <h1 className="text-xl text-[var(--text-strong)]">설정 — 벌금 단가</h1>

      <div className="card flex items-start gap-3 p-4 text-sm text-[var(--text-muted)]">
        <Info
          size={18}
          aria-hidden
          className="mt-0.5 shrink-0 text-[var(--accent)]"
        />
        <p>
          변경된 단가는 이후 출석 체크부터 적용되며, 이미 기록된 벌금은 바뀌지
          않습니다.
        </p>
      </div>

      <form
        onSubmit={handleSubmit}
        noValidate
        className="card flex flex-col gap-4 p-6"
      >
        <div>
          <label htmlFor="late-fine" className="label">
            지각 단가 (원)
          </label>
          <input
            id="late-fine"
            type="number"
            min={0}
            step={100}
            className="input"
            value={lateFineAmount}
            onChange={(e) => setLateFineAmount(e.target.value)}
          />
          {errors.lateFineAmount && (
            <p className="field-error">{errors.lateFineAmount}</p>
          )}
        </div>
        <div>
          <label htmlFor="absent-fine" className="label">
            결석 단가 (원)
          </label>
          <input
            id="absent-fine"
            type="number"
            min={0}
            step={100}
            className="input"
            value={absentFineAmount}
            onChange={(e) => setAbsentFineAmount(e.target.value)}
          />
          {errors.absentFineAmount && (
            <p className="field-error">{errors.absentFineAmount}</p>
          )}
        </div>
        <button
          type="submit"
          className="btn btn-primary mt-2 self-start"
          disabled={updateMutation.isPending}
        >
          {updateMutation.isPending ? "저장 중…" : "저장"}
        </button>
      </form>
    </div>
  );
}
