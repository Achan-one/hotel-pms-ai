package com.hotel.service;

import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.QuotaPolicy;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.service.dto.AssignmentAlert;

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

    // BatchAssigner.java 생성자 부분
    public BatchAssigner(RoomRepository roomRepository, QuotaPolicy quotaPolicy) {
        this(new RoomAssigner(roomRepository, new TagRepository(), quotaPolicy));
    }

    public BatchAssigner(RoomRepository roomRepository, TagRepository tagRepository, QuotaPolicy quotaPolicy) {
        this(new RoomAssigner(roomRepository, tagRepository, quotaPolicy));
    }

    public BatchAssignmentResult assignAll(List<Reservation> reservations) {
        if (reservations == null || reservations.isEmpty()) {
            return new BatchAssignmentResult(List.of(), List.of(), List.of());
        }

        List<Reservation> prioritizedQueue = new ArrayList<>(reservations);
        prioritizedQueue.sort(RESERVATION_PRIORITY_COMPARATOR);

        List<Reservation> successes = new ArrayList<>();
        List<BatchAssignmentResult.FailedAssignmentItem> failures = new ArrayList<>();
        List<AssignmentAlert> alerts = new ArrayList<>();

        for (Reservation reservation : prioritizedQueue) {
            Optional<Room> assignedRoom = roomAssigner.assign(reservation);
            if (assignedRoom.isPresent()) {
                successes.add(reservation);

                // [신규] 하드 리퀘스트 미충족 검출 시 Alert 리스트에 수집
                List<AssignmentAlert> roomAlerts = roomAssigner.checkHardRequestAlerts(reservation, assignedRoom.get());
                alerts.addAll(roomAlerts);
            } else {
                String reason = String.format("[%s] 타입 객실 인벤토리 소진 (만실/홀딩)", reservation.getBookedRoomType().getDescription());
                failures.add(new BatchAssignmentResult.FailedAssignmentItem(reservation, reason));
            }
        }

        return new BatchAssignmentResult(successes, failures, alerts);
    }

    public RoomAssigner getRoomAssigner() {
        return roomAssigner;
    }
}