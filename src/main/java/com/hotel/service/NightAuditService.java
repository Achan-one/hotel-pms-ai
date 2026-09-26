package com.hotel.service;

import com.hotel.domain.Reservation;
import com.hotel.domain.ReservationStatus;
import com.hotel.domain.RoomStatus;
import com.hotel.domain.StayPeriod;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.service.dto.NightAuditResult;
import com.hotel.service.dto.ReservationSearchCondition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional
public class NightAuditService {

    private static final Logger log = LoggerFactory.getLogger(NightAuditService.class);

    private final ReservationRepository reservationRepository;
    private final RoomRepository roomRepository;
    private final HotelOperationService hotelOperationService;

    public NightAuditService(ReservationRepository reservationRepository,
                             RoomRepository roomRepository,
                             HotelOperationService hotelOperationService) {
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
        this.roomRepository = Objects.requireNonNull(roomRepository);
        this.hotelOperationService = Objects.requireNonNull(hotelOperationService);
    }

    /**
     * 당일 체크인 예정이나 아직 미체크인 상태(PENDING, ASSIGNED)인 예약 목록 조회
     */
    @Transactional(readOnly = true)
    public List<Reservation> getUncheckedArrivals(LocalDate currentBusinessDate) {
        List<Reservation> arrivals = reservationRepository.findByCheckInDate(currentBusinessDate);
        return arrivals.stream()
                .filter(r -> r.getStatus() == ReservationStatus.PENDING || r.getStatus() == ReservationStatus.ASSIGNED)
                .toList();
    }

    /**
     * 🚀 [0박 지원] 미체크인 예약 익일 이월 (1박 차감)
     * - 2박 이상 -> 1박 차감 후 익일 체크인으로 이월
     * - 1박 단박 -> 취소하지 않고 '0박 (새벽 체크인 / 당일 아웃)'으로 전환하여 객실 유지!
     * - 이미 0박이었던 예약만 다음 날 마감 시 최종 노쇼(CANCELLED) 처리
     */
    public int rolloverUncheckedArrivals(LocalDate currentBusinessDate) {
        List<Reservation> uncheckedList = getUncheckedArrivals(currentBusinessDate);
        int processedCount = 0;
        LocalDate nextDate = currentBusinessDate.plusDays(1);

        for (Reservation rsv : uncheckedList) {
            String roomNo = rsv.getAssignedRoomNumber();

            if (rsv.getStayNights() == 0) {
                // 이미 0박으로 전날 이월되었음에도 끝내 미도착한 경우에만 최종 노쇼 취소 및 방 회수
                if (roomNo != null) {
                    roomRepository.findByRoomNumberForUpdate(roomNo).ifPresent(room -> {
                        if (!room.isAssigned() && room.getStatus() == RoomStatus.ASSIGNED) {
                            room.setStatus(RoomStatus.VACANT);
                        }
                        roomRepository.save(room);
                    });
                }
                rsv.cancelReservation();
                reservationRepository.save(rsv);
                log.warn("⚠️ [No-Show] 0박 최종 미도착 예약 노쇼 취소 완료: {} ({})", rsv.getReservationId(), rsv.getGuestName());
            } else {
                // 1박 및 연박 예약: 1박 차감 (1박 -> 0박, 2박 -> 1박)
                int newNights = rsv.getStayNights() - 1;

                if (roomNo != null) {
                    roomRepository.findByRoomNumberForUpdate(roomNo).ifPresent(room -> {
                        StayPeriod oldPeriod = new StayPeriod(rsv.getCheckInDate(), rsv.getStayNights());
                        room.cancelPeriod(oldPeriod);

                        // 1박 이상 잔여 시에만 룸 랙 일정 예약 등록 (0박은 당일 아침 퇴실이므로 야간 룸 랙 슬롯 점유 불필요)
                        if (newNights > 0) {
                            StayPeriod newPeriod = new StayPeriod(nextDate, newNights);
                            if (room.isAvailable(newPeriod)) {
                                room.bookPeriod(newPeriod);
                            } else {
                                rsv.cancelAssignment();
                            }
                        }
                        roomRepository.save(room);
                    });
                }

                String memoSuffix = (newNights == 0)
                        ? " [나이트오딧 이월: 0박 새벽체크인/당일아웃]"
                        : " [나이트오딧 이월: 1박 차감]";

                rsv.updateOperationalDetails(
                        rsv.getGuestName(),
                        nextDate,
                        newNights,
                        (rsv.getInternalStaffMemo() != null ? rsv.getInternalStaffMemo() : "") + memoSuffix
                );

                reservationRepository.save(rsv);
                log.info("🔄 [Rollover] 미도착 예약 익일 이월 완료: {} (새 체크인: {}, 잔여: {}박, 객실: {}호 유지)",
                        rsv.getReservationId(), nextDate, newNights, roomNo);
            }
            processedCount++;
        }

        return processedCount;
    }

    /**
     * 나이트 오딧 실행
     */
    public NightAuditResult runNightAudit(LocalDate currentBusinessDate) {
        Objects.requireNonNull(currentBusinessDate, "영업일자는 필수입니다.");
        log.info("🌙 [Night Audit] 야간 일일 마감 시작 - 기준일: {}", currentBusinessDate);

        // 1. 선행 방어: 미체크인 당일 예약 검증
        List<Reservation> unchecked = getUncheckedArrivals(currentBusinessDate);
        if (!unchecked.isEmpty()) {
            throw new IllegalStateException(String.format(
                    "당일 도착 예정인 미체크인 예약이 %d건 남아있어 나이트 오딧을 진행할 수 없습니다. " +
                            "체크인을 완료하거나 익일 이월(0박/1박 차감)을 먼저 진행하세요.", unchecked.size()));
        }

        // 2. 인하우스 고객 1박 객실료 정산 가산
        List<Reservation> inHouseGuests = reservationRepository.search(
                ReservationSearchCondition.byStayingDate(currentBusinessDate)
        ).stream().filter(r -> r.getStatus() == ReservationStatus.CHECKED_IN).toList();

        int postedCount = 0;
        long totalRevenue = 0L;

        for (Reservation guest : inHouseGuests) {
            long dailyRate = guest.getDailyRateSchedule().getRateForDate(currentBusinessDate);
            if (dailyRate <= 0) {
                dailyRate = guest.getStayNights() > 0
                        ? (guest.getPaymentLedger().getTotalCharges() / guest.getStayNights())
                        : 15_000L;
            }

            guest.getPaymentLedger().postRoomCharge(dailyRate);
            reservationRepository.save(guest);
            totalRevenue += dailyRate;
            postedCount++;
        }

        // 3. DB 시스템 영업일자 익일 롤오버
        LocalDate nextBusinessDate = hotelOperationService.rolloverToNextDate();

        log.info("✅ [Night Audit] 마감 완료 - 룸차지 포스팅: {}실(총 ¥{}), 롤오버: {} -> {}",
                postedCount, totalRevenue, currentBusinessDate, nextBusinessDate);

        return new NightAuditResult(
                currentBusinessDate,
                nextBusinessDate,
                0,
                List.of(),
                postedCount,
                totalRevenue,
                true,
                String.format("나이트 오딧이 성공적으로 완료되었습니다. (다음 영업일자: %s)", nextBusinessDate)
        );
    }
}