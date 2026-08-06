package com.jakesoneyo.studyfine.session;

import java.time.LocalDate;

/** 회차 목록의 집계 결과 행(벌금 합계·상태별 카운트를 단일 GROUP BY로 산출). */
public interface StudySessionSummaryProjection {

    Long getId();

    LocalDate getSessionDate();

    String getTitle();

    Boolean getCheckedIn();

    Long getTotalFine();

    Long getPresentCount();

    Long getLateCount();

    Long getAbsentCount();
}
