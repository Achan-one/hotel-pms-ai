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

    /**
     * 프론트 데스크 실무 룸 체인지 실행
     * - 당일 체크인 직후 0박 이동 지원
     * - 연박 중 잔여 박수 분할 이동 지원
     * - 체크아웃 당일 레이트 아웃 룸 무브 지원
     */
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

        // 1. 투숙 상태 검증 (인하우스 상태만 가능)
        if (!reservation.getStatus().isInHouse()) {
            return RoomChangeResult.failure(reservation.getReservationId(),
                    "투숙 중인 고객만 룸 체인지가 가능합니다. (현재 상태: " + reservation.getStatus() + ")");
        }

        String originRoomNumber = reservation.getAssignedRoomNumber();
        if (targetRoomNumber.trim().equals(originRoomNumber)) {
            return RoomChangeResult.failure(reservation.getReservationId(), "현재 배정된 객실과 동일한 객실로 이동할 수 없습니다.");
        }

        // 2. 이동 대상 객실 존재 여부 확인
        Optional<Room> targetRoomOpt = roomRepository.findByRoomNumber(targetRoomNumber.trim());
        if (targetRoomOpt.isEmpty()) {
            return RoomChangeResult.failure(reservation.getReservationId(), "해당 객실(" + targetRoomNumber + ")이 도면에 존재하지 않습니다.");
        }
        Room targetRoom = targetRoomOpt.get();

        // 3. 신규 객실 하우스키핑 상태 방어 (공실 VACANT 상태만 입실 가능)
        if (!targetRoom.getStatus().isAssignable()) {
            return RoomChangeResult.failure(reservation.getReservationId(),
                    "이동 대상 객실(" + targetRoomNumber + "호)은 입실 가능한 공실(VACANT)이 아닙니다. (현재 상태: "
                            + targetRoom.getStatus().getTitle() + ")");
        }

        // 4. 객실 타입 일치 검증
        if (targetRoom.getRoomType() != reservation.getBookedRoomType()) {
            return RoomChangeResult.failure(reservation.getReservationId(),
                    String.format("객실 타입 불일치: 예약 타입은 %s이나 대상 객실은 %s입니다.",
                            reservation.getBookedRoomType(), targetRoom.getRoomType()));
        }

        // 5. 남은 숙박 기간(Remaining StayPeriod) 분할 계산
        LocalDate moveDate = request.moveDate();
        LocalDate checkOutDate = reservation.getCheckOutDate();

        // 이동일자가 체크아웃 이후인 경우는 차단
        if (moveDate.isAfter(checkOutDate)) {
            return RoomChangeResult.failure(reservation.getReservationId(), "이동 일자는 체크아웃 날짜보다 이후일 수 없습니다.");
        }

        int remainingNights = (int) ChronoUnit.DAYS.between(moveDate, checkOutDate);
        StayPeriod remainingPeriod = (remainingNights > 0) ? new StayPeriod(moveDate, remainingNights) : null;

        // 6. 신규 객실의 남은 기간 스케줄 가용성 확인 (잔여 박수가 있을 경우에만)
        if (remainingPeriod != null && !targetRoom.isAvailable(remainingPeriod)) {
            return RoomChangeResult.failure(reservation.getReservationId(),
                    String.format("대상 객실(%s)은 해당 잔여 투숙 기간(%s)에 이미 다른 예약이 존재합니다.",
                            targetRoomNumber, remainingPeriod));
        }

        // ==========================================
        // 7. 스케줄 이전 및 상태 전이 실행
        // ==========================================

        // 기존 방: moveDate 이후 잔여 스케줄 단축 반납 + 청소 대기(OUT) 상태로 변경
        if (originRoomNumber != null) {
            roomRepository.findByRoomNumber(originRoomNumber.trim()).ifPresent(originRoom -> {
                originRoom.truncatePeriodFrom(moveDate);
                originRoom.setStatus(RoomStatus.OUT);
            });
        }

        // 신규 방: 잔여 기간 등록 + 재실(OCCUPIED) 상태로 변경
        if (remainingPeriod != null) {
            targetRoom.bookPeriod(remainingPeriod);
        }
        targetRoom.setStatus(RoomStatus.OCCUPIED);

        // 예약 객체 상태 전이 (ROOM_CHANGED 및 새 호실 갱신)
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