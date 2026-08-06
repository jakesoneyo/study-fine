package com.jakesoneyo.studyfine.studyroom;

import com.jakesoneyo.studyfine.common.NotFoundException;
import com.jakesoneyo.studyfine.studyroom.dto.StudyRoomResponse;
import com.jakesoneyo.studyfine.studyroom.dto.StudyRoomUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyRoomService {

    /** 스터디룸은 시스템에 정확히 1개, id=1 고정(마이그레이션 CHECK 제약, UBIQUITOUS_LANGUAGE.md). */
    private static final Long SINGLETON_ID = 1L;

    private final StudyRoomRepository studyRoomRepository;

    public StudyRoomService(StudyRoomRepository studyRoomRepository) {
        this.studyRoomRepository = studyRoomRepository;
    }

    @Transactional(readOnly = true)
    public StudyRoomResponse get() {
        return StudyRoomResponse.from(loadRoom());
    }

    /** 새 단가는 이후 출석 체크부터 적용된다. 기존 출석 기록의 fineAmount는 이 메서드가 절대 건드리지 않는다. */
    @Transactional
    public StudyRoomResponse update(StudyRoomUpdateRequest request) {
        StudyRoom room = loadRoom();
        room.updateRates(request.name(), request.lateFineAmount(), request.absentFineAmount());
        return StudyRoomResponse.from(room);
    }

    private StudyRoom loadRoom() {
        return studyRoomRepository.findById(SINGLETON_ID)
            .orElseThrow(() -> new NotFoundException("스터디룸 설정을 찾을 수 없습니다"));
    }
}
