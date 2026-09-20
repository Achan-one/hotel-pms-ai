package com.hotel.service;

import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.StayPeriod;
import com.hotel.repository.RoomRepository;
import com.hotel.service.dto.RoomChangeResult;

import java.util.Objects;
import java.util.Optional;

public class RoomChangeService {

    private final RoomRepository roomRepository;

    public RoomChangeService(RoomRepository roomRepository) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository는 필수입니다.");
    }

    public RoomChangeResult changeRoom(Reservation reservation, String targetRoomNumber, boolean releaseOldRoomSchedule) {
        if (reservation == null) {
            return RoomChangeResult.failure(null, "예약 정보가 유효하지 않습니다.");
        }
        if (targetRoomNumber == null || targetRoomNumber.isBlank()) {
            return RoomChangeResult.failure(reservation.getReservationId(), "이동할 대상 객실 번호가 지정되지 않았습니다.");
        }

        String originRoomNumber = reservation.getAssignedRoomNumber();
        if (targetRoomNumber.trim().equals(originRoomNumber)) {
            return RoomChangeResult.failure(reservation.getReservationId(), "현재 배정된 객실과 동일한 객실로 이동할 수 없습니다.");
        }

        // 1. 이동 대상 객실 존재 여부 확인 (Optional 방어)
        Optional<Room> targetRoomOpt = roomRepository.findByRoomNumber(targetRoomNumber.trim());
        if (targetRoomOpt.isEmpty()) {
            return RoomChangeResult.failure(reservation.getReservationId(), "해당 객실(" + targetRoomNumber + ")이 호텔 도면에 존재하지 않습니다.");
        }
        Room newRoom = targetRoomOpt.get();

        // 2. 객실 타입 일치 검증 (동일 타입 이동 기본 원칙)
        if (newRoom.getRoomType() != reservation.getBookedRoomType()) {
            return RoomChangeResult.failure(reservation.getReservationId(),
                    String.format("객실 타입 불일치: 예약 타입은 %s이나 대상 객실은 %s입니다.",
                            reservation.getBookedRoomType(), newRoom.getRoomType()));
        }

        // 3. 신규 객실의 날짜 스케줄 충돌 방어
        StayPeriod stayPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());
        if (newRoom.hasScheduleConflict(stayPeriod)) {
            return RoomChangeResult.failure(reservation.getReservationId(),
                    String.format("대상 객실(%s)은 해당 투숙 기간(%s)에 이미 다른 예약이 확정되어 있습니다.",
                            targetRoomNumber, stayPeriod));
        }

        // 4. 기존 객실 스케줄 반납 처리 (Optional 안전 처리)
        if (originRoomNumber != null && releaseOldRoomSchedule) {
            roomRepository.findByRoomNumber(originRoomNumber.trim())
                    .ifPresent(oldRoom -> oldRoom.cancelPeriod(stayPeriod));
        }

        // 5. 신규 객실 스케줄 등록 및 예약 정보 갱신
        newRoom.bookPeriod(stayPeriod);
        reservation.assignRoom(newRoom.getRoomNumber());

        return RoomChangeResult.success(
                reservation.getReservationId(),
                originRoomNumber,
                newRoom.getRoomNumber(),
                String.format("[%s -> %s] 수동 재배정 완료 (%s)", originRoomNumber, newRoom.getRoomNumber(), stayPeriod)
        );
    }
}