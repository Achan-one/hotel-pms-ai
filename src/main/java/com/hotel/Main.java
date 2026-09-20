package com.hotel;

import com.hotel.domain.*;
import com.hotel.repository.RoomRepository;
import com.hotel.service.*;
import com.hotel.service.dto.FloorMapResponseDto;
import com.hotel.service.dto.RoomChangeResult;
import com.hotel.service.dto.RoomMatrixItemDto;

import java.time.LocalDate;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        RoomRepository repository = new RoomRepository();
        RoomAssigner assigner = new RoomAssigner(repository);
        AiPreferenceParser aiParser = new AiPreferenceParser();
        RoomChangeService roomChangeService = new RoomChangeService(repository);
        FloorStatusService floorStatusService = new FloorStatusService(repository);

        LocalDate today = LocalDate.of(2026, 9, 20);

        System.out.println("================================================================================");
        System.out.println("🏨 [종합 실무 시뮬레이션] AI 다국어 파싱 -> 자동 배정 -> 룸 체인지 -> 191실 인디케이터");
        System.out.println("   운영 기준 일자: " + today);
        System.out.println("   AI 엔진 설정: " + aiParser.getConfig());
        System.out.println("================================================================================\n");

        // 1. 기준일 이전 체크인한 기존 투숙객(OCCUPIED) 20실 사전 세팅
        simulateExistingCheckInsWithSchedule(repository, 20, today);
        long preOccupied = repository.findAll().stream().filter(r -> r.isOccupiedOn(today)).count();
        System.out.printf("📌 [초기 상태] 기존 투숙 객실: %d실 / 배정 가능 공실: %d실%n%n",
                preOccupied, 191 - preOccupied);

        // 2. 다국어 요청 메모가 포함된 50개 신규 예약 생성
        List<Reservation> rawReservations = generate50RealisticReservations(today);
        System.out.printf("📝 50건의 비정형 예약 생성 완료 (한국어, 일본어, 영어, 무요청 혼합)%n");
        System.out.println("   샘플 1: " + rawReservations.get(0).getRawRequestText());
        System.out.println("   샘플 2: " + rawReservations.get(1).getRawRequestText());
        System.out.println("   샘플 3: " + rawReservations.get(2).getRawRequestText());
        System.out.println();

        // 3. 🚀 단 1회의 Gemini API 호출로 50건 일괄 정제
        System.out.println("⚡ [Gemini 2.5 Flash] 50건 일괄 배치 분석 요청 전송 중 (Single API Call)...");
        long startTime = System.currentTimeMillis();
        Map<String, GuestPreference> parsedPreferences = aiParser.parseBatch(rawReservations);
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("✅ AI 일괄 정제 완료! 소요시간: %d ms (분석된 선호도: %d건)%n%n", elapsed, parsedPreferences.size());

        // 4. AI가 정제해 준 선호도를 각 Reservation에 주입
        List<Reservation> enrichedReservations = new ArrayList<>();
        for (Reservation rsv : rawReservations) {
            GuestPreference pref = parsedPreferences.getOrDefault(rsv.getReservationId(), GuestPreference.empty());
            enrichedReservations.add(rsv.withPreference(pref));
        }

        // 5. 정제된 50개 예약으로 일괄 배정 엔진(BatchAssigner) 가동
        System.out.println("⚙️ [배치 배정 엔진] 우선순위 기반 자동 배정 가동...");
        BatchAssigner batchAssigner = new BatchAssigner(assigner);
        BatchAssignmentResult result = batchAssigner.assignAll(enrichedReservations);

        // 50건 상세 배정 요약 표 출력
        Map<String, Reservation> successMap = result.getSuccessfulAssignments().stream()
                .collect(java.util.stream.Collectors.toMap(Reservation::getReservationId, r -> r));

        System.out.println("========================================================================================================================");
        System.out.printf("%-13s | %-17s | %-4s | %-24s | %-26s | %s%n",
                "예약ID", "객실타입", "박수", "고객 요청 메모(원문)", "AI 선호도 정제", "최종 배정 결과");
        System.out.println("------------------------------------------------------------------------------------------------------------------------");

        for (Reservation r : enrichedReservations) {
            String memo = r.getRawRequestText();
            if (memo == null || memo.isBlank()) memo = "(요청 없음)";
            else if (memo.length() > 16) memo = memo.substring(0, 14) + "..";

            Reservation successRes = successMap.get(r.getReservationId());
            String resultRoom = (successRes != null && successRes.isAssigned())
                    ? successRes.getAssignedRoomNumber() + "호 확정"
                    : "❌ 배정실패(만실)";

            System.out.printf("%-13s | %-17s | %-3d박 | %-22s | %-24s | %s%n",
                    r.getReservationId(),
                    r.getBookedRoomType().name(),
                    r.getStayNights(),
                    memo,
                    r.getPreference(),
                    resultRoom
            );
        }
        System.out.println("========================================================================================================================\n");
        System.out.println(result.toSummaryString());
        System.out.println();

        // 5-1. ⚠️ 배정 실패(만실/타입 불일치 등) 건 별도 필터링 출력
        List<Reservation> failedList = result.getFailedAssignments();

        if (!failedList.isEmpty()) {
            System.out.println("================================================================================");
            System.out.printf("⚠️ [배정 실패 알림] 총 %d건의 예약이 배정되지 못했습니다 (만실 또는 제약조건 초과)%n", failedList.size());
            System.out.println("================================================================================");
            System.out.printf("%-13s | %-16s | %-4s | %-26s | %s%n",
                    "예약ID", "신청 객실타입", "박수", "AI 분석 선호도", "고객 요청 메모(원문)");
            System.out.println("--------------------------------------------------------------------------------");

            for (Reservation failed : failedList) {
                String memo = failed.getRawRequestText();
                if (memo == null || memo.isBlank()) memo = "(요청 없음)";

                System.out.printf("%-13s | %-16s | %-3d박 | %-24s | %s%n",
                        failed.getReservationId(),
                        failed.getBookedRoomType().getDescription(),
                        failed.getStayNights(),
                        failed.getPreference(),
                        memo
                );
            }
            System.out.println("--------------------------------------------------------------------------------\n");
        } else {
            System.out.println("🎉 축하합니다! 50건의 모든 예약이 100% 성공적으로 객실에 배정되었습니다.\n");
        }

        // 6. 🔄 프론트 데스크 수동 룸 체인지 시뮬레이션
        System.out.println("--------------------------------------------------------------------------------");
        System.out.println("🛎️ [프론트 데스크 실무] 수동 룸 체인지(Room Move) 시뮬레이션");
        System.out.println("--------------------------------------------------------------------------------");
        Reservation moveTarget = result.getSuccessfulAssignments().stream().findFirst().orElse(null);

        if (moveTarget != null) {
            String originRoom = moveTarget.getAssignedRoomNumber();
            StayPeriod movePeriod = new StayPeriod(moveTarget.getCheckInDate(), moveTarget.getStayNights());

            // 동일 타입의 다른 공실 탐색
            Room emptySameTypeRoom = repository.findAll().stream()
                    .filter(room -> room.getRoomType() == moveTarget.getBookedRoomType())
                    .filter(room -> !room.getRoomNumber().equals(originRoom))
                    .filter(room -> room.isAvailable(movePeriod))
                    .findFirst()
                    .orElse(null);

            if (emptySameTypeRoom != null) {
                System.out.printf("고객 [%s] 객실 이동 요청: %s호 -> %s호%n",
                        moveTarget.getGuestName(), originRoom, emptySameTypeRoom.getRoomNumber());
                RoomChangeResult changeResult = roomChangeService.changeRoom(moveTarget, emptySameTypeRoom.getRoomNumber(), true);
                System.out.println(">> " + changeResult.message());
            } else {
                System.out.println(">> 동일 타입의 대체 공실이 없어 룸 체인지를 건너뜁니다.");
            }
        }
        System.out.println();

        // 7. 📊 일자별 191실 전 객실 JSON 인디케이터 매트릭스 출력
        System.out.println("================================================================================");
        System.out.println("📊 [191실 전 객실 룸 랙 JSON 매트릭스 (Full Room Indicator JSON)]");
        System.out.println("================================================================================");

        FloorMapResponseDto matrixReport = floorStatusService.getFloorMatrix(today, result.getSuccessfulAssignments());

        // 순수 자바로 보기 좋은 들여쓰기(Indent) JSON 포맷팅 출력
        System.out.println(formatToJson(matrixReport));

        System.out.println("\n================================================================================");
        System.out.printf("🎉 [운영 요약] 총 %d실 | 점유 %d실 (재실+신규) | 공실 %d실 | 호텔 점유율 %.1f%%%n",
                matrixReport.totalRooms(),
                matrixReport.occupiedRooms(),
                matrixReport.vacantRooms(),
                matrixReport.occupancyRatePercent());
        System.out.println("================================================================================");
    }

    /**
     * 외부 라이브러리 없이 FloorMapResponseDto를 보기 좋은 들여쓰기 JSON으로 포맷팅
     */
    private static String formatToJson(FloorMapResponseDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append(String.format("  \"targetDate\": \"%s\",\n", dto.targetDate()));
        sb.append(String.format("  \"totalRooms\": %d,\n", dto.totalRooms()));
        sb.append(String.format("  \"occupiedRooms\": %d,\n", dto.occupiedRooms()));
        sb.append(String.format("  \"vacantRooms\": %d,\n", dto.vacantRooms()));
        sb.append(String.format("  \"occupancyRatePercent\": %.1f,\n", dto.occupancyRatePercent()));
        sb.append("  \"floorRooms\": {\n");

        int floorIndex = 0;
        int floorTotal = dto.floorRooms().size();

        for (Map.Entry<Integer, List<RoomMatrixItemDto>> entry : dto.floorRooms().entrySet()) {
            int floor = entry.getKey();
            List<RoomMatrixItemDto> rooms = entry.getValue();
            floorIndex++;

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
            sb.append(String.format("    ]%s\n", (floorIndex < floorTotal) ? "," : ""));
        }

        sb.append("  }\n");
        sb.append("}");
        return sb.toString();
    }

    /**
     * 기준일 이전 체크인하여 오늘(today)도 투숙 중인 기존 예약 20실을 날짜 스케줄과 함께 생성
     */
    private static void simulateExistingCheckInsWithSchedule(RoomRepository repo, int count, LocalDate today) {
        List<Room> allRooms = repo.findAll();
        Random random = new Random(42);
        int occupied = 0;

        for (Room room : allRooms) {
            if (occupied >= count) break;
            if (random.nextBoolean()) {
                // 기준일 전날(9/19) 체크인해서 3박(9/22 체크아웃) 투숙 중인 상태 시뮬레이션
                StayPeriod inHousePeriod = new StayPeriod(today.minusDays(1), 3);
                room.bookPeriod(inHousePeriod);
                occupied++;
            }
        }
        for (Room room : allRooms) {
            if (occupied >= count) break;
            if (room.isAvailable(new StayPeriod(today, 1))) {
                StayPeriod inHousePeriod = new StayPeriod(today.minusDays(2), 4);
                room.bookPeriod(inHousePeriod);
                occupied++;
            }
        }
    }

    private static List<Reservation> generate50RealisticReservations(LocalDate today) {
        List<Reservation> list = new ArrayList<>();
        RoomType[] types = RoomType.values();

        String[] realisticNotes = {
                "어머니 무릎이 안 좋으셔서 엘리베이터 가깝고 낮은 층으로 부탁드립니다.",
                "静かに過ごしたいので、エレベーターから離れた高層階の部屋をお願いします。",
                "High floor with a nice view, far from elevator please.",
                "결혼기념일 여행이라 전망 좋은 끝방/코너룸 배정해주시면 감사하겠습니다.",
                "足が不自由なため、できるだけ低層階かつエレベーター近くの部屋が希望です。",
                "Baby sleeping, very quiet room needed away from lift noise.",
                "야간 근무 후 쉬러 갑니다. 조용한 안쪽 방으로 주세요.",
                "眺めの良い角部屋を希望します。",
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
            int nights = rand.nextInt(4) + 1; // 1~4박
            String note = realisticNotes[i % realisticNotes.length];

            // 당일 체크인 기준 예약 생성
            list.add(new Reservation(rsvId, guestName, type, today, nights, note, GuestPreference.empty()));
        }

        return list;
    }
}