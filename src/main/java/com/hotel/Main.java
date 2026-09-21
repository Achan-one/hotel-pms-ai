package com.hotel;

import com.hotel.channel.ChannelManagerAdapter;
import com.hotel.channel.ChannelSyncService;
import com.hotel.channel.dto.ChannelInventorySyncDto;
import com.hotel.channel.dto.ChannelReservationRequest;
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

/**
 * AI Hotel PMS Core Engine 통합 시뮬레이션 엔트리포인트.
 *
 * <p>CMS 인바운드 예약/취소, 쿼터 방어 일괄 배정, 아웃바운드 ARI Push,
 * 룸 랙 테이블 매트릭스 렌더링 파이프라인을 실행합니다.</p>
 */
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

        // 채널 매니저 동기화 서비스 및 어댑터 초기화
        ChannelSyncService channelSyncService = new ChannelSyncService(roomRepository, quotaPolicy);
        ChannelManagerAdapter tlxAdapter = new TlxChannelAdapter();
        ChannelManagerAdapter ondaAdapter = new OndaChannelAdapter();

        LocalDate today = LocalDate.of(2026, 9, 20);

        System.out.println("==========================================================================================================");
        System.out.println("🏨 [AI-Driven Hotel PMS Core Engine] CMS(TLX/ONDA) 양방향 연동 & 쿼터 배정 시스템");
        System.out.println("   운영 기준 일자: " + today);
        System.out.println("   AI 엔진 설정: " + aiParser.getConfig());
        System.out.println("==========================================================================================================\n");

        // [신규] 태그 생성 및 매핑 가이드 섹션 출력
        printTagRegistrationGuide(tagRepository);

        System.out.println("🛡️ [호텔 관리자 정의 운영 보존 쿼터 (Safety Stock Hold)]");
        System.out.println("----------------------------------------------------------------------------------------------------------");
        System.out.println("- 타입별 킵: 이그제큐티브 더블 " + quotaPolicy.getTypeHoldQuota(RoomType.EXECUTIVE_DOUBLE) + "실, "
                + "슈페리어 트윈 " + quotaPolicy.getTypeHoldQuota(RoomType.SUPERIOR_TWIN) + "실, "
                + "레지덴셜 더블 " + quotaPolicy.getTypeHoldQuota(RoomType.RESIDENTIAL_DOUBLE) + "실");
        System.out.println("- 태그별 킵: 도쿄타워 전망 " + quotaPolicy.getTagHoldQuota("VIEW_TOKYO_TOWER") + "실, "
                + "배리어프리 " + quotaPolicy.getTagHoldQuota("ACCESSIBLE") + "실");
        System.out.println("----------------------------------------------------------------------------------------------------------\n");

        // 4. 초기 재실 생성 (20실 투숙 중)
        simulateExistingCheckInsWithSchedule(roomRepository, 20, today);
        long preOccupied = roomRepository.findAll().stream().filter(r -> r.isOccupiedOn(today)).count();
        System.out.printf("📌 [초기 객실 상태] 기존 투숙: %d실 / 배정 가능 공실: %d실%n%n",
                preOccupied, 191 - preOccupied);

        // 5. [인바운드 1단계: 외부 CMS 신규 예약 인입 시뮬레이션]
        System.out.println("📥 [채널 매니저(CMS) 외부 신규 예약 인바운드 수신]");
        System.out.println("----------------------------------------------------------------------------------------------------------");

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
        List<ChannelReservationRequest> tlxRequests = tlxAdapter.parseIncomingRequests(incomingTlxXml);
        Reservation tlxRes = tlxRequests.get(0).reservation();
        System.out.printf("  🇯🇵 [TL-Lincoln XML] %d건 수신 파싱 완료 -> 예약ID: %s (%s, %s, %d박)%n",
                tlxRequests.size(), tlxRes.getReservationId(), tlxRes.getGuestName(),
                tlxRes.getBookedRoomType(), tlxRes.getStayNights());

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
        List<ChannelReservationRequest> ondaRequests = ondaAdapter.parseIncomingRequests(incomingOndaJson);
        Reservation ondaRes = ondaRequests.get(0).reservation();
        System.out.printf("  🇰🇷 [ONDA Hub JSON]  %d건 수신 파싱 완료 -> 예약ID: %s (%s, %s, %d박)%n",
                ondaRequests.size(), ondaRes.getReservationId(), ondaRes.getGuestName(),
                ondaRes.getBookedRoomType(), ondaRes.getStayNights());

        // CMS 인입 2건 + 일반 가상 예약 48건 = 총 50건 수신
        List<Reservation> allIncoming = new ArrayList<>();
        allIncoming.add(tlxRes);
        allIncoming.add(ondaRes);
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
            System.out.println("==========================================================================================================");
            System.out.printf("⚠️ [배정 실패 알림] 총 %d건의 예약이 만실/보존 쿼터 홀딩 등으로 배정되지 못했습니다%n", failedItems.size());
            System.out.println("==========================================================================================================");
            System.out.printf("%-13s | %-16s | %-4s | %-24s | %s%n",
                    "예약ID", "신청 객실타입", "박수", "실패 사유", "고객 요청 메모(원문)");
            System.out.println("----------------------------------------------------------------------------------------------------------");

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
            System.out.println("----------------------------------------------------------------------------------------------------------\n");
        }

        // 8. 필수 하드 리퀘스트 미충족 경고 리포트
        List<AssignmentAlert> alerts = assignmentResult.getHardRequestAlerts();
        if (!alerts.isEmpty()) {
            System.out.println("==========================================================================================================");
            System.out.printf("🚨 [프론트 데스크 주의 요망: 필수 하드 리퀘스트(HARD) 미충족 배정 알림 (총 %d건)]%n", alerts.size());
            System.out.println("==========================================================================================================");
            System.out.printf("%-13s | %-12s | %-6s | %-24s | %s%n",
                    "예약ID", "고객명", "배정호실", "미충족 필수 요청", "미충족 원인 상세 사유");
            System.out.println("----------------------------------------------------------------------------------------------------------");
            for (AssignmentAlert alert : alerts) {
                System.out.printf("%-13s | %-12s | %-6s | %-22s | %s%n",
                        alert.reservation().getReservationId(),
                        alert.reservation().getGuestName(),
                        alert.assignedRoomNumber() + "호",
                        alert.unfulfilledTag(),
                        alert.reason()
                );
            }
            System.out.println("----------------------------------------------------------------------------------------------------------\n");
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

        // 10. TL-Lincoln 인바운드 취소 전문 수신 및 스케줄 공실 회수 시뮬레이션
        System.out.println("==========================================================================================================");
        System.out.println("🚫 [채널 매니저(CMS) 실시간 취소 웹훅 인입 & 스케줄 자동 회수 시뮬레이션]");
        System.out.println("==========================================================================================================");

        Reservation targetYamada = reservationService.getReservation("TLX-IN-001").orElseThrow();
        String yamadaAssignedRoom = targetYamada.getAssignedRoomNumber();
        System.out.printf("📌 [취소 전 상태] 예약ID: %s (%s) -> 배정호실: %s호 | 상태: %s (%d박 투숙 점유 중)%n",
                targetYamada.getReservationId(), targetYamada.getGuestName(),
                yamadaAssignedRoom, targetYamada.getStatus().getTitle(), targetYamada.getStayNights());

        String cancelTlxXml = """
                <TL_Reservations>
                  <Reservation>
                    <ReservationId>TLX-IN-001</ReservationId>
                    <TransactionType>CANCEL</TransactionType>
                  </Reservation>
                </TL_Reservations>
                """;

        List<ChannelReservationRequest> cancelRequests = tlxAdapter.parseIncomingRequests(cancelTlxXml);
        System.out.printf("📥 [린칸 전문 수신] 취소 요청 파싱 완료 -> 대상 예약ID: %s (Action: %s)%n",
                cancelRequests.get(0).reservationId(), cancelRequests.get(0).actionType());

        reservationService.processChannelRequests(cancelRequests);

        Reservation cancelledYamada = reservationService.getReservation("TLX-IN-001").orElseThrow();
        Room freedRoom = roomRepository.findByRoomNumber(yamadaAssignedRoom).orElseThrow();
        StayPeriod yamadaStayPeriod = new StayPeriod(today, 4);

        System.out.println("----------------------------------------------------------------------------------------------------------");
        System.out.printf("✅ [스케줄 회수 및 원장 보존 결과 확인]%n");
        System.out.printf(" - 예약 원장 보존 상태: %s (원장에서 행 삭제되지 않고 CANCELLED 이력 영구 보존)%n",
                cancelledYamada.getStatus().name());
        System.out.printf(" - 회수된 객실(%s호) 하우스키핑 상태: %s%n",
                freedRoom.getRoomNumber(), freedRoom.getStatus().getTitle());
        System.out.printf(" - 회수된 객실(%s호) 당일~4박 스케줄 가용 여부: %s (즉시 재판매 가능)%n",
                freedRoom.getRoomNumber(), freedRoom.isAvailable(yamadaStayPeriod) ? "배정 가능 (VACANT)" : "점유 중");
        System.out.println("----------------------------------------------------------------------------------------------------------\n");

        // 11. [아웃바운드: 취소분 환원 반영 판매 가능 잔여 재고(ARI Push) 산출]
        System.out.println("==========================================================================================================");
        System.out.println("📡 [채널 매니저(CMS) 아웃바운드: 취소분 환원 반영 판매 가능 잔여 재고(ARI Push)]");
        System.out.println("==========================================================================================================");
        List<ChannelInventorySyncDto> ariSyncData = channelSyncService.calculateDailySellableInventory(today);

        System.out.printf("%-20s | %-10s | %-8s | %-12s | %s%n",
                "객실타입", "물리공실", "안전킵", "최종판매가능(Sellable)", "1박기준요금");
        System.out.println("----------------------------------------------------------------------------------------------------------");
        for (ChannelInventorySyncDto item : ariSyncData) {
            System.out.printf("%-18s | %-8d실 | %-6d실 | %-16d실 | ¥%,d%n",
                    item.roomType().getDescription(),
                    item.physicalVacant(),
                    item.holdQuota(),
                    item.sellableInventory(),
                    item.rateYen());
        }
        System.out.println("----------------------------------------------------------------------------------------------------------\n");

        // 12. [개편] 콘솔 친화적 191실 전 객실 룸 랙 아스키 테이블 출력
        FloorMapResponseDto matrixReport = floorStatusService.getFloorMatrix(today, reservationService.searchReservations(null));
        printFloorMapTable(matrixReport);

        System.out.println("\n==========================================================================================================");
        System.out.printf("🎉 [운영 통계 요약] 총 %d실 | 점유: %d실 (재실+신규배정) | 공실: %d실 | 당일 점유율: %.1f%%%n",
                matrixReport.totalRooms(),
                matrixReport.occupiedRooms(),
                matrixReport.vacantRooms(),
                matrixReport.occupancyRatePercent());
        System.out.println("==========================================================================================================");
    }

    /**
     * 호텔 관리자를 위한 동적 태그 등록 가이드 및 전체 태그 카탈로그를 표 형식으로 출력합니다.
     */
    private static void printTagRegistrationGuide(TagRepository tagRepository) {
        System.out.println("📖 [태그 등록 시스템 가이드 (Tag Registration & Mapping Guide)]");
        System.out.println("----------------------------------------------------------------------------------------------------------");
        System.out.println("💡 1. 신규 태그 등록 방법 (Java Code):");
        System.out.println("   RoomTag newTag = new RoomTag(");
        System.out.println("       \"TAG_CODE\",              // 1) 고유 식별 코드 (예: VIEW_OCEAN, AMENITY_BATH)");
        System.out.println("       \"화면 표시명\",             // 2) 프론트 룸 랙 표시 이름 (예: 오션뷰, 히노끼탕)");
        System.out.println("       \"AI 프롬프트 상세 지침\",    // 3) 비정형 메모 매칭용 설명 (Gemini 2.5 Flash가 분석)");
        System.out.println("       RoomTag.TagCategory.VIEW, // 4) 카테고리 (FLOOR, LOCATION, VIEW, AMENITY, NOISE, ETC)");
        System.out.println("       TagStrictness.HARD,       // 5) 엄격도: HARD (미충족 시 경고), SOFT (미충족 시 차선 배정)");
        System.out.println("       30                        // 6) 배정 가중치 점수 (+30점 가산)");
        System.out.println("   );");
        System.out.println("   adminTagService.registerTag(isAdmin, newTag); // 관리자 권한 검증 후 등록");
        System.out.println();
        System.out.println("💡 2. 특정 객실(호실)에 태그 부여 방법:");
        System.out.println("   roomRepository.findByRoomNumber(\"1401\").ifPresent(room -> room.addTag(\"VIEW_TOKYO_TOWER\"));");
        System.out.println();
        System.out.println("💡 3. 태그 킵(Hold Quota) 방어 설정 방법:");
        System.out.println("   quotaPolicy.setTagHoldQuota(\"VIEW_TOKYO_TOWER\", 2); // 잔여 2실은 일반 고객 배정 차단");
        System.out.println("----------------------------------------------------------------------------------------------------------");
        System.out.println("📋 [현재 시스템에 등록된 전체 태그 카탈로그]");
        System.out.printf("%-18s | %-12s | %-10s | %-10s | %-8s | %s%n",
                "태그코드", "태그이름", "분류", "엄격도", "가중치", "AI 프롬프트 매칭 설명");
        System.out.println("----------------------------------------------------------------------------------------------------------");

        for (RoomTag tag : tagRepository.findAll()) {
            System.out.printf("%-18s | %-10s | %-8s | %-8s | +%-6d | %s%n",
                    tag.code(),
                    tag.name(),
                    tag.category().getDesc(),
                    tag.strictness().getTitle(),
                    tag.defaultWeight(),
                    tag.description()
            );
        }
        System.out.println("----------------------------------------------------------------------------------------------------------\n");
    }

    /**
     * 191개 전체 객실 매트릭스를 콘솔에서 한눈에 보기 편한 층별 테이블로 렌더링합니다.
     */
    private static void printFloorMapTable(FloorMapResponseDto dto) {
        System.out.println("==========================================================================================================");
        System.out.printf("📊 [191실 전 객실 룸 랙 현황 매트릭스 테이블] 기준일자: %s%n", dto.targetDate());
        System.out.println("==========================================================================================================");

        for (Map.Entry<Integer, List<RoomMatrixItemDto>> entry : dto.floorRooms().entrySet()) {
            int floor = entry.getKey();
            List<RoomMatrixItemDto> rooms = entry.getValue();

            System.out.printf("🏢 [%2d층 객실 현황 (총 %d실)]%n", floor, rooms.size());
            System.out.println("┌──────┬──────────────────────┬────────────┬──────┬──────┬─────────────────┬──────────────────────┐");
            System.out.println("│ 호실 │ 객실 타입            │ 룸 랙 상태 │ EV   │ 코너 │ 투숙객 / 예약ID │ 체류 일정            │");
            System.out.println("├──────┼──────────────────────┼────────────┼──────┼──────┼─────────────────┼──────────────────────┤");

            for (RoomMatrixItemDto r : rooms) {
                String statusStr = switch (r.status()) {
                    case VACANT -> "공실(VACANT)";
                    case OCCUPIED -> "재실(STAY)  ";
                    case ASSIGNED -> "배정(ASSIGN)";
                    case OUT -> "청소(OUT)   ";
                    case CLEANING -> "청소중(CLEAN)";
                    case BREAK -> "고장(BREAK) ";
                    case BLOCKED -> "점검(BLOCK) ";
                };

                String guestInfo = (r.guestName() != null)
                        ? String.format("%s (%s)", r.guestName(), r.reservationId())
                        : "-";

                String periodInfo = (r.stayPeriodStr() != null) ? r.stayPeriodStr() : "-";

                System.out.printf("│ %-4s │ %-18s │ %-10s │ %-4s │ %-4s │ %-15s │ %-20s │%n",
                        r.roomNumber(),
                        r.roomTypeName(),
                        statusStr,
                        r.nearElevator() ? "인접" : "이격",
                        r.cornerRoom() ? "코너" : "일반",
                        truncateString(guestInfo, 15),
                        truncateString(periodInfo, 20)
                );
            }
            System.out.println("└──────┴──────────────────────┴────────────┴──────┴──────┴─────────────────┴──────────────────────┘\n");
        }
    }

    private static String truncateString(String text, int maxLength) {
        if (text == null) return "-";
        return (text.length() > maxLength) ? text.substring(0, maxLength - 2) + ".." : text;
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