package com.hotel.channel;

import com.hotel.channel.dto.ChannelInventorySyncDto;
import com.hotel.domain.QuotaPolicy;
import com.hotel.domain.Room;
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
     * 수리/점검(OOO) 방과 해당 일자에 스케줄이 잡힌 방을 뺀 실제 판매 가능 공실 수를 구한다
     */
    public List<ChannelInventorySyncDto> calculateDailySellableInventory(LocalDate targetDate) {
        List<ChannelInventorySyncDto> results = new ArrayList<>();
        StayPeriod singleDay = new StayPeriod(targetDate, 1);
        List<Room> allRooms = roomRepository.findAll();

        for (RoomType type : RoomType.values()) {
            long physicalVacant = allRooms.stream()
                    .filter(r -> r.getRoomType() == type)
                    .filter(r -> !r.getStatus().isOutOfService()) // 고장/점검 객실 제외
                    .filter(r -> r.isAvailable(singleDay))         // 스케줄 충돌 없는 방
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

    public String buildChannelPayload(ChannelManagerAdapter adapter, LocalDate targetDate) {
        List<ChannelInventorySyncDto> syncData = calculateDailySellableInventory(targetDate);
        return adapter.serializeInventoryUpdate(syncData);
    }
}