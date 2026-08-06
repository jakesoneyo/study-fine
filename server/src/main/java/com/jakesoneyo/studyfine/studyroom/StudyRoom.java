package com.jakesoneyo.studyfine.studyroom;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 이 서비스가 관리하는 단 하나의 스터디 모임. id는 항상 1(마이그레이션의 CHECK 제약으로 강제).
 * 벌금 단가를 하드코딩/프로퍼티가 아니라 이 테이블에 두는 이유는 운영자가 재배포 없이 조정해야 하기 때문이다.
 */
@Entity
@Table(name = "study_room")
public class StudyRoom {

    @Id
    private Long id;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(name = "late_fine_amount", nullable = false)
    private int lateFineAmount;

    @Column(name = "absent_fine_amount", nullable = false)
    private int absentFineAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected StudyRoom() {
    }

    /** 벌금 단가 부분 수정. 기존 출석 기록의 fineAmount는 이 메서드로 절대 건드리지 않는다(스냅샷 원칙). */
    public void updateRates(String name, Integer lateFineAmount, Integer absentFineAmount) {
        if (name != null) {
            this.name = name;
        }
        if (lateFineAmount != null) {
            this.lateFineAmount = lateFineAmount;
        }
        if (absentFineAmount != null) {
            this.absentFineAmount = absentFineAmount;
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getLateFineAmount() {
        return lateFineAmount;
    }

    public int getAbsentFineAmount() {
        return absentFineAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
