package com.jakesoneyo.studyfine.member;

/**
 * 멤버 목록 + 누적 벌금 단일 집계 쿼리의 결과 행. 엔티티로 받아 자바에서 합산하면 N+1이 되므로
 * DB에서 이미 합산된 값을 그대로 옮겨 담는 인터페이스 projection을 쓴다(DATA-MODEL.md §4.1).
 */
public interface MemberFineSummaryProjection {

    Long getId();

    String getName();

    String getEmail();

    String getRole();

    Boolean getActive();

    // SUM(integer)는 Postgres에서 bigint로 온다. int로 받으면 매핑 예외.
    Long getAccumulatedFine();

    Long getPresentCount();

    Long getLateCount();

    Long getAbsentCount();
}
