package com.hotel.service.validator;

import com.hotel.domain.Reservation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReservationValidator {

    private static final int MAX_STAY_NIGHTS = 31; // 일반 예약 최대 연박 상한 (31박)
    private static final int MAX_REQUEST_TEXT_LENGTH = 500; // 요청 메모 길이 상한

    /**
     * 단일 예약 유효성 검증
     * @return 유효하면 null, 유효하지 않으면 거절 사유 문자열 반환
     */
    public String validateSingle(Reservation r) {
        if (r == null) {
            return "예약 객체 자체가 null입니다.";
        }

        if (r.getReservationId() == null || r.getReservationId().isBlank()) {
            return "예약 ID가 누락되었거나 공백입니다.";
        }

        if (r.getGuestName() == null || r.getGuestName().isBlank()) {
            return "투숙객 이름이 누락되었거나 공백입니다.";
        }

        if (r.getBookedRoomType() == null) {
            return "예약 객실 타입이 지정되지 않았습니다.";
        }

        if (r.getStayNights() < 1) {
            return "숙박 일수는 최소 1박 이상이어야 합니다 (입력값: " + r.getStayNights() + ").";
        }

        if (r.getStayNights() > MAX_STAY_NIGHTS) {
            return String.format("일반 예약 최대 숙박일수(%d박)를 초과했습니다 (입력값: %d박).", MAX_STAY_NIGHTS, r.getStayNights());
        }

        if (r.getRawRequestText() != null && r.getRawRequestText().length() > MAX_REQUEST_TEXT_LENGTH) {
            return String.format("요청 메모 길이가 제한(%d자)을 초과했습니다 (입력값: %d자).", MAX_REQUEST_TEXT_LENGTH, r.getRawRequestText().length());
        }

        return null; // 정상 통과
    }

    /**
     * 대량 예약 리스트 정합성 검증 (단일 검증 + 배치 내 ID 중복 체크)
     */
    public ValidationResult validateBatch(List<Reservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return new ValidationResult(List.of(), List.of());
        }

        List<Reservation> validList = new ArrayList<>();
        List<ValidationResult.RejectedReservation> rejectedList = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();

        for (Reservation r : reservations) {
            String singleCheckError = validateSingle(r);
            if (singleCheckError != null) {
                rejectedList.add(new ValidationResult.RejectedReservation(r, singleCheckError));
                continue;
            }

            // 배치 내 예약 ID 중복 여부 확인
            String rsvId = r.getReservationId().trim();
            if (seenIds.contains(rsvId)) {
                rejectedList.add(new ValidationResult.RejectedReservation(r, "동일 배치 내 중복 인입된 예약 ID입니다 (" + rsvId + ")."));
                continue;
            }

            seenIds.add(rsvId);
            validList.add(r);
        }

        return new ValidationResult(validList, rejectedList);
    }
}