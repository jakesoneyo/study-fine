/**
 * 멤버 관리 화면 (ORGANIZER 전용) — 목록/생성/수정/비활성화. SPEC O-1, O-6, O-7.
 */
import { useState, type FormEvent } from "react";
import { Plus, UserX } from "lucide-react";
import {
  useCreateMemberMutation,
  useMembersQuery,
  useUpdateMemberMutation,
} from "../api/members";
import { MemberCreateFormSchema, type MemberSummary } from "../schemas/member";
import { Modal } from "../components/Modal";
import { ConfirmDialog } from "../components/ConfirmDialog";
import { SkeletonRows } from "../components/Skeleton";
import { EmptyState } from "../components/EmptyState";
import { useToast } from "../hooks/useToast";
import { extractErrorMessage } from "../api/errors";
import { formatFine } from "../lib/format";

export default function Members() {
  const [includeInactive, setIncludeInactive] = useState(false);
  const { data, isPending, isError } = useMembersQuery(includeInactive);
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<MemberSummary | null>(null);
  const [deactivateTarget, setDeactivateTarget] =
    useState<MemberSummary | null>(null);
  const updateMutation = useUpdateMemberMutation();
  const { showError, showSuccess } = useToast();

  async function handleDeactivate() {
    if (!deactivateTarget) return;
    try {
      await updateMutation.mutateAsync({
        id: deactivateTarget.id,
        request: { active: false },
      });
      showSuccess(`${deactivateTarget.name}님을 비활성화했습니다`);
    } catch (error) {
      showError(extractErrorMessage(error, "비활성화에 실패했습니다"));
    } finally {
      setDeactivateTarget(null);
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl text-[var(--text-strong)]">멤버 관리</h1>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => setCreateOpen(true)}
        >
          <Plus size={16} aria-hidden />
          멤버 추가
        </button>
      </div>

      <label className="flex w-fit items-center gap-2 text-sm text-[var(--text-muted)]">
        <input
          type="checkbox"
          checked={includeInactive}
          onChange={(event) => setIncludeInactive(event.target.checked)}
        />
        비활성 멤버 포함
      </label>

      {isPending && <SkeletonRows rows={4} />}
      {isError && <EmptyState title="멤버 목록을 불러오지 못했습니다" />}
      {data && data.length === 0 && (
        <EmptyState
          title="등록된 멤버가 없습니다"
          description="멤버 추가 버튼으로 첫 멤버를 등록하세요"
        />
      )}

      {data && data.length > 0 && (
        <div className="card overflow-x-auto">
          <table className="w-full min-w-[640px] text-left text-sm">
            <thead className="border-b border-[var(--border)] text-xs text-[var(--text-muted)]">
              <tr>
                <th className="px-5 py-3 font-medium">이름</th>
                <th className="px-5 py-3 font-medium">이메일</th>
                <th className="px-5 py-3 font-medium">역할</th>
                <th className="px-5 py-3 font-medium">지각/결석</th>
                <th className="px-5 py-3 font-medium">누적 벌금</th>
                <th className="px-5 py-3 font-medium">상태</th>
                <th className="px-5 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-[var(--border)]">
              {data.map((member) => (
                <tr
                  key={member.id}
                  className={member.active ? "" : "opacity-60"}
                >
                  <td className="px-5 py-3 font-medium text-[var(--text-strong)]">
                    {member.name}
                  </td>
                  <td className="px-5 py-3 text-[var(--text-muted)]">
                    {member.email}
                  </td>
                  <td className="px-5 py-3">
                    {member.role === "ORGANIZER" ? "운영자" : "멤버"}
                  </td>
                  <td className="px-5 py-3 text-[var(--text-muted)]">
                    {member.lateCount} / {member.absentCount}
                  </td>
                  <td className="px-5 py-3 font-semibold text-[var(--text-strong)]">
                    {formatFine(member.accumulatedFine)}
                  </td>
                  <td className="px-5 py-3">
                    <span
                      className={`inline-flex rounded-full border px-2.5 py-0.5 text-xs font-medium ${
                        member.active
                          ? "border-[var(--status-present-border)] bg-[var(--status-present-bg)] text-[var(--status-present-text)]"
                          : "border-[var(--border)] bg-[var(--surface-muted)] text-[var(--text-muted)]"
                      }`}
                    >
                      {member.active ? "활성" : "비활성"}
                    </span>
                  </td>
                  <td className="px-5 py-3 text-right">
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        className="btn btn-ghost"
                        onClick={() => setEditTarget(member)}
                      >
                        수정
                      </button>
                      {member.active && (
                        <button
                          type="button"
                          className="btn btn-ghost"
                          aria-label={`${member.name} 비활성화`}
                          onClick={() => setDeactivateTarget(member)}
                        >
                          <UserX size={16} aria-hidden />
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <CreateMemberModal
        open={createOpen}
        onClose={() => setCreateOpen(false)}
      />
      <EditMemberModal
        member={editTarget}
        onClose={() => setEditTarget(null)}
      />
      <ConfirmDialog
        open={!!deactivateTarget}
        title="멤버 비활성화"
        description={`${deactivateTarget?.name ?? ""}님을 비활성화하시겠습니까? 과거 출석·벌금 기록은 그대로 유지됩니다.`}
        confirmLabel="비활성화"
        danger
        onConfirm={handleDeactivate}
        onCancel={() => setDeactivateTarget(null)}
      />
    </div>
  );
}

function CreateMemberModal({
  open,
  onClose,
}: {
  open: boolean;
  onClose: () => void;
}) {
  const createMutation = useCreateMemberMutation();
  const { showError, showSuccess } = useToast();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [role, setRole] = useState<"MEMBER" | "ORGANIZER">("MEMBER");
  const [errors, setErrors] = useState<Record<string, string>>({});

  function reset() {
    setName("");
    setEmail("");
    setPassword("");
    setRole("MEMBER");
    setErrors({});
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const parsed = MemberCreateFormSchema.safeParse({
      name,
      email,
      password,
      role,
    });
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
      showSuccess("멤버를 추가했습니다");
      reset();
      onClose();
    } catch (error) {
      showError(extractErrorMessage(error, "멤버 추가에 실패했습니다"));
    }
  }

  return (
    <Modal
      open={open}
      title="멤버 추가"
      onClose={() => {
        reset();
        onClose();
      }}
    >
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
        <div>
          <label htmlFor="member-name" className="label">
            이름
          </label>
          <input
            id="member-name"
            className="input"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          {errors.name && <p className="field-error">{errors.name}</p>}
        </div>
        <div>
          <label htmlFor="member-email" className="label">
            이메일
          </label>
          <input
            id="member-email"
            type="email"
            className="input"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          {errors.email && <p className="field-error">{errors.email}</p>}
        </div>
        <div>
          <label htmlFor="member-password" className="label">
            초기 비밀번호
          </label>
          <input
            id="member-password"
            type="password"
            className="input"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {errors.password && <p className="field-error">{errors.password}</p>}
        </div>
        <div>
          <label htmlFor="member-role" className="label">
            역할
          </label>
          <select
            id="member-role"
            className="input"
            value={role}
            onChange={(e) => setRole(e.target.value as "MEMBER" | "ORGANIZER")}
          >
            <option value="MEMBER">멤버</option>
            <option value="ORGANIZER">운영자</option>
          </select>
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
            추가
          </button>
        </div>
      </form>
    </Modal>
  );
}

function EditMemberModal({
  member,
  onClose,
}: {
  member: MemberSummary | null;
  onClose: () => void;
}) {
  const updateMutation = useUpdateMemberMutation();
  const { showError, showSuccess } = useToast();
  const [name, setName] = useState(member?.name ?? "");
  const [role, setRole] = useState<"MEMBER" | "ORGANIZER">(
    member?.role ?? "MEMBER",
  );

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!member) return;
    try {
      await updateMutation.mutateAsync({
        id: member.id,
        request: { name, role },
      });
      showSuccess("멤버 정보를 수정했습니다");
      onClose();
    } catch (error) {
      showError(extractErrorMessage(error, "수정에 실패했습니다"));
    }
  }

  return (
    <Modal
      key={member?.id ?? "none"}
      open={!!member}
      title="멤버 수정"
      onClose={onClose}
    >
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
        <div>
          <label htmlFor="edit-name" className="label">
            이름
          </label>
          <input
            id="edit-name"
            className="input"
            defaultValue={member?.name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>
        <div>
          <label htmlFor="edit-role" className="label">
            역할
          </label>
          <select
            id="edit-role"
            className="input"
            defaultValue={member?.role}
            onChange={(e) => setRole(e.target.value as "MEMBER" | "ORGANIZER")}
          >
            <option value="MEMBER">멤버</option>
            <option value="ORGANIZER">운영자</option>
          </select>
        </div>
        <div className="mt-2 flex justify-end gap-2">
          <button type="button" className="btn btn-secondary" onClick={onClose}>
            취소
          </button>
          <button
            type="submit"
            className="btn btn-primary"
            disabled={updateMutation.isPending}
          >
            저장
          </button>
        </div>
      </form>
    </Modal>
  );
}
