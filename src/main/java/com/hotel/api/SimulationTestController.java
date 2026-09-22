package com.hotel.api;

import com.hotel.channel.dto.ChannelReservationRequest;
import com.hotel.channel.tlx.TlxChannelAdapter;
import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.service.ReservationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

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

        // 7개 인자 생성자 활용: (id, name, type, checkIn, nights, memo, pref)
        Reservation r1 = new Reservation("RSV-TEST-01", "Tanaka Kenji", RoomType.SUPERIOR_TWIN, target, 2, "고층 희망", GuestPreference.empty());
        r1.assignRoom("0501");
        reservationRepository.save(r1);

        Reservation r2 = new Reservation("RSV-TEST-02", "Sato Yuki", RoomType.MODERATE_DOUBLE, target, 3, "조용한 방", GuestPreference.empty());
        r2.assignRoom("0302");
        r2.checkIn();
        reservationRepository.save(r2);
        roomRepository.findByRoomNumber("0302").ifPresent(r -> r.setStatus(RoomStatus.OCCUPIED));

        Reservation r3 = new Reservation("RSV-TEST-03", "Kim Minsoo", RoomType.SUPERIOR_DOUBLE, target, 1, "엘리베이터 근처", GuestPreference.empty());
        reservationRepository.save(r3);

        Reservation r4 = new Reservation("RSV-TEST-04", "John Smith", RoomType.SUPERIOR_TWIN, target, 4, "연박", GuestPreference.empty());
        reservationRepository.save(r4);

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
     * [신규] 3. 신규 예약 50건 인입 + 이미 투숙 중인(In-House) 예약 30건 대량 등록
     */
    @PostMapping("/bulk-simulate-50-and-30")
    public ResponseEntity<?> bulkSimulate50And30() {
        LocalDate today = LocalDate.of(2026, 9, 20);

        // 1. 이미 투숙 중인(In-House) 예약 30건 생성 및 장부 적재 (실물 방 점유 동기화)
        List<Room> allRooms = roomRepository.findAll();
        int inHouseRegistered = 0;

        for (int i = 1; i <= 30; i++) {
            if (i > allRooms.size()) break;
            Room room = allRooms.get(i - 1);

            String rsvId = String.format("STAY-INHOUSE-%03d", i);
            String guestName = "InHouse_Guest_" + i;

            StayPeriod stayPeriod = new StayPeriod(today.minusDays(1), 3);
            if (room.isAvailable(stayPeriod)) {
                room.bookPeriod(stayPeriod);
                room.setStatus(RoomStatus.OCCUPIED);

                // 7개 인자 생성자 활용
                Reservation inHouseRes = new Reservation(
                        rsvId, guestName, room.getRoomType(),
                        today.minusDays(1), 3,
                        "기존 투숙 중인 고객", GuestPreference.empty()
                );
                inHouseRes.assignRoom(room.getRoomNumber());
                inHouseRes.checkIn();
                reservationRepository.save(inHouseRes);
                inHouseRegistered++;
            }
        }

        // 2. 신규 예약 50건 채널 인입 생성 (미배정 PENDING 상태)
        List<Reservation> newBookings = new ArrayList<>();
        RoomType[] types = RoomType.values();
        Random rand = new Random(2026);

        String[] sampleNotes = {
                "어머니 무릎이 안 좋으셔서 엘리베이터 가깝고 낮은 층으로 부탁드립니다.",
                "High floor with a nice Tokyo Tower view please!",
                "조용한 안쪽 방으로 주세요.",
                "도쿄타워 보이는 방으로 꼭 부탁드립니다.",
                "아기 동반이라 소음 없는 방 원합니다.",
                ""
        };

        for (int i = 1; i <= 50; i++) {
            String rsvId = String.format("NEW-CMS-%03d", i);
            String guestName = "New_Guest_" + i;
            RoomType type = types[rand.nextInt(types.length)];
            int nights = rand.nextInt(5) + 1;
            String note = sampleNotes[i % sampleNotes.length];

            // 7개 인자 생성자 활용
            Reservation newRes = new Reservation(
                    rsvId, guestName, type,
                    today, nights,
                    note, GuestPreference.empty()
            );
            newBookings.add(newRes);
        }

        reservationRepository.saveAll(newBookings);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", String.format("재실 투숙 중인 예약 %d건 등록 완료 및 신규 채널 예약 50건 인입 완료!", inHouseRegistered)
        ));
    }

    /**
     * 4. 예약 및 객실 물리 상태 완전 초기화
     */
    @PostMapping("/clear")
    public ResponseEntity<?> clearAll() {
        reservationRepository.clear();

        for (Room room : roomRepository.findAll()) {
            room.release();

            try {
                java.lang.reflect.Field statusField = Room.class.getDeclaredField("status");
                statusField.setAccessible(true);
                statusField.set(room, RoomStatus.VACANT);
            } catch (Exception ignored) {
            }
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "모든 예약 및 객실 191실이 완전한 공실(VACANT)로 초기화되었습니다."));
    }
}