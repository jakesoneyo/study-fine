package com.jakesoneyo.studyfine.attendance;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {

    /**
     * 회차 상세용. member를 fetch join하지 않으면 출석 M건에 대해 member SELECT가 M번 나간다.
     * bulk upsert 사전 조회에도 재사용 — 멤버마다 findBySessionAndMember를 부르면 N+1이 된다.
     */
    @EntityGraph(attributePaths = "member")
    List<AttendanceRecord> findByStudySessionId(Long studySessionId);

    /** 내 출석 내역 / 특정 멤버 출석 내역. 회차 정보를 join fetch로 한 번에 가져온다. */
    @Query("""
        select ar from AttendanceRecord ar
        join fetch ar.studySession s
        where ar.member.id = :memberId
        order by s.sessionDate desc
        """)
    List<AttendanceRecord> findByMemberIdOrderBySessionDateDesc(@Param("memberId") Long memberId);
}
