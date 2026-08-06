package com.jakesoneyo.studyfine.session;

import java.util.List;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StudySessionRepository extends JpaRepository<StudySession, Long> {

    boolean existsBySessionDate(LocalDate sessionDate);

    /** 회차 목록 + 출석 집계를 단일 GROUP BY 쿼리로 산출한다(회차마다 합계 쿼리를 따로 날리면 N+1). */
    @Query(
        value = """
            SELECT s.id AS id, s.session_date AS sessionDate, s.title AS title,
                   (COUNT(ar.id) > 0) AS checkedIn,
                   COALESCE(SUM(ar.fine_amount), 0) AS totalFine,
                   COUNT(ar.id) FILTER (WHERE ar.status = 'PRESENT') AS presentCount,
                   COUNT(ar.id) FILTER (WHERE ar.status = 'LATE') AS lateCount,
                   COUNT(ar.id) FILTER (WHERE ar.status = 'ABSENT') AS absentCount
            FROM study_session s
            LEFT JOIN attendance_record ar ON ar.session_id = s.id
            GROUP BY s.id
            ORDER BY s.session_date DESC
            """,
        nativeQuery = true
    )
    List<StudySessionSummaryProjection> findAllWithSummary();
}
