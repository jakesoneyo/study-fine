package com.jakesoneyo.studyfine.studyroom;

import com.jakesoneyo.studyfine.attendance.AttendanceStatus;

/**
 * 출석 상태 + 스터디룸의 현재 벌금 단가 → 벌금 산출. 스프링 빈이 아니고 DB도 모르는 순수 함수다.
 * 호출 시점의 단가로 즉시 계산해 호출부가 그 결과를 확정 저장(스냅샷)하게 한다 — 단가가 나중에
 * 바뀌어도 이미 계산된 값 자체는 다시 계산하지 않는 한 변하지 않는다(ARCHITECTURE.md §5.2).
 */
public final class FinePolicy {

    private FinePolicy() {
    }

    /** 출석 상태에 대응하는 벌금을 계산한다. 부작용 없음. */
    public static int calculate(AttendanceStatus status, StudyRoom room) {
        return switch (status) {
            case PRESENT -> 0;
            case LATE -> room.getLateFineAmount();
            case ABSENT -> room.getAbsentFineAmount();
        };
    }
}
