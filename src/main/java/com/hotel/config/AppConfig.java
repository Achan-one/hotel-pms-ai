package com.hotel.config;

import com.hotel.channel.ChannelSyncService;
import com.hotel.channel.onda.OndaChannelAdapter;
import com.hotel.channel.tlx.TlxChannelAdapter;
import com.hotel.domain.QuotaPolicy;
import com.hotel.repository.ReservationRepository;
import com.hotel.repository.RoomRepository;
import com.hotel.repository.TagRepository;
import com.hotel.repository.memory.InMemoryReservationRepository;
import com.hotel.repository.memory.InMemoryRoomRepository;
import com.hotel.repository.memory.InMemoryTagRepository;
import com.hotel.service.*;
import com.hotel.service.report.ReportExportService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    // 1. 저장소 계층 (인메모리 싱글톤)
    @Bean
    public RoomRepository roomRepository() {
        return new InMemoryRoomRepository();
    }

    @Bean
    public ReservationRepository reservationRepository() {
        return new InMemoryReservationRepository();
    }

    @Bean
    public TagRepository tagRepository() {
        return new InMemoryTagRepository();
    }

    // 2. 운영 정책 및 AI 파서
    @Bean
    public QuotaPolicy quotaPolicy() {
        return new QuotaPolicy();
    }

    @Bean
    public AiPreferenceParser aiPreferenceParser(TagRepository tagRepository) {
        return new AiPreferenceParser(tagRepository);
    }

    // 3. 배정 및 도메인 엔진
    @Bean
    public RoomAssigner roomAssigner(RoomRepository roomRepository,
                                     TagRepository tagRepository,
                                     QuotaPolicy quotaPolicy) {
        return new RoomAssigner(roomRepository, tagRepository, quotaPolicy);
    }

    @Bean
    public BatchAssigner batchAssigner(RoomAssigner roomAssigner) {
        return new BatchAssigner(roomAssigner);
    }

    @Bean
    public RoomChangeService roomChangeService(RoomRepository roomRepository) {
        return new RoomChangeService(roomRepository);
    }

    // 4. 핵심 서비스 계층
    @Bean
    public ReservationService reservationService(ReservationRepository reservationRepository,
                                                 RoomRepository roomRepository,
                                                 AiPreferenceParser aiPreferenceParser,
                                                 TagRepository tagRepository,
                                                 QuotaPolicy quotaPolicy) {
        return new ReservationService(
                reservationRepository,
                roomRepository,
                aiPreferenceParser,
                tagRepository,
                quotaPolicy
        );
    }

    @Bean
    public FloorStatusService floorStatusService(RoomRepository roomRepository) {
        return new FloorStatusService(roomRepository);
    }

    @Bean
    public ReportExportService reportExportService(ReservationRepository reservationRepository,
                                                   RoomRepository roomRepository,
                                                   RoomAssigner roomAssigner) {
        return new ReportExportService(reservationRepository, roomRepository, roomAssigner);
    }

    @Bean
    public AdminTagService adminTagService(TagRepository tagRepository, QuotaPolicy quotaPolicy) {
        return new AdminTagService(tagRepository, quotaPolicy);
    }

    // 5. 채널 연동 서비스 및 어댑터
    @Bean
    public ChannelSyncService channelSyncService(RoomRepository roomRepository, QuotaPolicy quotaPolicy) {
        return new ChannelSyncService(roomRepository, quotaPolicy);
    }

    @Bean
    public TlxChannelAdapter tlxChannelAdapter() {
        return new TlxChannelAdapter();
    }

    @Bean
    public OndaChannelAdapter ondaChannelAdapter() {
        return new OndaChannelAdapter();
    }
}