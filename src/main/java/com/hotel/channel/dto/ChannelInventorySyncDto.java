package com.hotel.channel.dto;

import com.hotel.domain.RoomType;
import java.time.LocalDate;

/**
 * 채널 매니저로 전송할 일자별/타입별 재고 및 요금 표준 동기화 모델 (ARI)
 */
public record ChannelInventorySyncDto(
        LocalDate targetDate,
        RoomType roomType,
        long physicalVacant,     // 물리적 잔여 공실
        int holdQuota,           // 호텔 내부 안전 킵 수량
        long sellableInventory,  // CMS에 열어줄 최종 판매 가능 수량 = Max(0, physicalVacant - holdQuota)
        int rateYen              // 1박 요금 (기본 통화 기준)
) {}