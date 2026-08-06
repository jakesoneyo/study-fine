package com.jakesoneyo.studyfine.member;

import com.jakesoneyo.studyfine.common.ConflictException;
import com.jakesoneyo.studyfine.common.NotFoundException;
import com.jakesoneyo.studyfine.member.dto.MemberCreateRequest;
import com.jakesoneyo.studyfine.member.dto.MemberSummaryResponse;
import com.jakesoneyo.studyfine.member.dto.MemberUpdateRequest;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** 누적 벌금 포함 목록. 단일 GROUP BY 집계 쿼리라 멤버 수와 무관하게 쿼리 1회(N+1 없음). */
    @Transactional(readOnly = true)
    public List<MemberSummaryResponse> list(boolean includeInactive) {
        return memberRepository.findAllWithFineSummary(includeInactive).stream()
            .map(MemberSummaryResponse::from)
            .toList();
    }

    @Transactional
    public MemberSummaryResponse create(MemberCreateRequest request) {
        if (memberRepository.existsByEmail(request.email())) {
            throw new ConflictException("이미 등록된 이메일입니다");
        }
        Member member = new Member(
            request.email(), passwordEncoder.encode(request.password()), request.name(), request.role()
        );
        memberRepository.save(member);
        return MemberSummaryResponse.ofNewMember(member);
    }

    /**
     * 마지막 남은 활성 운영자를 스스로 비활성화/강등하면 아무도 운영을 못 하게 된다.
     * 이 사고를 막기 위해 "이 수정으로 활성 운영자가 0명이 되는가"를 먼저 검사한다.
     */
    @Transactional
    public MemberSummaryResponse update(Long id, MemberUpdateRequest request) {
        Member member = memberRepository.findById(id)
            .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다"));

        boolean wasActiveOrganizer = member.getRole() == MemberRole.ORGANIZER && member.isActive();
        MemberRole nextRole = request.role() != null ? request.role() : member.getRole();
        boolean nextActive = request.active() != null ? request.active() : member.isActive();
        boolean willBeActiveOrganizer = nextRole == MemberRole.ORGANIZER && nextActive;

        if (wasActiveOrganizer && !willBeActiveOrganizer && memberRepository.countByRoleAndActiveTrue(MemberRole.ORGANIZER) <= 1) {
            throw new ConflictException("운영자가 최소 1명 필요합니다");
        }

        member.updateProfile(request.name(), request.role(), request.active());

        return memberRepository.findFineSummaryByMemberId(id)
            .map(MemberSummaryResponse::from)
            .orElseThrow(() -> new NotFoundException("멤버를 찾을 수 없습니다"));
    }
}
