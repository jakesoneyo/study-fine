package com.jakesoneyo.studyfine.attendance.dto;

/** 회차 상세(#12)·출석 체크 응답(#14)의 멤버 1인분 출석 상태. 미기록 멤버는 status가 null로 온다. */
public record AttendanceView(Long memberId, String memberName, String status, int fineAmount) {
}
