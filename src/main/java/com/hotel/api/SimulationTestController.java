package com.hotel.api;

import com.hotel.channel.dto.ChannelReservationRequest;
import com.hotel.channel.tlx.TlxChannelAdapter;
import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.service.HotelOperationService;
import com.hotel.service.ReservationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
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
    private final HotelOperationService hotelOperationService;

    private static final String[] UNIQUE_GUEST_NAMES = {
            "Tanaka Kenji", "Sato Sakura", "Suzuki Ichiro", "Takahashi Misaki", "Watanabe Ren",
            "Ito Daiki", "Yamamoto Aoi", "Nakamura Yuto", "Kobayashi Hina", "Kato Sota",
            "Yoshida Mei", "Yamada Haruto", "Sasaki Kaito", "Yamaguchi Nanami", "Matsumoto Riku",
            "Inoue Yuna", "Kimura Sora", "Hayashi Koharu", "Saito Hinata", "Shimizu Yuma",
            "Yamazaki Akari", "Mori Takumi", "Abe Rio", "Ikeda Asahi", "Hashimoto Yuzuki",
            "Ishikawa Shoma", "Ogawa Rin", "Maeda Minato", "Fujita Ema", "Okada Haruki",
            "Goto Mio", "Hasegawa Hayato", "Murakami Yuka", "Kondo Ryuto", "Ishii Kohana",
            "Sakamoto Reo", "Endo Sana", "Aoki Taiga", "Fujii Yua", "Nishimura Koki",
            "Fukuda Sara", "Ota Yamato", "Miura Kanna", "Fujiwara Ryusei", "Okamoto Riko",
            "Matsuda Ayato", "Nakagawa Shiori", "Nakano Souta", "Harada Tsubaki", "Ono Taichi"
    };

    private static final BookingChannelInfo.ChannelType[] OTA_TYPES = {
            BookingChannelInfo.ChannelType.AGODA,
            BookingChannelInfo.ChannelType.BOOKING_COM,
            BookingChannelInfo.ChannelType.EXPEDIA,
            BookingChannelInfo.ChannelType.RAKUTEN,
            BookingChannelInfo.ChannelType.TRIP_COM
    };

    public SimulationTestController(ReservationRepository reservationRepository,
                                    ReservationService reservationService,
                                    RoomRepository roomRepository,
                                    TlxChannelAdapter tlxAdapter,
                                    HotelOperationService hotelOperationService) {
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
        this.roomRepository = roomRepository;
        this.tlxAdapter = tlxAdapter;
        this.hotelOperationService = hotelOperationService;
    }

    // 🚀 DTO 본문에 targetDate 추가
    public record BulkSimulationRequest(List<String> customNotes, String targetDate) {}

    /**
     * 1. 시스템 현재 공식 영업일자 기준 기본 샘플 예약 5건 주입
     */
    @PostMapping("/seed-samples")
    public ResponseEntity<?> seedSampleReservations(@RequestParam(required = false) String targetDate) {
        LocalDate currentBusinessDate = (targetDate != null && !targetDate.isBlank())
                ? LocalDate.parse(targetDate.trim())
                : hotelOperationService.getCurrentBusinessDate();

        Reservation r1 = new Reservation("RSV-TEST-01", "Tanaka Kenji", RoomType.SUPERIOR_TWIN, currentBusinessDate, 2, "고층 희망", GuestPreference.empty());
        r1.assignRoom("0501");
        reservationRepository.save(r1);

        Reservation r2 = new Reservation("RSV-TEST-02", "Sato Yuki", RoomType.MODERATE_DOUBLE, currentBusinessDate, 3, "조용한 방", GuestPreference.empty());
        r2.assignRoom("0302");
        r2.checkIn();
        reservationRepository.save(r2);
        roomRepository.findByRoomNumber("0302").ifPresent(r -> r.setStatus(RoomStatus.OCCUPIED));

        Reservation r3 = new Reservation("RSV-TEST-03", "Kim Minsoo", RoomType.SUPERIOR_DOUBLE, currentBusinessDate, 1, "엘리베이터 근처", GuestPreference.empty());
        reservationRepository.save(r3);

        Reservation r4 = new Reservation("RSV-TEST-04", "John Smith", RoomType.SUPERIOR_TWIN, currentBusinessDate, 4, "연박", GuestPreference.empty());
        reservationRepository.save(r4);

        Reservation r5 = new Reservation("RSV-TEST-05", "Lee Jinwoo", RoomType.EXECUTIVE_DOUBLE, currentBusinessDate, 2, "최고층 선호", GuestPreference.empty());
        reservationRepository.save(r5);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", String.format("공식 영업일자(%s) 기준 검증용 샘플 예약 5건 주입 완료", currentBusinessDate)
        ));
    }

    /**
     * 2. TL-Lincoln(린칸) XML 전문 인입 시뮬레이션
     */
    @PostMapping("/lincoln-mock")
    public ResponseEntity<?> simulateLincolnXml(@RequestParam(required = false) String targetDate) {
        LocalDate currentBusinessDate = (targetDate != null && !targetDate.isBlank())
                ? LocalDate.parse(targetDate.trim())
                : hotelOperationService.getCurrentBusinessDate();

        String mockXml = String.format("""
            <TL_Reservations>
              <Reservation>
                <ReservationId>LNC-%s-99</ReservationId>
                <GuestName>Yamamoto Daiki</GuestName>
                <RoomType>SUPERIOR_TWIN</RoomType>
                <CheckInDate>%s</CheckInDate>
                <StayNights>2</StayNights>
                <SpecialRequest>금연실 및 저층 선호</SpecialRequest>
              </Reservation>
            </TL_Reservations>
            """, currentBusinessDate.toString().replace("-", ""), currentBusinessDate);

        List<ChannelReservationRequest> requests = tlxAdapter.parseIncomingRequests(mockXml);
        reservationService.processChannelRequests(requests);
        return ResponseEntity.ok(Map.of("success", true, "message", "TL-Lincoln XML 예약 전문 인입 및 동기화 완료"));
    }

    /**
     * 3. [개선] 50인 실명 + 6대 OTA 채널 + 당일 날짜 완벽 연동 대량 인입
     */
    @PostMapping("/bulk-simulate-50-and-30")
    public ResponseEntity<?> bulkSimulate50And30(@RequestBody(required = false) BulkSimulationRequest request) {

        // 🚀 DTO의 targetDate 확인 -> 없으면 DB의 공식 영업일자 사용
        LocalDate currentBusinessDate;
        if (request != null && request.targetDate() != null && !request.targetDate().isBlank()) {
            currentBusinessDate = LocalDate.parse(request.targetDate().trim());
        } else {
            currentBusinessDate = hotelOperationService.getCurrentBusinessDate();
        }

        // 1. 이미 투숙 중인 인하우스 30건 생성 (어제 입실해서 오늘 체류 중인 상태)
        List<Room> allRooms = roomRepository.findAll();
        int inHouseRegistered = 0;

        for (int i = 1; i <= 30; i++) {
            if (i > allRooms.size()) break;
            Room room = allRooms.get(i - 1);

            String rsvId = String.format("STAY-INHOUSE-%03d", i);
            String guestName = UNIQUE_GUEST_NAMES[(i - 1) % UNIQUE_GUEST_NAMES.length];

            // 어제 체크인 ~ 3박 투숙 (오늘 재실)
            StayPeriod stayPeriod = new StayPeriod(currentBusinessDate.minusDays(1), 3);
            if (room.isAvailable(stayPeriod)) {
                room.bookPeriod(stayPeriod);
                room.setStatus(RoomStatus.OCCUPIED);

                BookingChannelInfo channelInfo = new BookingChannelInfo(
                        BookingChannelInfo.ChannelType.DIRECT,
                        "DIR-" + rsvId,
                        "호텔 공식 프론트 현장 일반 플랜"
                );

                Reservation inHouseRes = new Reservation(
                        rsvId, guestName, room.getRoomType(),
                        currentBusinessDate.minusDays(1), 3, 1,
                        "기존 투숙 중인 고객", null,
                        GuestPreference.empty(), TagPreference.empty(),
                        channelInfo, BreakfastOption.included(1),
                        new PaymentLedger(PaymentLedger.PaymentType.PREPAID, 45000L),
                        LocalTime.of(15, 0)
                );

                inHouseRes.assignRoom(room.getRoomNumber());
                inHouseRes.checkIn();
                reservationRepository.save(inHouseRes);
                inHouseRegistered++;
            }
        }

        // 2. 기본 요구사항 6개 풀
        List<String> combinedNotes = new ArrayList<>(List.of(
                "어머니 무릎이 안 좋으셔서 엘리베이터 가깝고 낮은 층으로 부탁드립니다.",
                "High floor with a nice Tokyo Tower view please!",
                "조용한 안쪽 방으로 주세요.",
                "도쿄타워 보이는 방으로 꼭 부탁드립니다.",
                "아기 동반이라 소음 없는 방 원합니다.",
                ""
        ));

        if (request != null && request.customNotes() != null) {
            for (String cn : request.customNotes()) {
                if (cn != null && !cn.trim().isBlank()) {
                    combinedNotes.add(cn.trim());
                }
            }
        }

        int totalNotePoolSize = combinedNotes.size();

        // 3. 당일(currentBusinessDate) 체크인 신규 50건 생성
        List<Reservation> newBookings = new ArrayList<>();
        RoomType[] types = RoomType.values();
        Random rand = new Random(currentBusinessDate.toEpochDay());

        for (int i = 0; i < 50; i++) {
            String rsvId = String.format("BULK-CMS-%03d", i + 1);
            String guestName = UNIQUE_GUEST_NAMES[i];
            BookingChannelInfo.ChannelType channelType = OTA_TYPES[rand.nextInt(OTA_TYPES.length)];
            RoomType type = types[rand.nextInt(types.length)];
            int nights = rand.nextInt(5) + 1;
            String note = combinedNotes.get(i % totalNotePoolSize);

            long baseRate = switch (type) {
                case MODERATE_DOUBLE -> 12_000L;
                case SUPERIOR_DOUBLE -> 15_000L;
                case SUPERIOR_TWIN -> 16_000L;
                case RESIDENTIAL_DOUBLE -> 18_000L;
                case EXECUTIVE_DOUBLE -> 28_000L;
            };
            long totalRoomCharge = baseRate * nights;

            BookingChannelInfo channelInfo = new BookingChannelInfo(
                    channelType,
                    channelType.name() + "-RSV-" + (100000 + i),
                    "【" + channelType.getDescription() + "】 조식 포함 특가 스탠다드 프로모션"
            );

            BreakfastOption breakfastOption = (i % 2 == 0) ? BreakfastOption.included(1) : BreakfastOption.none();
            PaymentLedger.PaymentType pType = (i % 3 == 0) ? PaymentLedger.PaymentType.PAY_ON_ARRIVAL : PaymentLedger.PaymentType.PREPAID;
            PaymentLedger paymentLedger = new PaymentLedger(pType, totalRoomCharge);

            Reservation newRes = new Reservation(
                    rsvId, guestName, type,
                    currentBusinessDate, nights, 1,
                    note, null,
                    GuestPreference.empty(), TagPreference.empty(),
                    channelInfo, breakfastOption, paymentLedger,
                    LocalTime.of(15 + (i % 6), 0)
            );

            newRes.updateOperationalDetails(guestName, currentBusinessDate, nights, note);
            newBookings.add(newRes);
        }

        reservationRepository.saveAll(newBookings);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", String.format("공식 영업일자(%s) 기준 50건 신규 인입 및 재실 %d실 동기화 완료!", currentBusinessDate, inHouseRegistered)
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