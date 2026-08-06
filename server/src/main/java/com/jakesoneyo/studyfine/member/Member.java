package com.jakesoneyo.studyfine.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * 스터디룸의 참여자이자 로그인 주체. 단일 스터디룸 전제에서 참여자와 로그인 계정을 분리하는 것은
 * 과설계이므로 하나로 통합했다(UBIQUITOUS_LANGUAGE.md).
 */
@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 72)
    private String passwordHash;

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Member() {
    }

    public Member(String email, String passwordHash, String name, MemberRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.active = true;
    }

    /** 명단·출석 체크 화면에서 제외한다. 물리 삭제하지 않아 과거 출석 기록·벌금 근거가 보존된다. */
    public void deactivate() {
        this.active = false;
    }

    /** 이름·역할·활성 여부 부분 수정(PATCH). 보낸 필드만 반영한다. */
    public void updateProfile(String name, MemberRole role, Boolean active) {
        if (name != null) {
            this.name = name;
        }
        if (role != null) {
            this.role = role;
        }
        if (active != null) {
            this.active = active;
        }
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public MemberRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
