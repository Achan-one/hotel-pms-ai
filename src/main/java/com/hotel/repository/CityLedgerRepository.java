package com.hotel.repository;

import com.hotel.domain.BookingChannelInfo;
import com.hotel.entity.CityLedgerRecordEntity;

import java.util.List;

public interface CityLedgerRepository {
    void save(CityLedgerRecordEntity record);
    List<CityLedgerRecordEntity> findAll();
    List<CityLedgerRecordEntity> findByChannelType(BookingChannelInfo.ChannelType channelType);
    void clear();
}