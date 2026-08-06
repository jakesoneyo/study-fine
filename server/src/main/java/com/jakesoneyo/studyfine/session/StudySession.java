package com.jakesoneyo.studyfine.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import org.hibernate.annotations.CreationTimestamp;

/**
 * 특정 날짜에 열린 스터디 모임 1회(회차). 출석 기록이 하나도 없어도 "만들어진 회차"로 존재해야 하므로
 * 출석 기록에서 날짜를 파생하지 않고 별도 1급 엔티티로 둔다(DATA-MODEL.md §2.3).
 */
@Entity
@Table(name = "study_session")
public class StudySession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_date", nullable = false, unique = true)
    private LocalDate sessionDate;

    @Column(nullable = false, length = 100)
    private String title;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected StudySession() {
    }

    public StudySession(LocalDate sessionDate, String title) {
        this.sessionDate = sessionDate;
        this.title = title;
    }

    public Long getId() {
        return id;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public String getTitle() {
        return title;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
