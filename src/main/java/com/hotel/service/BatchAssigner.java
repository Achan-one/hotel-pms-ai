package com.hotel.service;

import com.hotel.domain.QuotaPolicy;
import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.RoomType;
import com.hotel.domain.StayPeriod;
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

        // 타입별로 쿼터 보존(Hold)에 의해 직접 차단된 건수를 카운트 (킵 N실당 최초 N건만 쿼터 차단 사유 부여)
        Map<RoomType, Integer> quotaBlockedCountMap = new HashMap<>();

        for (Reservation reservation : prioritizedQueue) {
            Optional<Room> assignedRoom = roomAssigner.assign(reservation);

            if (assignedRoom.isPresent()) {
                successes.add(reservation);

                // 필수 하드 리퀘스트 미충족 검출 시 Alert 수집
                List<AssignmentAlert> roomAlerts = roomAssigner.checkHardRequestAlerts(reservation, assignedRoom.get());
                alerts.addAll(roomAlerts);
            } else {
                StayPeriod targetPeriod = new StayPeriod(reservation.getCheckInDate(), reservation.getStayNights());
                RoomType bookedType = reservation.getBookedRoomType();

                // 해당 투숙 기간에 물리적으로 비어 있는 공실 수량 확인
                long physicalRemaining = roomAssigner.getRoomRepository().findAll().stream()
                        .filter(r -> r.getRoomType() == bookedType)
                        .filter(r -> r.isAvailable(targetPeriod))
                        .count();

                int holdQuota = roomAssigner.getQuotaPolicy().getTypeHoldQuota(bookedType);
                int alreadyBlockedCount = quotaBlockedCountMap.getOrDefault(bookedType, 0);

                String failureReason;
                // 물리적 공실이 존재하고, 아직 쿼터 킵 수량(N실) 내에서 직접 튕겨나간 최초 N명인 경우
                if (physicalRemaining > 0 && alreadyBlockedCount < holdQuota) {
                    quotaBlockedCountMap.put(bookedType, alreadyBlockedCount + 1);
                    failureReason = String.format("물리적 공실(%d실)이 존재하나 안전 쿼터(Hold: %d실)에 의해 차단됨 (우선 구제 후보)",
                            physicalRemaining, holdQuota);
                } else {
                    // 그 뒤에 줄 선 고객들은 가용 인벤토리 완전 매진
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