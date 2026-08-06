package com.jakesoneyo.studyfine.member;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Member> findByActiveTrueOrderByIdAsc();

    /** 마지막 남은 운영자를 스스로 비활성화/강등하는 사고를 막기 위한 카운트(#8 API의 409 조건). */
    long countByRoleAndActiveTrue(MemberRole role);

    /**
     * 멤버 목록 + 누적 벌금을 단일 GROUP BY 쿼리로 산출한다(N+1 방지, DATA-MODEL.md §4.1).
     * LEFT JOIN이라 출석 기록이 없는 신규 멤버도 0원으로 나온다.
     */
    @Query(
        value = """
            SELECT m.id AS id, m.name AS name, m.email AS email, m.role AS role, m.active AS active,
                   COALESCE(SUM(ar.fine_amount), 0) AS accumulatedFine,
                   COUNT(ar.id) FILTER (WHERE ar.status = 'PRESENT') AS presentCount,
                   COUNT(ar.id) FILTER (WHERE ar.status = 'LATE') AS lateCount,
                   COUNT(ar.id) FILTER (WHERE ar.status = 'ABSENT') AS absentCount
            FROM member m
            LEFT JOIN attendance_record ar ON ar.member_id = m.id
            WHERE (:includeInactive = TRUE OR m.active = TRUE)
            GROUP BY m.id
            ORDER BY accumulatedFine DESC, m.name
            """,
        nativeQuery = true
    )
    List<MemberFineSummaryProjection> findAllWithFineSummary(@Param("includeInactive") boolean includeInactive);

    @Query(
        value = """
            SELECT m.id AS id, m.name AS name, m.email AS email, m.role AS role, m.active AS active,
                   COALESCE(SUM(ar.fine_amount), 0) AS accumulatedFine,
                   COUNT(ar.id) FILTER (WHERE ar.status = 'PRESENT') AS presentCount,
                   COUNT(ar.id) FILTER (WHERE ar.status = 'LATE') AS lateCount,
                   COUNT(ar.id) FILTER (WHERE ar.status = 'ABSENT') AS absentCount
            FROM member m
            LEFT JOIN attendance_record ar ON ar.member_id = m.id
            WHERE m.id = :memberId
            GROUP BY m.id
            """,
        nativeQuery = true
    )
    Optional<MemberFineSummaryProjection> findFineSummaryByMemberId(@Param("memberId") Long memberId);
}
