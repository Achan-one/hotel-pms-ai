package com.hotel.service;

import com.hotel.domain.QuotaPolicy;
import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.RoomType;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.service.dto.AssignmentAlert;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        Map<RoomType, Integer> quotaBlockedCountMap = new HashMap<>();

        for (Reservation reservation : prioritizedQueue) {
            Optional<Room> assignedRoom = roomAssigner.assign(reservation);

            if (assignedRoom.isPresent()) {
                successes.add(reservation);
                List<AssignmentAlert> roomAlerts = roomAssigner.checkHardRequestAlerts(reservation, assignedRoom.get());
                alerts.addAll(roomAlerts);
            } else {
                RoomType bookedType = reservation.getBookedRoomType();
                // [논리 오류 수정] 일자별 병목 공실 기준으로 쿼터 차단 여부 판정
                long minDailyVacant = roomAssigner.calculateMinDailyVacant(
                        bookedType, reservation.getCheckInDate(), reservation.getStayNights()
                );

                int holdQuota = roomAssigner.getQuotaPolicy().getTypeHoldQuota(bookedType);
                int alreadyBlockedCount = quotaBlockedCountMap.getOrDefault(bookedType, 0);

                String failureReason;
                if (minDailyVacant > 0 && alreadyBlockedCount < holdQuota) {
                    quotaBlockedCountMap.put(bookedType, alreadyBlockedCount + 1);
                    failureReason = String.format("물리적 공실(%d실)이 존재하나 안전 쿼터(Hold: %d실)에 의해 차단됨 (우선 구제 후보)",
                            minDailyVacant, holdQuota);
                } else {
                    failureReason = String.format("[%s] 인벤토리 전량 소진 및 가용 공실 매진", bookedType.getDescription());
                }

                failures.add(new BatchAssignmentResult.FailedAssignmentItem(reservation, failureReason));
            }
        }

        return new BatchAssignmentResult(successes, failures, alerts);
    }

    public RoomAssigner getRoomAssigner() {
        return roomAssigner;
    }
}