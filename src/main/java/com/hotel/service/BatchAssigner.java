package com.hotel.service;

import com.hotel.domain.Reservation;
import com.hotel.domain.Room;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BatchAssigner {

    private final RoomAssigner roomAssigner;

    // 1순위: 숙박 일수 내림차순 (연박 우선)
    // 2순위: 선호도 제약 조건 개수 내림차순 (까다로운 조건 우선)
    // 3순위: 예약 번호 오름차순 (FIFO 기본 보장)
    private static final Comparator<Reservation> RESERVATION_PRIORITY_COMPARATOR = Comparator
            .comparingInt(Reservation::getStayNights).reversed()
            .thenComparing((Reservation r) -> r.getPreference().getConstraintCount(), Comparator.reverseOrder())
            .thenComparing(Reservation::getReservationId);

    public BatchAssigner(RoomAssigner roomAssigner) {
        this.roomAssigner = Objects.requireNonNull(roomAssigner, "roomAssigner는 필수입니다.");
    }

    /**
     * 대량의 예약 목록을 우선순위 규칙에 맞춰 정렬한 뒤 일괄 배정합니다.
     *
     * @param reservations 당일 배정 대상 예약 목록
     * @return 성공 및 실패 목록을 포함한 BatchAssignmentResult
     */
    public BatchAssignmentResult assignAll(List<Reservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return new BatchAssignmentResult(List.of(), List.of());
        }

        // 원본 리스트 불변성 유지를 위해 복사 후 우선순위 정렬
        List<Reservation> prioritizedQueue = new ArrayList<>(reservations);
        prioritizedQueue.sort(RESERVATION_PRIORITY_COMPARATOR);

        List<Reservation> successes = new ArrayList<>();
        List<Reservation> failures = new ArrayList<>();

        for (Reservation reservation : prioritizedQueue) {
            Optional<Room> assignedRoom = roomAssigner.assign(reservation);
            if (assignedRoom.isPresent()) {
                successes.add(reservation);
            } else {
                failures.add(reservation);
            }
        }

        return new BatchAssignmentResult(successes, failures);
    }
}