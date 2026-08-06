package com.jakesoneyo.studyfine.studyroom;

import static org.assertj.core.api.Assertions.assertThat;

import com.jakesoneyo.studyfine.attendance.AttendanceStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/** FinePolicy는 이 프로젝트의 핵심 도메인 규칙. 목(mock) 없이 순수 계산만 검증한다. */
class FinePolicyTest {

    private static StudyRoom room(int lateFineAmount, int absentFineAmount) {
        StudyRoom room = new StudyRoom();
        room.updateRates("스터디룸", lateFineAmount, absentFineAmount);
        return room;
    }

    @ParameterizedTest(name = "단가(지각={0}, 결석={1})가 얼마든 PRESENT는 0원")
    @CsvSource({
        "3000, 5000",
        "0, 0",
        "1000000, 1000000"
    })
    void present_isAlwaysZero(int lateFineAmount, int absentFineAmount) {
        StudyRoom room = room(lateFineAmount, absentFineAmount);

        assertThat(FinePolicy.calculate(AttendanceStatus.PRESENT, room)).isZero();
    }

    @ParameterizedTest(name = "지각 단가={0}원이면 LATE 벌금도 {0}원")
    @CsvSource({"3000", "5000", "0", "1000000"})
    void late_equalsLateFineAmount(int lateFineAmount) {
        StudyRoom room = room(lateFineAmount, 9999);

        assertThat(FinePolicy.calculate(AttendanceStatus.LATE, room)).isEqualTo(lateFineAmount);
    }

    @ParameterizedTest(name = "결석 단가={0}원이면 ABSENT 벌금도 {0}원")
    @CsvSource({"3000", "5000", "0", "1000000"})
    void absent_equalsAbsentFineAmount(int absentFineAmount) {
        StudyRoom room = room(9999, absentFineAmount);

        assertThat(FinePolicy.calculate(AttendanceStatus.ABSENT, room)).isEqualTo(absentFineAmount);
    }

    @Test
    void zeroRate_producesZeroFine() {
        StudyRoom room = room(0, 0);

        assertThat(FinePolicy.calculate(AttendanceStatus.LATE, room)).isZero();
        assertThat(FinePolicy.calculate(AttendanceStatus.ABSENT, room)).isZero();
    }

    @Test
    void snapshot_pastResultDoesNotChangeWhenRateChangesLater() {
        // 1월: 지각 단가 3,000원으로 체크 → 3,000원을 "확정값"으로 별도 변수에 받아둔다(호출부가 그대로 저장한다고 가정).
        StudyRoom room = room(3000, 5000);
        int confirmedFineInJanuary = FinePolicy.calculate(AttendanceStatus.LATE, room);

        // 2월: 운영자가 같은 room의 단가를 인상한다.
        room.updateRates(null, 5000, null);

        // 1월에 이미 확정해 저장해둔 값은 순수 계산 결과이므로 room이 바뀌어도 변하지 않는다.
        assertThat(confirmedFineInJanuary).isEqualTo(3000);
        // 반대로 지금 다시 계산하면(=신규 체크) 새 단가가 적용된다 — 스냅샷은 "저장한 값"에 대한 보장이지,
        // room 객체 자체가 불변이라는 뜻이 아님을 함께 보여준다.
        assertThat(FinePolicy.calculate(AttendanceStatus.LATE, room)).isEqualTo(5000);
    }
}
