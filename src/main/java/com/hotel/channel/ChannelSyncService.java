package com.hotel.channel;

import com.hotel.channel.dto.ChannelInventorySyncDto;
import com.hotel.domain.QuotaPolicy;
import com.hotel.domain.RoomType;
import com.hotel.domain.StayPeriod;
import com.hotel.repository.RoomRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ChannelSyncService {

    private final RoomRepository roomRepository;
    private final QuotaPolicy quotaPolicy;

    public ChannelSyncService(RoomRepository roomRepository, QuotaPolicy quotaPolicy) {
        this.roomRepository = Objects.requireNonNull(roomRepository, "roomRepository는 필수입니다.");
        this.quotaPolicy = Objects.requireNonNull(quotaPolicy, "quotaPolicy는 필수입니다.");
    }

    /**
     * 일자별 각 룸타입의 (물리 공실 - 킵 수량)을 계산하여 표준 ARI 데이터 생성
     */
    public List<ChannelInventorySyncDto> calculateDailySellableInventory(LocalDate targetDate) {
        List<ChannelInventorySyncDto> results = new ArrayList<>();
        StayPeriod singleDay = new StayPeriod(targetDate, 1);

        for (RoomType type : RoomType.values()) {
            long physicalVacant = roomRepository.findAll().stream()
                    .filter(r -> r.getRoomType() == type)
                    .filter(r -> r.isAvailable(singleDay))
                    .count();

            int holdQuota = quotaPolicy.getTypeHoldQuota(type);
            long sellable = Math.max(0, physicalVacant - holdQuota);

            int baseRate = switch (type) {
                case MODERATE_DOUBLE -> 12_000;
                case SUPERIOR_DOUBLE -> 15_000;
                case SUPERIOR_TWIN -> 16_000;
                case RESIDENTIAL_DOUBLE -> 18_000;
                case EXECUTIVE_DOUBLE -> 28_000;
            };

            results.add(new ChannelInventorySyncDto(
                    targetDate, type, physicalVacant, holdQuota, sellable, baseRate
            ));
        }

        return results;
    }

    /**
     * 주입된 채널 어댑터를 이용해 최종 전송 전문 생성
     */
    public String buildChannelPayload(ChannelManagerAdapter adapter, LocalDate targetDate) {
        List<ChannelInventorySyncDto> syncData = calculateDailySellableInventory(targetDate);
        return adapter.serializeInventoryUpdate(syncData);
    }
}