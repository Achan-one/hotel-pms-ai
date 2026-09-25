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
    private final HotelOperationService hotelOperationService; // 👈 1. DB 영업일자 관리 서비스 주입

    public NightAuditService(ReservationRepository reservationRepository,
                             RoomRepository roomRepository,
                             HotelOperationService hotelOperationService) { // 👈 2. 생성자 파라미터 추가
        this.reservationRepository = Objects.requireNonNull(reservationRepository);
        this.roomRepository = Objects.requireNonNull(roomRepository);
        this.hotelOperationService = Objects.requireNonNull(hotelOperationService);
    }

    /**
     * [나이트 오딧 실행]
     * 1. 당일 노쇼(미체크인) 전산 처리 및 객실 스케줄 회수
     * 2. 인하우스(재실) 고객 1박 숙박료 청구원장(PaymentLedger) 자동 포스팅
     * 3. DB 시스템 영업일자 익일 롤오버 (영구 보존)
     */
    public NightAuditResult runNightAudit(LocalDate currentBusinessDate) {
        Objects.requireNonNull(currentBusinessDate, "영업일자는 필수입니다.");
        log.info("🌙 [Night Audit] 야간 일일 마감 시작 - 기준일: {}", currentBusinessDate);

        // 1. 노쇼 처리 (체크인 당일인데 아직 ASSIGNED 또는 PENDING 상태인 고객)
        List<Reservation> arrivals = reservationRepository.findByCheckInDate(currentBusinessDate);
        List<String> noShowIds = new ArrayList<>();

        for (Reservation rsv : arrivals) {
            if (rsv.getStatus() == ReservationStatus.ASSIGNED || rsv.getStatus() == ReservationStatus.PENDING) {
                String roomNo = rsv.getAssignedRoomNumber();
                if (roomNo != null) {
                    // 🔒 비관적 락을 통해 안전하게 스케줄 회수
                    roomRepository.findByRoomNumberForUpdate(roomNo).ifPresent(room -> {
                        StayPeriod period = new StayPeriod(rsv.getCheckInDate(), rsv.getStayNights());
                        room.cancelPeriod(period);
                        if (!room.isAssigned() && room.getStatus() == RoomStatus.ASSIGNED) {
                            room.setStatus(RoomStatus.VACANT);
                        }
                        roomRepository.save(room);
                    });
                }
                rsv.cancelReservation(); // 취소(노쇼) 처리
                reservationRepository.save(rsv);
                noShowIds.add(rsv.getReservationId());
                log.warn("⚠️ [No-Show] 노쇼 자동 취소 및 객실 회수 완료: {} ({})", rsv.getReservationId(), rsv.getGuestName());
            }
        }

        // 2. 인하우스 고객 1박 객실료 정산 가산
        List<Reservation> inHouseGuests = reservationRepository.search(
                ReservationSearchCondition.byStayingDate(currentBusinessDate)
        ).stream().filter(r -> r.getStatus() == ReservationStatus.CHECKED_IN).toList();

        int postedCount = 0;
        long totalRevenue = 0L;

        for (Reservation guest : inHouseGuests) {
            long dailyRate = switch (guest.getBookedRoomType()) {
                case MODERATE_DOUBLE -> 12_000L;
                case SUPERIOR_DOUBLE -> 15_000L;
                case SUPERIOR_TWIN -> 16_000L;
                case RESIDENTIAL_DOUBLE -> 18_000L;
                case EXECUTIVE_DOUBLE -> 28_000L;
            };

            guest.getPaymentLedger().addCharge(dailyRate);
            reservationRepository.save(guest);
            totalRevenue += dailyRate;
            postedCount++;
        }

        // 3. 🚀 DB 시스템 영업일자 익일 롤오버 (DB 단일 진실 공급원 전진 및 영구 저장)
        LocalDate nextBusinessDate = hotelOperationService.rolloverToNextDate();

        log.info("✅ [Night Audit] 마감 완료 - 노쇼: {}건, 룸차지 포스팅: {}실(총 ¥{}), 롤오버: {} -> {}",
                noShowIds.size(), postedCount, totalRevenue, currentBusinessDate, nextBusinessDate);

        return new NightAuditResult(
                currentBusinessDate,
                nextBusinessDate,
                noShowIds.size(),
                noShowIds,
                postedCount,
                totalRevenue,
                true,
                String.format("나이트 오딧이 성공적으로 완료되었습니다. (다음 영업일자: %s)", nextBusinessDate)
        );
    }
}