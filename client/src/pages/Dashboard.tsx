/**
 * 대시보드 — 역할 분기(SPEC M-1, M-2 / O-6). 7:5 비대칭 그리드(DESIGN.md 시안 B).
 * MEMBER: 본인 누적 벌금 + 상태별 횟수 + 회차별 내 출석.
 * ORGANIZER: 전체 누적 벌금 합계 + 멤버 랭킹 + 최근 회차 요약.
 */
import { CalendarClock, Coins } from "lucide-react";
import { useAuthStore } from "../stores/authStore";
import { useMeAttendancesQuery } from "../api/meAttendances";
import { useMembersQuery } from "../api/members";
import { useSessionsQuery } from "../api/sessions";
import { useStudyRoomQuery } from "../api/studyRoom";
import { StatusBadge } from "../components/StatusBadge";
import { SkeletonRows } from "../components/Skeleton";
import { EmptyState } from "../components/EmptyState";
import { Reveal } from "../components/Reveal";
import { formatFine, formatSessionDate } from "../lib/format";

export default function Dashboard() {
  const member = useAuthStore((state) => state.member);
  return member?.role === "ORGANIZER" ? (
    <OrganizerDashboard />
  ) : (
    <MemberDashboard />
  );
}

function MemberDashboard() {
  const { data, isPending, isError } = useMeAttendancesQuery();

  if (isPending) return <SkeletonRows rows={5} />;
  if (isError || !data) {
    return (
      <EmptyState
        title="출석 내역을 불러오지 못했습니다"
        description="잠시 후 다시 시도해주세요"
      />
    );
  }

  return (
    <div className="flex flex-col gap-8">
      <Reveal className="grid grid-cols-1 gap-4 sm:grid-cols-[7fr_5fr]">
        <div className="card flex flex-col justify-center gap-2 p-8">
          <p className="flex items-center gap-2 text-sm font-medium text-[var(--text-muted)]">
            <Coins size={16} aria-hidden />내 누적 벌금
          </p>
          <p className="text-4xl font-bold text-[var(--accent)]">
            {formatFine(data.accumulatedFine)}
          </p>
        </div>
        <div className="card grid grid-cols-3 divide-x divide-[var(--border)] p-6 text-center">
          <StatCell label="정상" value={data.presentCount} />
          <StatCell label="지각" value={data.lateCount} />
          <StatCell label="결석" value={data.absentCount} />
        </div>
      </Reveal>

      <Reveal>
        <h2 className="mb-3 text-base text-[var(--text-strong)]">회차별 내 출석</h2>
        {data.records.length === 0 ? (
          <EmptyState title="아직 출석 기록이 없습니다" />
        ) : (
          <div className="card divide-y divide-[var(--border)]">
            {data.records.map((record) => (
              <div
                key={record.sessionId}
                className="flex items-center justify-between gap-4 px-5 py-4"
              >
                <div>
                  <p className="text-sm font-medium text-[var(--text-strong)]">
                    {record.sessionTitle}
                  </p>
                  <p className="text-xs text-[var(--text-muted)]">
                    {formatSessionDate(record.sessionDate)}
                  </p>
                </div>
                <div className="flex items-center gap-3">
                  <StatusBadge status={record.status} />
                  <span className="w-20 text-right text-sm font-semibold text-[var(--text-strong)]">
                    {formatFine(record.fineAmount)}
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </Reveal>
    </div>
  );
}

function OrganizerDashboard() {
  const membersQuery = useMembersQuery(false);
  const sessionsQuery = useSessionsQuery();
  const studyRoomQuery = useStudyRoomQuery();

  if (
    membersQuery.isPending ||
    sessionsQuery.isPending ||
    studyRoomQuery.isPending
  ) {
    return <SkeletonRows rows={5} />;
  }
  if (membersQuery.isError || sessionsQuery.isError || studyRoomQuery.isError) {
    return (
      <EmptyState
        title="대시보드를 불러오지 못했습니다"
        description="잠시 후 다시 시도해주세요"
      />
    );
  }

  const members = membersQuery.data ?? [];
  const sessions = sessionsQuery.data ?? [];
  const totalFine = members.reduce(
    (sum, member) => sum + member.accumulatedFine,
    0,
  );
  const recentSessions = sessions.slice(0, 5);

  return (
    <div className="flex flex-col gap-8">
      <Reveal className="grid grid-cols-1 gap-4 sm:grid-cols-[7fr_5fr]">
        <div className="card flex flex-col justify-center gap-2 p-8">
          <p className="flex items-center gap-2 text-sm font-medium text-[var(--text-muted)]">
            <Coins size={16} aria-hidden />
            스터디룸 누적 벌금 합계
          </p>
          <p className="text-4xl font-bold text-[var(--accent)]">
            {formatFine(totalFine)}
          </p>
          <p className="text-xs text-[var(--text-muted)]">
            지각 {studyRoomQuery.data?.lateFineAmount.toLocaleString("ko-KR")}원
            · 결석{" "}
            {studyRoomQuery.data?.absentFineAmount.toLocaleString("ko-KR")}원
          </p>
        </div>
        <div className="card p-6">
          <p className="mb-3 flex items-center gap-2 text-sm font-medium text-[var(--text-muted)]">
            <CalendarClock size={16} aria-hidden />
            최근 회차
          </p>
          {recentSessions.length === 0 ? (
            <p className="text-sm text-[var(--text-muted)]">
              아직 생성된 회차가 없습니다
            </p>
          ) : (
            <ul className="flex flex-col gap-2">
              {recentSessions.map((session) => (
                <li
                  key={session.id}
                  className="flex items-center justify-between text-sm"
                >
                  <span className="text-[var(--text-strong)]">
                    {session.title}
                  </span>
                  <span className="text-[var(--text-muted)]">
                    {session.checkedIn
                      ? formatFine(session.totalFine)
                      : "미체크"}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </Reveal>

      <Reveal>
        <h2 className="mb-3 text-base text-[var(--text-strong)]">멤버별 누적 벌금 랭킹</h2>
        {members.length === 0 ? (
          <EmptyState title="등록된 멤버가 없습니다" />
        ) : (
          <div className="card divide-y divide-[var(--border)]">
            {members.map((member, index) => (
              <div
                key={member.id}
                className="flex items-center justify-between gap-4 px-5 py-4"
              >
                <div className="flex items-center gap-3">
                  <span className="w-6 text-sm font-semibold text-[var(--text-muted)]">
                    {index + 1}
                  </span>
                  <div>
                    <p className="text-sm font-medium text-[var(--text-strong)]">
                      {member.name}
                    </p>
                    <p className="text-xs text-[var(--text-muted)]">
                      지각 {member.lateCount}회 · 결석 {member.absentCount}회
                    </p>
                  </div>
                </div>
                <span className="text-sm font-semibold text-[var(--text-strong)]">
                  {formatFine(member.accumulatedFine)}
                </span>
              </div>
            ))}
          </div>
        )}
      </Reveal>
    </div>
  );
}

function StatCell({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex flex-col gap-1">
      <p className="text-xs text-[var(--text-muted)]">{label}</p>
      <p className="text-xl font-bold text-[var(--text-strong)]">{value}</p>
    </div>
  );
}
