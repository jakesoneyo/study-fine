package com.jakesoneyo.studyfine.studyroom.dto;

import com.jakesoneyo.studyfine.studyroom.StudyRoom;

public record StudyRoomResponse(Long id, String name, int lateFineAmount, int absentFineAmount) {

    public static StudyRoomResponse from(StudyRoom room) {
        return new StudyRoomResponse(room.getId(), room.getName(), room.getLateFineAmount(), room.getAbsentFineAmount());
    }
}
