package com.hotel.repository.jpa;

import com.hotel.domain.BookingChannelInfo;
import com.hotel.entity.CityLedgerRecordEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SpringDataCityLedgerRepository extends JpaRepository<CityLedgerRecordEntity, Long> {
    List<CityLedgerRecordEntity> findByChannelType(BookingChannelInfo.ChannelType channelType);
    List<CityLedgerRecordEntity> findAllByOrderBySettledDateDesc();
}