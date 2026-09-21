package com.hotel;

import com.hotel.domain.*;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.service.*;
import com.hotel.service.dto.FloorMapResponseDto;
import com.hotel.service.dto.RoomChangeRequest;
import com.hotel.service.dto.RoomChangeResult;
import com.hotel.service.dto.RoomMatrixItemDto;

import java.time.LocalDate;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        // 1. 인프라 및 저장소 초기화 (동적 TagRepository 생성)
        TagRepository tagRepository = new TagRepository();
        RoomRepository roomRepository = new RoomRepository();
        ReservationRepository reservationRepository = new ReservationRepository();

        // 2. [핵심] 관리자 커스텀 태그 동적 등록 및 객실 부여
        registerCustomTagsAndAssignToRooms(tagRepository, roomRepository);

        // 3. AI 파서에 최신 TagRepository를 주입하여 동적 프롬프트 자동 조립
        AiPreferenceParser aiParser = new AiPreferenceParser(tagRepository);

        // 4. 통합 서비스 계층 구성
        ReservationService reservationService = new ReservationService(reservationRepository, roomRepository, aiParser);
        FloorStatusService floorStatusService = new FloorStatusService(roomRepository);

        LocalDate today = LocalDate.of(2026, 9, 20);

        System.out.println("================================================================================");
        System.out.println("🏨 [AI-Driven Hotel PMS Core Engine] 태그 지향 시스템 오케스트레이션 가동");
        System.out.println("   운영 기준 일자: " + today);
        System.out.println("   AI 엔진 설정: " + aiParser.getConfig());
        System.out.println("================================================================================\n");

        // 관리자가 등록한 동적 태그 카탈로그 출력
        System.out.println("🏷️ [호텔 관리자 정의 실시간 객실 태그 카탈로그 (AI 프롬프트에 자동 주입됨)]");
        System.out.println("--------------------------------------------------------------------------------");
        System.out.print(tagRepository.buildPromptTagDictionary());
        System.out.println("--------------------------------------------------------------------------------\n");

        // 5. 기준일 이전 체크인한 기존 투숙객 20실 사전 안전 세팅
        simulateExistingCheckInsWithSchedule(roomRepository, 20, today);
        long preOccupied = roomRepository.findAll().stream().filter(r -> r.isOccupiedOn(today)).count();
        System.out.printf("📌 [초기 객실 상태] 기존 투숙: %d실 / 배정 가능 공실: %d실%n%n",
                preOccupied, 191 - preOccupied);

        // 6. 외부 OTA 유입 50개 예약 생성 및 장부 접수 (PENDING)
        List<Reservation> incomingReservations = generate50RealisticReservations(today);
        List<Reservation> acceptedReservations = reservationService.receiveReservations(incomingReservations);
        System.out.printf("📝 [예약 접수 완료] %d건 인입 중 유효 예약 %d건 장부(PENDING) 적재 완료%n%n",
                incomingReservations.size(), acceptedReservations.size());

        // 7. ReservationService 통한 당일 일괄 AI 파싱 및 우선순위 자동 배정 (Single API Call)
        System.out.println("⚡ [Gemini 2.5 Flash & BatchAssigner] 당일 일괄 배정 파이프라인 가동...");
        long startTime = System.currentTimeMillis();
        BatchAssignmentResult assignmentResult = reservationService.runDailyBatchAssignment(today);
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("✅ 당일 자동 배정 완료! (소요 시간: %d ms)%n", elapsed);
        System.out.println(assignmentResult.toSummaryString());
        System.out.println();

        // 8. 🎯 [고객 요청 메모 & 태그 스위칭 결과 테이블]
        Map<String, Reservation> successMap = new HashMap<>();
        for (Reservation r : assignmentResult.getSuccessfulAssignments()) {
            successMap.put(r.getReservationId(), r);
        }

        System.out.println("========================================================================================================================");
        System.out.println("🎯 [고객 요청 메모 -> Gemini AI 태그 스위치(ON/OFF) 판별 -> 최종 객실 매칭 결과]");
        System.out.println("========================================================================================================================");
        System.out.printf("%-13s | %-16s | %-4s | %-20s | %-32s | %s%n",
                "예약ID", "객실타입", "박수", "고객 요청 메모(원문)", "AI 활성화 태그 (선호[+] / 기피[-])", "최종 배정 호실");
        System.out.println("------------------------------------------------------------------------------------------------------------------------");

        for (Reservation r : acceptedReservations) {
            String memo = r.getRawRequestText();
            if (memo != null && !memo.isBlank()) {
                String memoPreview = (memo.length() > 14) ? memo.substring(0, 12) + ".." : memo;
                Reservation matchedSuccess = successMap.get(r.getReservationId());

                String assignResultText;
                String tagSwitchText;

                if (matchedSuccess != null) {
                    TagPreference tagPref = matchedSuccess.getTagPreference();
                    String prefStr = tagPref.preferredTags().isEmpty() ? "없음" : String.join(",", tagPref.preferredTags());
                    String avoidStr = tagPref.avoidTags().isEmpty() ? "" : " / 기피:" + String.join(",", tagPref.avoidTags());
                    tagSwitchText = String.format("[+]%s%s", prefStr, avoidStr);

                    assignResultText = matchedSuccess.isAssigned()
                            ? String.format("✅ %s호 확정", matchedSuccess.getAssignedRoomNumber())
                            : "❌ 배정실패";
                } else {
                    tagSwitchText = "[미분석]";
                    assignResultText = "❌ 배정실패(만실)";
                }

                System.out.printf("%-13s | %-16s | %-3d박 | %-18s | %-30s | %s%n",
                        r.getReservationId(),
                        r.getBookedRoomType().name(),
                        r.getStayNights(),
                        memoPreview,
                        tagSwitchText,
                        assignResultText
                );
            }
        }
        System.out.println("========================================================================================================================\n");

        // 9. ⚠️ 배정 실패 건 리포트
        var failedItems = assignmentResult.getFailedAssignments();
        if (!failedItems.isEmpty()) {
            System.out.println("================================================================================");
            System.out.printf("⚠️ [배정 실패 알림] 총 %d건의 예약이 만실 등으로 배정되지 못했습니다%n", failedItems.size());
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

        // 10. 🛎️ 프론트 데스크 실무: 첫 번째 배정 고객 체크인 후 룸 체인지 시뮬레이션
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
                        resId,
                        emptySameTypeRoom.getRoomNumber(),
                        today,
                        "고객 현장 요청 (전망 개선)"
                );

                RoomChangeResult changeResult = reservationService.processRoomChange(changeRequest);

                Reservation movedGuest = reservationService.getReservation(resId).orElseThrow();
                System.out.printf("🔄 [수동 룸 체인지] 고객 [%s] 객실 이동 완료: %s -> %s호 | 상태: %s%n",
                        movedGuest.getGuestName(), originRoom, movedGuest.getAssignedRoomNumber(), movedGuest.getStatus().name());
                System.out.println("   >> 처리 결과: " + changeResult.message() + "\n");
            }
        }

        // 11. 📊 191실 전 객실 룸 랙 Full JSON 매트릭스 출력
        System.out.println("================================================================================");
        System.out.println("📊 [191실 전 객실 룸 랙 Full JSON 매트릭스]");
        System.out.println("================================================================================");
        FloorMapResponseDto matrixReport = floorStatusService.getFloorMatrix(today, assignmentResult.getSuccessfulAssignments());
        System.out.println(formatFloorMapToJson(matrixReport));

        System.out.println("\n================================================================================");
        System.out.printf("🎉 [운영 통계 요약] 총 %d실 | 점유: %d실 (재실+신규배정) | 공실: %d실 | 당일 점유율: %.1f%%%n",
                matrixReport.totalRooms(),
                matrixReport.occupiedRooms(),
                matrixReport.vacantRooms(),
                matrixReport.occupancyRatePercent());
        System.out.println("================================================================================");
    }

    /**
     * 호텔 관리자가 런타임에 커스텀 태그를 신규 생성하고 특정 호실들에 장착하는 시뮬레이션
     */
    private static void registerCustomTagsAndAssignToRooms(TagRepository tagRepo, RoomRepository roomRepo) {
        // 1. 도쿄타워 전망 태그 등록 및 14~15층 특정 방 부여
        RoomTag tokyoTowerView = new RoomTag(
                "VIEW_TOKYO_TOWER", "도쿄타워 전망",
                "창밖으로 도쿄타워 및 시티 랜드마크 야경이 펼쳐지는 객실, 타워 뷰 희망 시 매칭",
                RoomTag.TagCategory.VIEW, 30
        );
        tagRepo.save(tokyoTowerView);

        // 14층, 15층 01호, 02호에 도쿄타워 뷰 태그 부여
        roomRepo.findByRoomNumber("1401").ifPresent(r -> r.addTag("VIEW_TOKYO_TOWER"));
        roomRepo.findByRoomNumber("1402").ifPresent(r -> r.addTag("VIEW_TOKYO_TOWER"));
        roomRepo.findByRoomNumber("1501").ifPresent(r -> r.addTag("VIEW_TOKYO_TOWER"));
        roomRepo.findByRoomNumber("1502").ifPresent(r -> r.addTag("VIEW_TOKYO_TOWER"));

        // 2. 아로마 디퓨저 구비 힐링 객실 태그 등록
        RoomTag aromaRoom = new RoomTag(
                "AMENITY_AROMA", "아로마 힐링룸",
                "라벤더 아로마 디퓨저 및 릴렉스 공기청정기가 구비된 힐링 전용 객실, 향기나 힐링 요청 시 매칭",
                RoomTag.TagCategory.AMENITY, 20
        );
        tagRepo.save(aromaRoom);

        // 12층 16호, 13층 16호에 아로마 힐링 태그 부여
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

    private static List<Reservation> generate50RealisticReservations(LocalDate today) {
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

        for (int i = 1; i <= 50; i++) {
            String rsvId = String.format("RSV-BATCH-%03d", i);
            String guestName = "Guest_" + i;
            RoomType type = types[rand.nextInt(types.length)];
            int nights = rand.nextInt(4) + 1;
            String note = realisticNotes[i % realisticNotes.length];

            list.add(new Reservation(rsvId, guestName, type, today, nights, note, GuestPreference.empty()));
        }

        return list;
    }
}