package com.hotel.service;

import com.hotel.domain.*;
import com.hotel.repository.RoomRepository;
import com.hotel.service.dto.RoomChangeRequest;
import com.hotel.service.dto.RoomChangeResult;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

public class RoomChangeService {

    private final RoomRepository roomRepository;

    public RoomChangeService(RoomRepository roomRepository) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository는 필수입니다.");
    }

    public RoomChangeResult changeRoom(Reservation reservation, RoomChangeRequest request) {
        if (reservation == null) {
            return RoomChangeResult.failure(null, "예약 정보가 유효하지 않습니다.");
        }
        if (request == null) {
            return RoomChangeResult.failure(reservation.getReservationId(), "요청 정보가 유효하지 않습니다.");
        }

        String targetRoomNumber = request.targetRoomNumber();
        if (targetRoomNumber == null || targetRoomNumber.isBlank()) {
            return RoomChangeResult.failure(reservation.getReservationId(), "이동할 대상 객실 번호가 지정되지 않았습니다.");
        }

        if (!reservation.getStatus().isInHouse()) {
            return RoomChangeResult.failure(reservation.getReservationId(),
                    "투숙 중인 고객만 룸 체인지가 가능합니다. (현재 상태: " + reservation.getStatus() + ")");
        }

        String originRoomNumber = reservation.getAssignedRoomNumber();
        if (targetRoomNumber.trim().equals(originRoomNumber)) {
            return RoomChangeResult.failure(reservation.getReservationId(), "현재 배정된 객실과 동일한 객실로 이동할 수 없습니다.");
        }

        Optional<Room> targetRoomOpt = roomRepository.findByRoomNumber(targetRoomNumber.trim());
        if (targetRoomOpt.isEmpty()) {
            return RoomChangeResult.failure(reservation.getReservationId(), "해당 객실(" + targetRoomNumber + ")이 도면에 존재하지 않습니다.");
        }
        Room targetRoom = targetRoomOpt.get();

        if (!targetRoom.getStatus().isAssignable()) {
            return RoomChangeResult.failure(reservation.getReservationId(),
                    "이동 대상 객실(" + targetRoomNumber + "호)은 입실 가능한 공실(VACANT)이 아닙니다. (현재 상태: "
                            + targetRoom.getStatus().getTitle() + ")");
        }

        if (targetRoom.getRoomType() != reservation.getBookedRoomType()) {
            return RoomChangeResult.failure(reservation.getReservationId(),
                    String.format("객실 타입 불일치: 예약 타입은 %s이나 대상 객실은 %s입니다.",
                            reservation.getBookedRoomType(), targetRoom.getRoomType()));
        }

        LocalDate moveDate = request.moveDate();
        LocalDate checkOutDate = reservation.getCheckOutDate();

        if (moveDate.isAfter(checkOutDate)) {
            return RoomChangeResult.failure(reservation.getReservationId(), "이동 일자는 체크아웃 날짜보다 이후일 수 없습니다.");
        }

        int remainingNights = (int) ChronoUnit.DAYS.between(moveDate, checkOutDate);
        StayPeriod remainingPeriod = (remainingNights > 0) ? new StayPeriod(moveDate, remainingNights) : null;

        // [동시성 원자적 선점 보장]
        // 기존 객실을 훼손하기 전에, 신규 객실에 대한 점유를 먼저 원자적으로 시도(tryBookPeriod)합니다.
        if (remainingPeriod != null) {
            boolean booked = targetRoom.tryBookPeriod(remainingPeriod);
            if (!booked) {
                return RoomChangeResult.failure(reservation.getReservationId(),
                        String.format("대상 객실(%s호)은 해당 잔여 기간(%s)에 이미 다른 예약이 점유하여 배정할 수 없습니다.",
                                targetRoomNumber, remainingPeriod));
            }
        }

        // 신규 객실 점유 성공 확정 후, 비로소 기존 객실의 잔여 스케줄 단축 및 청소대기(OUT) 전이
        if (originRoomNumber != null) {
            roomRepository.findByRoomNumber(originRoomNumber.trim()).ifPresent(originRoom -> {
                originRoom.truncatePeriodFrom(moveDate);
                originRoom.setStatus(RoomStatus.OUT);
            });
        }

        // 신규 객실 상태 재실(OCCUPIED) 확정 및 예약 호실 번호 최신화
        targetRoom.setStatus(RoomStatus.OCCUPIED);
        reservation.changeRoom(targetRoom.getRoomNumber());

        return RoomChangeResult.success(
                reservation.getReservationId(),
                originRoomNumber,
                targetRoom.getRoomNumber(),
                moveDate,
                remainingNights
        );
    }
}