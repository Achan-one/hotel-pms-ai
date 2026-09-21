package com.hotel;

import com.hotel.channel.ChannelManagerAdapter;
import com.hotel.channel.ChannelSyncService;
import com.hotel.channel.dto.ChannelInventorySyncDto;
import com.hotel.channel.onda.OndaChannelAdapter;
import com.hotel.channel.tlx.TlxChannelAdapter;
import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.service.*;
import com.hotel.service.dto.AssignmentAlert;
import com.hotel.service.dto.FloorMapResponseDto;
import com.hotel.service.dto.RoomChangeRequest;
import com.hotel.service.dto.RoomChangeResult;
import com.hotel.service.dto.RoomMatrixItemDto;

import java.time.LocalDate;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        // 1. 인프라 및 저장소 초기화
        TagRepository tagRepository = new TagRepository();
        RoomRepository roomRepository = new RoomRepository();
        ReservationRepository reservationRepository = new ReservationRepository();

        // 2. 통합 쿼터 정책 (타입 킵: 이그제큐티브 1실, 트윈 2실, 레지덴셜 2실 / 태그 킵: 타워뷰 2실, 배리어프리 1실)
        QuotaPolicy quotaPolicy = new QuotaPolicy();

        // 3. 어드민 태그 서비스 및 기본 태그 등록
        AdminTagService adminTagService = new AdminTagService(tagRepository, quotaPolicy);
        registerAdminCustomTags(adminTagService, roomRepository);

        AiPreferenceParser aiParser = new AiPreferenceParser(tagRepository);
        ReservationService reservationService = new ReservationService(
                reservationRepository, roomRepository, aiParser, tagRepository, quotaPolicy
        );
        FloorStatusService floorStatusService = new FloorStatusService(roomRepository);

        // [신규] 채널 매니저 동기화 서비스 및 어댑터 초기화
        ChannelSyncService channelSyncService = new ChannelSyncService(roomRepository, quotaPolicy);
        ChannelManagerAdapter tlxAdapter = new TlxChannelAdapter();
        ChannelManagerAdapter ondaAdapter = new OndaChannelAdapter();

        LocalDate today = LocalDate.of(2026, 9, 20);

        System.out.println("================================================================================");
        System.out.println("🏨 [AI-Driven Hotel PMS Core Engine] CMS(TLX/ONDA) 양방향 연동 & 쿼터 배정 시스템");
        System.out.println("   운영 기준 일자: " + today);
        System.out.println("   AI 엔진 설정: " + aiParser.getConfig());
        System.out.println("================================================================================\n");

        System.out.println("🛡️ [호텔 관리자 정의 운영 보존 쿼터 (Safety Stock Hold)]");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("- 타입별 킵: 이그제큐티브 더블 " + quotaPolicy.getTypeHoldQuota(RoomType.EXECUTIVE_DOUBLE) + "실, "
                + "슈페리어 트윈 " + quotaPolicy.getTypeHoldQuota(RoomType.SUPERIOR_TWIN) + "실, "
                + "레지덴셜 더블 " + quotaPolicy.getTypeHoldQuota(RoomType.RESIDENTIAL_DOUBLE) + "실");
        System.out.println("- 태그별 킵: 도쿄타워 전망 " + quotaPolicy.getTagHoldQuota("VIEW_TOKYO_TOWER") + "실, "
                + "배리어프리 " + quotaPolicy.getTagHoldQuota("ACCESSIBLE") + "실");
        System.out.println("--------------------------------------------------------------------------------\n");

        // 4. 초기 재실 생성 (20실 투숙 중)
        simulateExistingCheckInsWithSchedule(roomRepository, 20, today);
        long preOccupied = roomRepository.findAll().stream().filter(r -> r.isOccupiedOn(today)).count();
        System.out.printf("📌 [초기 객실 상태] 기존 투숙: %d실 / 배정 가능 공실: %d실%n%n",
                preOccupied, 191 - preOccupied);

        // 5. [인바운드 1단계: 외부 CMS 전문 인입 시뮬레이션]
        System.out.println("📥 [채널 매니저(CMS) 외부 예약 인바운드 수신]");
        System.out.println("--------------------------------------------------------------------------------");

        // (1) 일본 TL-Lincoln(XML) 예약 전문 수신
        String incomingTlxXml = """
                <TL_Reservations>
                  <Reservation>
                    <ReservationId>TLX-IN-001</ReservationId>
                    <GuestName>Yamada Taro</GuestName>
                    <RoomType>SUPERIOR_TWIN</RoomType>
                    <CheckInDate>2026-09-20</CheckInDate>
                    <StayNights>4</StayNights>
                    <SpecialRequest>東京タワーが見える部屋をお願いします。</SpecialRequest>
                  </Reservation>
                </TL_Reservations>
                """;
        List<Reservation> tlxParsed = tlxAdapter.parseIncomingReservations(incomingTlxXml);
        System.out.printf("  🇯🇵 [TL-Lincoln XML] %d건 수신 파싱 완료 -> 예약ID: %s (%s, %s, %d박)%n",
                tlxParsed.size(), tlxParsed.get(0).getReservationId(), tlxParsed.get(0).getGuestName(),
                tlxParsed.get(0).getBookedRoomType(), tlxParsed.get(0).getStayNights());

        // (2) 한국 ONDA Hub(JSON) 웹훅 예약 전문 수신
        String incomingOndaJson = """
                {
                  "channel": "ONDA_HUB",
                  "reservations": [
                    {
                      "reservationId": "ONDA-IN-001",
                      "guestName": "김철수",
                      "roomType": "RESIDENTIAL_DOUBLE",
                      "checkInDate": "2026-09-20",
                      "stayNights": 3,
                      "specialRequests": "어머니 무릎이 불편하셔서 엘리베이터 가깝고 낮은 층 부탁드립니다."
                    }
                  ]
                }
                """;
        List<Reservation> ondaParsed = ondaAdapter.parseIncomingReservations(incomingOndaJson);
        System.out.printf("  🇰🇷 [ONDA Hub JSON]  %d건 수신 파싱 완료 -> 예약ID: %s (%s, %s, %d박)%n",
                ondaParsed.size(), ondaParsed.get(0).getReservationId(), ondaParsed.get(0).getGuestName(),
                ondaParsed.get(0).getBookedRoomType(), ondaParsed.get(0).getStayNights());

        // CMS 인입 2건 + 일반 가상 예약 48건 = 총 50건 수신
        List<Reservation> allIncoming = new ArrayList<>();
        allIncoming.addAll(tlxParsed);
        allIncoming.addAll(ondaParsed);
        allIncoming.addAll(generate48RealisticReservations(today));

        List<Reservation> acceptedReservations = reservationService.receiveReservations(allIncoming);
        System.out.printf("%n📝 [예약 원장 적재 완료] 총 %d건 인입 중 유효 예약 %d건 장부 적재 완료%n%n",
                allIncoming.size(), acceptedReservations.size());

        // 6. 배치 자동 배정 실행
        System.out.println("⚡ [Gemini 2.5 Flash & BatchAssigner] 당일 일괄 배정 파이프라인 가동...");
        long startTime = System.currentTimeMillis();
        BatchAssignmentResult assignmentResult = reservationService.runDailyBatchAssignment(today);
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("✅ 당일 자동 배정 완료! (소요 시간: %d ms)%n", elapsed);
        System.out.println(assignmentResult.toSummaryString());
        System.out.println();

        // 7. 배정 실패 건 리포트 (만실 / 타입별 킵 방어)
        var failedItems = assignmentResult.getFailedAssignments();
        if (!failedItems.isEmpty()) {
            System.out.println("================================================================================");
            System.out.printf("⚠️ [배정 실패 알림] 총 %d건의 예약이 만실/보존 쿼터 홀딩 등으로 배정되지 못했습니다%n", failedItems.size());
            System.out.println("================================================================================");
            System.out.printf("%-13s | %-16s | %-4s | %-24s | %s%n",
                    "예약ID", "신청 객실타입", "박수", "실패 사유", "고객 요청 메모(원문)");
            System.out.println("--------------------------------------------------------------------------------");

            for (var item : failedItems) {
                Reservation failed = item.reservation();
                String memo = failed.getRawRequestText();
                if (memo == null || memo.isBlank()) memo = "(요청 없음)";

                System.out.printf("%-13s | %-16s | %-3d박 | %-22s | %s%n",
                        failed.getReservationId(),
                        failed.getBookedRoomType().getDescription(),
                        failed.getStayNights(),
                        item.reason(),
                        memo
                );
            }
            System.out.println("--------------------------------------------------------------------------------\n");
        }

        // 8. 🚨 필수 하드 리퀘스트 미충족 경고 리포트
        List<AssignmentAlert> alerts = assignmentResult.getHardRequestAlerts();
        if (!alerts.isEmpty()) {
            System.out.println("========================================================================================================================");
            System.out.printf("🚨 [프론트 데스크 주의 요망: 필수 하드 리퀘스트(HARD) 미충족 배정 알림 (총 %d건)]%n", alerts.size());
            System.out.println("========================================================================================================================");
            System.out.printf("%-13s | %-12s | %-6s | %-24s | %s%n",
                    "예약ID", "고객명", "배정호실", "미충족 필수 요청", "미충족 원인 상세 사유");
            System.out.println("------------------------------------------------------------------------------------------------------------------------");
            for (AssignmentAlert alert : alerts) {
                System.out.printf("%-13s | %-12s | %-6s | %-22s | %s%n",
                        alert.reservation().getReservationId(),
                        alert.reservation().getGuestName(),
                        alert.assignedRoomNumber() + "호",
                        alert.unfulfilledTag(),
                        alert.reason()
                );
            }
            System.out.println("------------------------------------------------------------------------------------------------------------------------\n");
        }

        // 9. 체크인 & 룸 체인지 시뮬레이션
        Reservation firstAssigned = assignmentResult.getSuccessfulAssignments().stream().findFirst().orElse(null);
        if (firstAssigned != null) {
            String resId = firstAssigned.getReservationId();
            reservationService.processCheckIn(resId);
            Reservation checkedInGuest = reservationService.getReservation(resId).orElseThrow();
            System.out.printf("🛎️ [프론트 체크인] 고객 [%s] 키 발급 완료 -> 상태: %s (%s호)%n",
                    checkedInGuest.getGuestName(), checkedInGuest.getStatus().getTitle(), checkedInGuest.getAssignedRoomNumber());

            String originRoom = checkedInGuest.getAssignedRoomNumber();
            StayPeriod remainingPeriod = new StayPeriod(today, checkedInGuest.getStayNights());

            Room emptySameTypeRoom = roomRepository.findAll().stream()
                    .filter(room -> room.getRoomType() == checkedInGuest.getBookedRoomType())
                    .filter(room -> !room.getRoomNumber().equals(originRoom))
                    .filter(room -> room.getStatus().isAssignable())
                    .filter(room -> room.isAvailable(remainingPeriod))
                    .findFirst()
                    .orElse(null);

            if (emptySameTypeRoom != null) {
                RoomChangeRequest changeRequest = new RoomChangeRequest(
                        resId, emptySameTypeRoom.getRoomNumber(), today, "고객 현장 요청 (전망 개선)"
                );
                RoomChangeResult changeResult = reservationService.processRoomChange(changeRequest);

                Reservation movedGuest = reservationService.getReservation(resId).orElseThrow();
                System.out.printf("🔄 [수동 룸 체인지] 고객 [%s] 객실 이동 완료: %s -> %s호 | 상태: %s%n",
                        movedGuest.getGuestName(), originRoom, movedGuest.getAssignedRoomNumber(), movedGuest.getStatus().name());
                System.out.println("   >> 처리 결과: " + changeResult.message() + "\n");
            }
        }

        // 10. [아웃바운드 2단계: 배정 후 최종 잔여 판매 가능 재고(ARI Push) 산출 및 전문 직렬화]
        System.out.println("================================================================================");
        System.out.println("📡 [채널 매니저(CMS) 아웃바운드: 판매 가능 잔여 재고(ARI Push) 동기화]");
        System.out.println("================================================================================");
        List<ChannelInventorySyncDto> ariSyncData = channelSyncService.calculateDailySellableInventory(today);

        System.out.printf("%-20s | %-10s | %-8s | %-12s | %s%n",
                "객실타입", "물리공실", "안전킵", "최종판매가능(Sellable)", "1박기준요금");
        System.out.println("--------------------------------------------------------------------------------");
        for (ChannelInventorySyncDto item : ariSyncData) {
            System.out.printf("%-18s | %-8d실 | %-6d실 | %-16d실 | ¥%,d%n",
                    item.roomType().getDescription(),
                    item.physicalVacant(),
                    item.holdQuota(),
                    item.sellableInventory(),
                    item.rateYen());
        }
        System.out.println("--------------------------------------------------------------------------------\n");

        System.out.println("📤 [TL-Lincoln 송신 XML 전문 (Japan OTA용)]");
        System.out.println(tlxAdapter.serializeInventoryUpdate(ariSyncData));
        System.out.println();

        System.out.println("📤 [ONDA Hub 송신 REST JSON 전문 (Korea OTA용)]");
        System.out.println(ondaAdapter.serializeInventoryUpdate(ariSyncData));
        System.out.println();

        // 11. 최종 룸 랙 Full JSON 출력
        FloorMapResponseDto matrixReport = floorStatusService.getFloorMatrix(today, assignmentResult.getSuccessfulAssignments());
        System.out.println("================================================================================");
        System.out.println("📊 [191실 전 객실 룸 랙 Full JSON 매트릭스]");
        System.out.println("================================================================================");
        System.out.println(formatFloorMapToJson(matrixReport));

        System.out.println("\n================================================================================");
        System.out.printf("🎉 [운영 통계 요약] 총 %d실 | 점유: %d실 (재실+신규배정) | 공실: %d실 | 당일 점유율: %.1f%%%n",
                matrixReport.totalRooms(),
                matrixReport.occupiedRooms(),
                matrixReport.vacantRooms(),
                matrixReport.occupancyRatePercent());
        System.out.println("================================================================================");
    }

    private static void registerAdminCustomTags(AdminTagService adminService, RoomRepository roomRepo) {
        boolean isAdmin = true;

        RoomTag tokyoTowerView = new RoomTag(
                "VIEW_TOKYO_TOWER", "도쿄타워 전망",
                "창밖으로 도쿄타워 및 시티 랜드마크 야경이 펼쳐지는 객실, 타워 뷰 희망 시 매칭",
                RoomTag.TagCategory.VIEW, TagStrictness.SOFT, 30
        );
        adminService.registerTag(isAdmin, tokyoTowerView);

        roomRepo.findByRoomNumber("1401").ifPresent(r -> r.addTag("VIEW_TOKYO_TOWER"));
        roomRepo.findByRoomNumber("1402").ifPresent(r -> r.addTag("VIEW_TOKYO_TOWER"));
        roomRepo.findByRoomNumber("1501").ifPresent(r -> r.addTag("VIEW_TOKYO_TOWER"));
        roomRepo.findByRoomNumber("1502").ifPresent(r -> r.addTag("VIEW_TOKYO_TOWER"));

        RoomTag aromaRoom = new RoomTag(
                "AMENITY_AROMA", "아로마 힐링룸",
                "라벤더 아로마 디퓨저 및 릴렉스 공기청정기가 구비된 힐링 전용 객실, 향기나 힐링 요청 시 매칭",
                RoomTag.TagCategory.AMENITY, TagStrictness.SOFT, 20
        );
        adminService.registerTag(isAdmin, aromaRoom);

        roomRepo.findByRoomNumber("1216").ifPresent(r -> r.addTag("AMENITY_AROMA"));
        roomRepo.findByRoomNumber("1316").ifPresent(r -> r.addTag("AMENITY_AROMA"));
    }

    private static String formatFloorMapToJson(FloorMapResponseDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"targetDate\": \"%s\",\n", dto.targetDate()));
        sb.append(String.format("  \"totalRooms\": %d,\n", dto.totalRooms()));
        sb.append(String.format("  \"occupiedRooms\": %d,\n", dto.occupiedRooms()));
        sb.append(String.format("  \"vacantRooms\": %d,\n", dto.vacantRooms()));
        sb.append(String.format("  \"occupancyRatePercent\": %.1f,\n", dto.occupancyRatePercent()));
        sb.append("  \"floorRooms\": {\n");

        int floorCount = 0;
        int totalFloors = dto.floorRooms().size();

        for (Map.Entry<Integer, List<RoomMatrixItemDto>> entry : dto.floorRooms().entrySet()) {
            int floor = entry.getKey();
            List<RoomMatrixItemDto> rooms = entry.getValue();
            floorCount++;

            sb.append(String.format("    \"%d\": [\n", floor));
            for (int i = 0; i < rooms.size(); i++) {
                RoomMatrixItemDto r = rooms.get(i);
                sb.append(String.format(
                        "      {\"roomNumber\": \"%s\", \"roomType\": \"%s\", \"status\": \"%s\", \"nearElevator\": %b, \"cornerRoom\": %b}%s\n",
                        r.roomNumber(),
                        r.roomType(),
                        r.status(),
                        r.nearElevator(),
                        r.cornerRoom(),
                        (i < rooms.size() - 1) ? "," : ""
                ));
            }
            sb.append(String.format("    ]%s\n", (floorCount < totalFloors) ? "," : ""));
        }

        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }

    private static void simulateExistingCheckInsWithSchedule(RoomRepository repo, int count, LocalDate today) {
        List<Room> allRooms = new ArrayList<>(repo.findAll());
        Collections.shuffle(allRooms, new Random(42));

        int occupiedCount = 0;
        for (Room room : allRooms) {
            if (occupiedCount >= count) break;

            int pastDays = (occupiedCount % 2 == 0) ? 1 : 2;
            int totalNights = pastDays + 2;

            StayPeriod stayPeriod = new StayPeriod(today.minusDays(pastDays), totalNights);

            if (room.isAvailable(stayPeriod)) {
                room.bookPeriod(stayPeriod);
                room.setStatus(RoomStatus.OCCUPIED);
                occupiedCount++;
            }
        }
    }

    private static List<Reservation> generate48RealisticReservations(LocalDate today) {
        List<Reservation> list = new ArrayList<>();
        RoomType[] types = RoomType.values();

        String[] realisticNotes = {
                "어머니 무릎이 안 좋으셔서 엘리베이터 가깝고 낮은 층으로 부탁드립니다.",
                "静かに過ごしたいので、エレベーターから離れた高層階の部屋をお願いします。",
                "High floor with a nice Tokyo Tower view please!",
                "결혼기념일 여행이라 야경 좋은 방이나 아로마 힐링되는 방 희망합니다.",
                "足が不自由なため、できるだけ低層階かつエレベーター近くの部屋が希望です。",
                "Baby sleeping, very quiet room needed away from lift noise.",
                "야간 근무 후 쉬러 갑니다. 조용한 안쪽 방으로 주세요.",
                "도쿄타워 보이는 방으로 꼭 부탁드립니다.",
                "No specific request, thank you.",
                "밥이 먹고 싶은데요?",
                "모두 고생하십니다. 나중에 인사드리러 갈게요",
                ""
        };

        Random rand = new Random(2026);

        for (int i = 3; i <= 50; i++) {
            String rsvId = String.format("RSV-BATCH-%03d", i);
            String guestName = "Guest_" + i;
            RoomType type = types[rand.nextInt(types.length)];
            int nights = rand.nextInt(7) + 1;
            String note = realisticNotes[i % realisticNotes.length];

            list.add(new Reservation(rsvId, guestName, type, today, nights, note, GuestPreference.empty()));
        }

        return list;
    }
}