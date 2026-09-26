package com.hotel.api;

import com.hotel.channel.dto.ChannelReservationRequest;
import com.hotel.channel.tlx.TlxChannelAdapter;
import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.service.HotelOperationService;
import com.hotel.service.ReservationService;
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
    private final TagRepository tagRepository;
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
                                    TagRepository tagRepository,
                                    TlxChannelAdapter tlxAdapter,
                                    HotelOperationService hotelOperationService) {
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
        this.roomRepository = roomRepository;
        this.tagRepository = tagRepository;
        this.tlxAdapter = tlxAdapter;
        this.hotelOperationService = hotelOperationService;
    }

    public record BulkSimulationRequest(List<String> customNotes, String targetDate) {}

    /**
     * 1. 시스템 현재 공식 영업일자 기준 기본 샘플 예약 5건 주입
     */
    @PostMapping("/seed-samples")
    public ResponseEntity<?> seedSampleReservations(@RequestParam(required = false) String targetDate) {
        LocalDate currentBusinessDate = (targetDate != null && !targetDate.isBlank())
                ? LocalDate.parse(targetDate.trim())
                : hotelOperationService.getCurrentBusinessDate();

        String suffix = String.valueOf(System.currentTimeMillis() % 10000);

        Reservation r1 = new Reservation("RSV-TEST-01-" + suffix, "Tanaka Kenji", RoomType.SUPERIOR_TWIN, currentBusinessDate, 2, "고층 희망", GuestPreference.empty());
        reservationRepository.save(r1);

        Reservation r2 = new Reservation("RSV-TEST-02-" + suffix, "Sato Yuki", RoomType.MODERATE_DOUBLE, currentBusinessDate, 3, "조용한 방", GuestPreference.empty());
        reservationRepository.save(r2);

        Reservation r3 = new Reservation("RSV-TEST-03-" + suffix, "Kim Minsoo", RoomType.SUPERIOR_DOUBLE, currentBusinessDate, 1, "엘리베이터 근처", GuestPreference.empty());
        reservationRepository.save(r3);

        Reservation r4 = new Reservation("RSV-TEST-04-" + suffix, "John Smith", RoomType.SUPERIOR_TWIN, currentBusinessDate, 4, "연박", GuestPreference.empty());
        reservationRepository.save(r4);

        Reservation r5 = new Reservation("RSV-TEST-05-" + suffix, "Lee Jinwoo", RoomType.EXECUTIVE_DOUBLE, currentBusinessDate, 2, "최고층 선호", GuestPreference.empty());
        reservationRepository.save(r5);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", String.format("기준 일자(%s) 샘플 예약 5건 추가 적재 완료", currentBusinessDate)
        ));
    }

    /**
     * 2. TL-Lincoln XML 전문 인입
     */
    @PostMapping("/lincoln-mock")
    public ResponseEntity<?> simulateLincolnXml(@RequestParam(required = false) String targetDate) {
        LocalDate currentBusinessDate = (targetDate != null && !targetDate.isBlank())
                ? LocalDate.parse(targetDate.trim())
                : hotelOperationService.getCurrentBusinessDate();

        String uid = String.valueOf(System.currentTimeMillis() % 10000);
        String mockXml = String.format("""
            <TL_Reservations>
              <Reservation>
                <ReservationId>LNC-%s-%s</ReservationId>
                <GuestName>Yamamoto Daiki</GuestName>
                <RoomType>SUPERIOR_TWIN</RoomType>
                <CheckInDate>%s</CheckInDate>
                <StayNights>2</StayNights>
                <SpecialRequest>금연실 및 저층 선호</SpecialRequest>
              </Reservation>
            </TL_Reservations>
            """, currentBusinessDate.toString().replace("-", ""), uid, currentBusinessDate);

        List<ChannelReservationRequest> requests = tlxAdapter.parseIncomingRequests(mockXml);
        reservationService.processChannelRequests(requests);
        return ResponseEntity.ok(Map.of("success", true, "message", "TL-Lincoln XML 예약 전문 인입 및 추가 완료"));
    }

    /**
     * 3. [개선] 기존 데이터를 지우지 않고 누적(Append) 추가되는 50건 인입
     */
    @PostMapping("/bulk-simulate-50-and-30")
    public ResponseEntity<?> bulkSimulate50And30(@RequestBody(required = false) BulkSimulationRequest request) {
        LocalDate selectedDate;
        if (request != null && request.targetDate() != null && !request.targetDate().isBlank()) {
            selectedDate = LocalDate.parse(request.targetDate().trim());
        } else {
            selectedDate = hotelOperationService.getCurrentBusinessDate();
        }

        long batchTimestamp = System.currentTimeMillis();

        // 1. 기본 요구사항 풀 구성
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

        // 2. 선택한 날짜(selectedDate) 기준 신규 미배정 예약 50건 생성 및 누적 적재
        List<Reservation> newBookings = new ArrayList<>();
        RoomType[] types = RoomType.values();
        Random rand = new Random(batchTimestamp);

        for (int i = 0; i < 50; i++) {
            // 고유 식별자 발급 (덮어쓰기 방어)
            String rsvId = String.format("BULK-%s-%03d-%d", selectedDate.toString().replace("-", ""), i + 1, batchTimestamp % 10000);
            String guestName = UNIQUE_GUEST_NAMES[i % UNIQUE_GUEST_NAMES.length];
            BookingChannelInfo.ChannelType channelType = OTA_TYPES[rand.nextInt(OTA_TYPES.length)];
            RoomType type = types[rand.nextInt(types.length)];
            int nights = rand.nextInt(4) + 1; // 1~4박
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
                    channelType.name() + "-RSV-" + (batchTimestamp % 100000 + i),
                    "【" + channelType.getDescription() + "】 프로모션 패키지"
            );

            BreakfastOption breakfastOption = (i % 2 == 0) ? BreakfastOption.included(1) : BreakfastOption.none();
            PaymentLedger.PaymentType pType = (i % 3 == 0) ? PaymentLedger.PaymentType.PAY_ON_ARRIVAL : PaymentLedger.PaymentType.PREPAID;
            PaymentLedger paymentLedger = new PaymentLedger(pType, totalRoomCharge);

            Reservation newRes = new Reservation(
                    rsvId, guestName, type,
                    selectedDate, nights, 1,
                    note, null,
                    GuestPreference.empty(), TagPreference.empty(),
                    channelInfo, breakfastOption, paymentLedger,
                    LocalTime.of(15 + (i % 6), 0)
            );

            newRes.updateOperationalDetails(guestName, selectedDate, nights, note);
            newBookings.add(newRes);
        }

        reservationRepository.saveAll(newBookings);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", String.format("[%s] 체크인 기준 50건의 예약이 기존 원장에 성공적으로 누적 적재되었습니다! (총 누적 예약: %d건)",
                        selectedDate, reservationRepository.count())
        ));
    }

    /**
     * 4. 예약 원장 및 룸 점유만 초기화
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
            } catch (Exception ignored) {}
            roomRepository.save(room);
        }

        return ResponseEntity.ok(Map.of("success", true, "message", "모든 예약 데이터가 초기화되고 191실이 완전한 공실(VACANT)로 리셋되었습니다."));
    }

    /**
     * 5. [신규] 모든 설정 및 데이터 완벽 초기화 (Full Reset)
     * - 예약 전량 삭제
     * - 191실 공실화 및 커스텀 태그 초기화
     * - 기본 시스템 태그 7종 복원
     * - 시스템 공식 영업일자 기본값(2026-09-20) 롤백
     */
    @PostMapping("/reset-all-settings")
    public ResponseEntity<?> resetAllSettings() {
        // 1) 예약 삭제
        reservationRepository.clear();

        // 2) 시스템 기본 태그 목록 외 커스텀 태그 전량 삭제
        List<RoomTag> currentTags = tagRepository.findAll();
        for (RoomTag tag : currentTags) {
            if (!tag.isSystemDefault()) {
                tagRepository.deleteByCode(tag.code());
            }
        }

        // 3) 191실 객실 스케줄 및 커스텀 태그 초기화
        for (Room room : roomRepository.findAll()) {
            room.release();
            for (String tagCode : new ArrayList<>(room.getTags())) {
                boolean isDefault = switch (tagCode) {
                    case "HIGH_FLOOR", "LOW_FLOOR", "NEAR_ELEVATOR", "AWAY_FROM_ELEVATOR", "CORNER_ROOM", "QUIET_ZONE", "ACCESSIBLE" -> true;
                    default -> false;
                };
                if (!isDefault) {
                    room.removeTag(tagCode);
                }
            }
            try {
                java.lang.reflect.Field statusField = Room.class.getDeclaredField("status");
                statusField.setAccessible(true);
                statusField.set(room, RoomStatus.VACANT);
            } catch (Exception ignored) {}
            roomRepository.save(room);
        }

        // 4) 공식 영업일자 2026-09-20 기본 롤백
        LocalDate defaultDate = LocalDate.of(2026, 9, 20);
        hotelOperationService.setBusinessDate(defaultDate);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "businessDate", defaultDate.toString(),
                "message", "모든 예약, 커스텀 태그, 객실 점유가 초기화되었으며 영업일자가 2026-09-20으로 복원되었습니다."
        ));
    }
}