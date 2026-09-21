package com.hotel.service;

import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.TagQuotaPolicy;
import com.hotel.repository.RoomRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class BatchAssigner {

    private final RoomAssigner roomAssigner;

    private static final Comparator<Reservation> RESERVATION_PRIORITY_COMPARATOR = Comparator
            .comparingInt(Reservation::getStayNights).reversed()
            .thenComparing((Reservation r) -> r.getPreference().getConstraintCount(), Comparator.reverseOrder())
            .thenComparing(Reservation::getReservationId);

    public BatchAssigner(RoomAssigner roomAssigner) {
        this.roomAssigner = Objects.requireNonNull(roomAssigner, "roomAssigner는 필수입니다.");
    }

    // [신규 편의 생성자] RoomRepository와 TagQuotaPolicy 주입 지원
    public BatchAssigner(RoomRepository roomRepository, TagQuotaPolicy tagQuotaPolicy) {
        this(new RoomAssigner(roomRepository, tagQuotaPolicy));
    }

    public BatchAssignmentResult assignAll(List<Reservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return new BatchAssignmentResult(List.of(), List.of());
        }

        List<Reservation> prioritizedQueue = new ArrayList<>(reservations);
        prioritizedQueue.sort(RESERVATION_PRIORITY_COMPARATOR);

        List<Reservation> successes = new ArrayList<>();
        List<BatchAssignmentResult.FailedAssignmentItem> failures = new ArrayList<>();

        for (Reservation reservation : prioritizedQueue) {
            Optional<Room> assignedRoom = roomAssigner.assign(reservation);
            if (assignedRoom.isPresent()) {
                successes.add(reservation);
            } else {
                String reason = String.format("[%s] 타입 객실 인벤토리 소진 (만실/홀딩)", reservation.getBookedRoomType().getDescription());
                failures.add(new BatchAssignmentResult.FailedAssignmentItem(reservation, reason));
            }
        }

        return new BatchAssignmentResult(successes, failures);
    }

    public RoomAssigner getRoomAssigner() {
        return roomAssigner;
    }
}