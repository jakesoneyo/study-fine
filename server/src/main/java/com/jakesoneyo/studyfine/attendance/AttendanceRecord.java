package com.jakesoneyo.studyfine.attendance;

import com.jakesoneyo.studyfine.member.Member;
import com.jakesoneyo.studyfine.session.StudySession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 한 회차에 대한 한 멤버의 출석 결과 1건. fineAmount는 파생값 캐시가 아니라 기록 시점에 확정된
 * 사실(fact)이다 — 이후 벌금 단가가 바뀌어도 이 값은 이 메서드를 통해서만 바뀐다(스냅샷 원칙).
 */
@Entity
@Table(
    name = "attendance_record",
    uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "member_id"})
)
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private StudySession studySession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AttendanceStatus status;

    @Column(name = "fine_amount", nullable = false)
    private int fineAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AttendanceRecord() {
    }

    public AttendanceRecord(StudySession studySession, Member member, AttendanceStatus status, int fineAmount) {
        this.studySession = studySession;
        this.member = member;
        this.status = status;
        this.fineAmount = fineAmount;
    }

    /** 출석 체크 재저장(bulk upsert) 시 기존 기록을 새 상태·벌금으로 갱신한다. */
    public void updateStatus(AttendanceStatus status, int fineAmount) {
        this.status = status;
        this.fineAmount = fineAmount;
    }

    public Long getId() {
        return id;
    }

    public StudySession getStudySession() {
        return studySession;
    }

    public Member getMember() {
        return member;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public int getFineAmount() {
        return fineAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
