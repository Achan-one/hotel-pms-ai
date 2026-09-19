package com.hotel;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.Room;
import com.hotel.domain.RoomType;
import com.hotel.repository.RoomRepository;
import com.hotel.service.AiPreferenceParser;
import com.hotel.service.BatchAssigner;
import com.hotel.service.BatchAssignmentResult;
import com.hotel.service.RoomAssigner;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class Main {

    public static void main(String[] args) {
        RoomRepository repository = new RoomRepository();
        RoomAssigner assigner = new RoomAssigner(repository);
        AiPreferenceParser aiParser = new AiPreferenceParser();

        System.out.println("================================================================================");
        System.out.println("🏨 [대량 검증] 50건 다국어 자연어 메모 -> 단 1회 Gemini API 배치 파싱 & 날짜 기반 배정");
        System.out.println("   엔진 설정: " + aiParser.getConfig());
        System.out.println("================================================================================\n");

        // 1. 사전 체크인(재실) 20실 세팅
        simulateExistingCheckIns(repository, 20);
        long preOccupied = repository.findAll().stream().filter(Room::isAssigned).count();
        System.out.printf("📌 [초기 상태] 기존 재실 객실: %d실 / 배정 가능 공실: %d실%n%n",
                preOccupied, 191 - preOccupied);

        // 2. 실제 다국어 요청 메모 및 체크인 날짜가 포함된 50개 예약 생성
        List<Reservation> rawReservations = generate50RealisticReservations();
        System.out.printf("📝 50건의 비정형 예약 생성 완료 (한국어, 일본어, 영어, 무요청 혼합)%n");
        System.out.println("   샘플 1: " + rawReservations.get(0).getRawRequestText());
        System.out.println("   샘플 2: " + rawReservations.get(1).getRawRequestText());
        System.out.println("   샘플 3: " + rawReservations.get(2).getRawRequestText());
        System.out.println();

        // 3. 단 1회의 Gemini API 호출로 50건 일괄 정제
        System.out.println("⚡ [Gemini 2.5 Flash] 50건 일괄 배치 분석 요청 전송 중 (Single API Call)...");
        long startTime = System.currentTimeMillis();
        Map<String, GuestPreference> parsedPreferences = aiParser.parseBatch(rawReservations);
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.printf("✅ AI 일괄 정제 완료! 소요시간: %d ms (분석된 선호도: %d건)%n%n", elapsed, parsedPreferences.size());

        // 4. AI가 정제해 준 선호도를 각 Reservation에 주입 (withPreference)
        List<Reservation> enrichedReservations = new ArrayList<>();
        for (Reservation rsv : rawReservations) {
            GuestPreference pref = parsedPreferences.getOrDefault(rsv.getReservationId(), GuestPreference.empty());
            enrichedReservations.add(rsv.withPreference(pref));
        }

        // 정제 샘플 3건 확인
        System.out.println("🔍 [AI 정제 샘플 확인]");
        for (int i = 0; i < 3; i++) {
            Reservation r = enrichedReservations.get(i);
            System.out.printf("   - [%s | %s] 메모: \"%s\"%n     => AI 분석: %s%n",
                    r.getReservationId(), r.getGuestName(), r.getRawRequestText(), r.getPreference());
        }
        System.out.println();

        // 5. 정제된 50개 예약으로 일괄 배정 엔진(BatchAssigner) 가동
        System.out.println("⚙️ [배치 배정 엔진] 우선순위(장기숙박, 요구조건 난이도) 기반 배정 시작...");
        BatchAssigner batchAssigner = new BatchAssigner(assigner);
        BatchAssignmentResult result = batchAssigner.assignAll(enrichedReservations);

        // 6. 50건 전체 상세 배정 표 출력 (투숙 기간 컬럼 포함)
        Map<String, Reservation> successMap = result.getSuccessfulAssignments().stream()
                .collect(java.util.stream.Collectors.toMap(Reservation::getReservationId, r -> r));

        System.out.println("==========================================================================================================================================");
        System.out.printf("%-13s | %-16s | %-23s | %-24s | %-26s | %s%n",
                "예약ID", "객실타입", "투숙 기간 (체크인~체크아웃)", "고객 요청 메모(원문)", "AI 선호도 정제", "최종 배정 결과");
        System.out.println("------------------------------------------------------------------------------------------------------------------------------------------");

        for (Reservation r : enrichedReservations) {
            String memo = r.getRawRequestText();
            if (memo == null || memo.isBlank()) memo = "(요청 없음)";
            else if (memo.length() > 16) memo = memo.substring(0, 14) + "..";

            Reservation successRes = successMap.get(r.getReservationId());
            String resultRoom = (successRes != null && successRes.isAssigned())
                    ? successRes.getAssignedRoomNumber() + "호 확정"
                    : "❌ 배정실패(만실)";

            String periodStr = String.format("%s~%s(%d박)", r.getCheckInDate(), r.getCheckOutDate(), r.getStayNights());

            System.out.printf("%-13s | %-16s | %-23s | %-22s | %-24s | %s%n",
                    r.getReservationId(),
                    r.getBookedRoomType().name(),
                    periodStr,
                    memo,
                    r.getPreference(),
                    resultRoom
            );
        }
        System.out.println("==========================================================================================================================================\n");

        System.out.println(result.toSummaryString());
    }

    private static void simulateExistingCheckIns(RoomRepository repo, int count) {
        List<Room> vacantRooms = repo.findAll().stream().filter(r -> !r.isAssigned()).toList();
        Random random = new Random(42);
        int occupied = 0;
        for (Room room : vacantRooms) {
            if (occupied >= count) break;
            if (random.nextBoolean()) {
                room.assign();
                occupied++;
            }
        }
        for (Room room : vacantRooms) {
            if (occupied >= count) break;
            if (!room.isAssigned()) {
                room.assign();
                occupied++;
            }
        }
    }

    private static List<Reservation> generate50RealisticReservations() {
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
        LocalDate baseDate = LocalDate.now();

        for (int i = 1; i <= 50; i++) {
            String rsvId = String.format("RSV-BATCH-%03d", i);
            String guestName = "Guest_" + i;
            RoomType type = types[rand.nextInt(types.length)];

            // 체크인 날짜를 오늘, 내일, 모레 중 하나로 분산
            LocalDate checkIn = baseDate.plusDays(rand.nextInt(3));
            int nights = rand.nextInt(4) + 1; // 1~4박
            String note = realisticNotes[i % realisticNotes.length];

            list.add(new Reservation(rsvId, guestName, type, checkIn, nights, note, GuestPreference.empty()));
        }

        return list;
    }
}