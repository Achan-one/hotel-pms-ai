package com.hotel.service.validator;

import com.hotel.domain.Reservation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReservationValidator {

    public static final int MAX_STAY_NIGHTS = 31;

    public ValidationResult validateSingle(Reservation reservation) {
        if (reservation == null) {
            return ValidationResult.failure("예약 데이터가 null입니다.");
        }

        if (isBlank(reservation.getReservationId())) {
            return ValidationResult.failure("예약 ID가 누락되었습니다.");
        }

        if (isBlank(reservation.getGuestName())) {
            return ValidationResult.failure("고객 이름이 누락되었습니다.");
        }

        if (reservation.getBookedRoomType() == null) {
            return ValidationResult.failure("객실 타입이 지정되지 않았습니다.");
        }

        if (reservation.getCheckInDate() == null) {
            return ValidationResult.failure("체크인 날짜가 누락되었습니다.");
        }

        if (reservation.getStayNights() <= 0) {
            return ValidationResult.failure("투숙 박수는 최소 1박 이상이어야 합니다.");
        }

        if (reservation.getStayNights() > MAX_STAY_NIGHTS) {
            return ValidationResult.failure(String.format("최대 투숙 가능 일수(%d박)를 초과했습니다: %d박",
                    MAX_STAY_NIGHTS, reservation.getStayNights()));
        }

        return ValidationResult.success(reservation);
    }

    public List<Reservation> filterValidReservations(List<Reservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return List.of();
        }

        Set<String> seenIds = new HashSet<>();
        List<Reservation> validList = new ArrayList<>();

        for (Reservation res : reservations) {
            ValidationResult result = validateSingle(res);
            if (result.isValid()) {
                if (seenIds.add(res.getReservationId())) {
                    validList.add(res);
                } else {
                    System.err.printf("[Validator 방어] 중복된 예약 ID 차단: %s (고객: %s)%n",
                            res.getReservationId(), res.getGuestName());
                }
            } else {
                System.err.printf("[Validator 방어] 유효하지 않은 예약 거절 (%s): %s%n",
                        res.getReservationId(), result.getReason());
            }
        }
        return validList;
    }

    private boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}