package com.hotel.api;

import com.hotel.channel.ChannelManagerAdapter;
import com.hotel.channel.dto.ChannelReservationRequest;
import com.hotel.channel.tlx.TlxChannelAdapter;
import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/simulation")
public class SimulationTestController {

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;
    private final RoomRepository roomRepository;
    private final TlxChannelAdapter tlxAdapter;

    public SimulationTestController(ReservationRepository reservationRepository,
                                    ReservationService reservationService,
                                    RoomRepository roomRepository,
                                    TlxChannelAdapter tlxAdapter) {
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
        this.roomRepository = roomRepository;
        this.tlxAdapter = tlxAdapter;
    }

    /**
     * 1. 2026-09-20 기준 프론트 검증용 기본 샘플 예약 5건 주입
     */
    @PostMapping("/seed-samples")
    public ResponseEntity<?> seedSampleReservations() {
        LocalDate target = LocalDate.of(2026, 9, 20);

        // 1) 배정 완료 건 (체크인 테스트 가능)
        Reservation r1 = new Reservation("RSV-TEST-01", "Tanaka Kenji", RoomType.SUPERIOR_TWIN, target, 2, "고층 희망", GuestPreference.empty());
        r1.assignRoom("0501");
        reservationRepository.save(r1);

        // 2) 이미 체크인되어 재실 중인 건 (룸 체인지 테스트 가능)
        Reservation r2 = new Reservation("RSV-TEST-02", "Sato Yuki", RoomType.MODERATE_DOUBLE, target, 3, "조용한 방", GuestPreference.empty());
        r2.assignRoom("0302");
        r2.checkIn();
        reservationRepository.save(r2);
        roomRepository.findByRoomNumber("0302").ifPresent(r -> r.setStatus(com.hotel.domain.RoomStatus.OCCUPIED));

        // 3) 미배정 상태 건 (자동 일괄 배정 테스트 가능)
        Reservation r3 = new Reservation("RSV-TEST-03", "Kim Minsoo", RoomType.SUPERIOR_DOUBLE, target, 1, "엘리베이터 근처", GuestPreference.empty());
        reservationRepository.save(r3);

        // 4) 미배정 상태 건 2
        Reservation r4 = new Reservation("RSV-TEST-04", "John Smith", RoomType.SUPERIOR_TWIN, target, 4, "연박", GuestPreference.empty());
        reservationRepository.save(r4);

        // 5) 고층 이그제큐티브 미배정 건
        Reservation r5 = new Reservation("RSV-TEST-05", "Lee Jinwoo", RoomType.EXECUTIVE_DOUBLE, target, 2, "최고층 선호", GuestPreference.empty());
        reservationRepository.save(r5);

        return ResponseEntity.ok(Map.of("success", true, "message", "검증용 샘플 예약 5건 주입 완료"));
    }

    /**
     * 2. TL-Lincoln(린칸) XML 전문 인입 시뮬레이션
     */
    @PostMapping("/lincoln-mock")
    public ResponseEntity<?> simulateLincolnXml() {
        String mockXml = """
            <TL_Reservations>
              <Reservation>
                <ReservationId>LNC-20260920-99</ReservationId>
                <GuestName>Yamamoto Daiki</GuestName>
                <RoomType>SUPERIOR_TWIN</RoomType>
                <CheckInDate>2026-09-20</CheckInDate>
                <StayNights>2</StayNights>
                <SpecialRequest>금연실 및 저층 선호</SpecialRequest>
              </Reservation>
            </TL_Reservations>
            """;
        List<ChannelReservationRequest> requests = tlxAdapter.parseIncomingRequests(mockXml);
        reservationService.processChannelRequests(requests);
        return ResponseEntity.ok(Map.of("success", true, "message", "TL-Lincoln XML 예약 전문 인입 및 동기화 완료"));
    }

    /**
     * 3. 예약 전체 초기화
     */
    @PostMapping("/clear")
    public ResponseEntity<?> clearAll() {
        reservationRepository.clear();
        return ResponseEntity.ok(Map.of("success", true, "message", "모든 예약 데이터가 초기화되었습니다."));
    }
}