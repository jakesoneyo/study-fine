package com.jakesoneyo.studyfine.seed;

import com.jakesoneyo.studyfine.attendance.AttendanceRecord;
import com.jakesoneyo.studyfine.attendance.AttendanceRepository;
import com.jakesoneyo.studyfine.attendance.AttendanceStatus;
import com.jakesoneyo.studyfine.member.Member;
import com.jakesoneyo.studyfine.member.MemberRepository;
import com.jakesoneyo.studyfine.member.MemberRole;
import com.jakesoneyo.studyfine.session.StudySession;
import com.jakesoneyo.studyfine.session.StudySessionRepository;
import com.jakesoneyo.studyfine.studyroom.FinePolicy;
import com.jakesoneyo.studyfine.studyroom.StudyRoom;
import com.jakesoneyo.studyfine.studyroom.StudyRoomRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 면접관이 데모 로그인 직후 빈 화면을 보지 않게 admin + 샘플 멤버 3명 + 회차 2개 + 출석 8건을
 * 채운다(DATA-MODEL.md §6). {@code app.seed.enabled=false}로 끌 수 있다.
 */
@Component
@ConditionalOnProperty(prefix = "app.seed", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DemoDataSeeder implements ApplicationRunner {

    private static final String ADMIN_EMAIL = "admin";
    // 데모용 멤버 계정의 로그인 비밀번호. 이 세 계정은 안내 대상이 아니다 — 데모 로그인은 admin만 노출한다.
    private static final String SAMPLE_MEMBER_PASSWORD = "studyfine-demo-2024";

    private final MemberRepository memberRepository;
    private final StudySessionRepository studySessionRepository;
    private final AttendanceRepository attendanceRepository;
    private final StudyRoomRepository studyRoomRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(
        MemberRepository memberRepository,
        StudySessionRepository studySessionRepository,
        AttendanceRepository attendanceRepository,
        StudyRoomRepository studyRoomRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.memberRepository = memberRepository;
        this.studySessionRepository = studySessionRepository;
        this.attendanceRepository = attendanceRepository;
        this.studyRoomRepository = studyRoomRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        // 멱등 — admin이 이미 있으면 이전에 시드가 끝났다는 뜻이므로 통째로 스킵한다.
        if (memberRepository.existsByEmail(ADMIN_EMAIL)) {
            return;
        }

        Member organizer = new Member(ADMIN_EMAIL, passwordEncoder.encode(ADMIN_EMAIL), "굴리자", MemberRole.ORGANIZER);
        Member member1 = new Member("member1@studyfine.dev", passwordEncoder.encode(SAMPLE_MEMBER_PASSWORD), "김스터디", MemberRole.MEMBER);
        Member member2 = new Member("member2@studyfine.dev", passwordEncoder.encode(SAMPLE_MEMBER_PASSWORD), "박열공", MemberRole.MEMBER);
        Member member3 = new Member("member3@studyfine.dev", passwordEncoder.encode(SAMPLE_MEMBER_PASSWORD), "이신입", MemberRole.MEMBER);
        memberRepository.saveAll(List.of(organizer, member1, member2, member3));

        StudySession session1 = new StudySession(LocalDate.now().minusDays(8), "2주차 — 스프링");
        StudySession session2 = new StudySession(LocalDate.now().minusDays(1), "3주차 — 알고리즘");
        studySessionRepository.saveAll(List.of(session1, session2));

        StudyRoom room = studyRoomRepository.findById(1L)
            .orElseThrow(() -> new IllegalStateException("study_room 시드 행(id=1)이 없습니다. V1__init.sql을 확인하세요"));

        // PRESENT/LATE/ABSENT를 섞어 누적 벌금이 0원이 되지 않게 한다 — 0원이면 핵심 기능이 안 보인다.
        attendanceRepository.saveAll(List.of(
            attendance(session1, organizer, AttendanceStatus.PRESENT, room),
            attendance(session1, member1, AttendanceStatus.LATE, room),
            attendance(session1, member2, AttendanceStatus.ABSENT, room),
            attendance(session1, member3, AttendanceStatus.PRESENT, room),
            attendance(session2, organizer, AttendanceStatus.PRESENT, room),
            attendance(session2, member1, AttendanceStatus.PRESENT, room),
            attendance(session2, member2, AttendanceStatus.LATE, room),
            attendance(session2, member3, AttendanceStatus.ABSENT, room)
        ));
    }

    private AttendanceRecord attendance(StudySession session, Member member, AttendanceStatus status, StudyRoom room) {
        return new AttendanceRecord(session, member, status, FinePolicy.calculate(status, room));
    }
}
