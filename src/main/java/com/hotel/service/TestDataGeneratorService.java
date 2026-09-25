package com.hotel.service;

import com.hotel.domain.GuestPreference;
import com.hotel.domain.Reservation;
import com.hotel.domain.RoomType;
import com.hotel.repository.ReservationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@Transactional
public class TestDataGeneratorService {

    private final ReservationRepository reservationRepository;
    private final ReservationService reservationService;

    // 50명의 고유한 실명 리스트 (중복 배제)
    private static final String[] UNIQUE_NAMES = {
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

    private static final String[] OTA_CHANNELS = {
            "AGODA", "BOOKING_COM", "EXPEDIA", "RAKUTEN", "TRIP_COM", "AIRBNB"
    };

    private static final String[] GUEST_MEMOS = {
            "고층 객실 희망합니다.", "조용한 방으로 부탁드립니다.", "엘리베이터 가까운 방 희망",
            "금연실 필수, 고층 뷰 원함", "장애인 편의시설 필요", "코너룸 선호합니다",
            "침대 가드 요청", "늦은 체크인 예정 (21시)", "도쿄타워 전망 희망"
    };

    public TestDataGeneratorService(ReservationRepository reservationRepository,
                                    ReservationService reservationService) {
        this.reservationRepository = reservationRepository;
        this.reservationService = reservationService;
    }

    /**
     * 🚀 지정된 기준일자(baseDate)를 중심으로 50개의 고유 예약 생성 및 도메인 라이프사이클 분배
     */
    public List<Reservation> generate50DynamicReservations(LocalDate baseDate) {
        Random random = new Random(baseDate.toEpochDay()); // 동일 일자에 대해 일관된 시드 보장
        List<Reservation> generatedList = new ArrayList<>();
        RoomType[] roomTypes = RoomType.values();

        for (int i = 0; i < 50; i++) {
            String rsvId = String.format("RSV-%s-%03d", baseDate.toString().replace("-", ""), i + 1);
            String guestName = UNIQUE_NAMES[i];
            RoomType roomType = roomTypes[random.nextInt(roomTypes.length)];
            String ota = OTA_CHANNELS[random.nextInt(OTA_CHANNELS.length)];
            String memo = GUEST_MEMOS[random.nextInt(GUEST_MEMOS.length)];

            // 라이프사이클 다양화: 당일 도착(25건), 전일 도착 체류중(15건), 사전 예약(10건)
            LocalDate checkInDate;
            int nights;

            if (i < 25) {
                // 오늘 도착 고객
                checkInDate = baseDate;
                nights = 1 + random.nextInt(4); // 1~4박
            } else if (i < 40) {
                // 어제 이전 체크인하여 오늘 체류(In-House) 중인 고객
                checkInDate = baseDate.minusDays(1 + random.nextInt(2));
                nights = 3 + random.nextInt(3); // 3~5박
            } else {
                // 내일 이후 도착할 미래 예약
                checkInDate = baseDate.plusDays(1 + random.nextInt(3));
                nights = 2 + random.nextInt(3);
            }

            Reservation rsv = new Reservation(
                    rsvId, guestName, roomType, checkInDate, nights, memo, GuestPreference.empty()
            );
            // OTA 채널 세팅 (도메인 메서드 또는 필드 연결)
            rsv.updateOperationalDetails(guestName, checkInDate, nights, String.format("[%s] %s", ota, memo));
            generatedList.add(rsv);
        }

        // 1. 예약 접수
        reservationService.receiveReservations(generatedList);

        // 2. 당일 이전 고객은 자동 배정 및 체크인 상태로 전이
        reservationService.runDailyBatchAssignment(baseDate.minusDays(2));
        reservationService.runDailyBatchAssignment(baseDate.minusDays(1));
        reservationService.runDailyBatchAssignment(baseDate);

        for (Reservation rsv : generatedList) {
            if (rsv.getCheckInDate().isBefore(baseDate) && rsv.isAssigned()) {
                try {
                    reservationService.processCheckIn(rsv.getReservationId());
                } catch (Exception ignored) {}
            }
        }

        return generatedList;
    }
}