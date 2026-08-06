package com.jakesoneyo.studyfine.member.dto;

import com.jakesoneyo.studyfine.member.Member;
import com.jakesoneyo.studyfine.member.MemberFineSummaryProjection;

/** 멤버 목록(#6)·생성(#7)·수정(#8) 공통 응답 형태. 누적 벌금·상태별 카운트를 포함한다. */
public record MemberSummaryResponse(
    Long id,
    String name,
    String email,
    String role,
    boolean active,
    long accumulatedFine,
    long lateCount,
    long absentCount
) {

    public static MemberSummaryResponse from(MemberFineSummaryProjection projection) {
        return new MemberSummaryResponse(
            projection.getId(),
            projection.getName(),
            projection.getEmail(),
            projection.getRole(),
            projection.getActive(),
            projection.getAccumulatedFine(),
            projection.getLateCount(),
            projection.getAbsentCount()
        );
    }

    /** 방금 생성해 출석 기록이 있을 수 없는 신규 멤버용 — 집계 쿼리를 다시 태우지 않고 0으로 채운다. */
    public static MemberSummaryResponse ofNewMember(Member member) {
        return new MemberSummaryResponse(
            member.getId(), member.getName(), member.getEmail(), member.getRole().name(), member.isActive(), 0, 0, 0
        );
    }
}
