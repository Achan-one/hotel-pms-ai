package com.hotel.repository.rdb;

import com.hotel.domain.BookingChannelInfo;
import com.hotel.entity.CityLedgerRecordEntity;
import com.hotel.repository.CityLedgerRepository;
import com.hotel.repository.jpa.SpringDataCityLedgerRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Repository
public class JpaCityLedgerRepository implements CityLedgerRepository {

    private final SpringDataCityLedgerRepository jpaRepo;

    public JpaCityLedgerRepository(SpringDataCityLedgerRepository jpaRepo) {
        this.jpaRepo = Objects.requireNonNull(jpaRepo);
    }

    @Override
    @Transactional
    public void save(CityLedgerRecordEntity record) {
        Objects.requireNonNull(record, "CityLedger 기록은 null일 수 없습니다.");
        jpaRepo.save(record);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CityLedgerRecordEntity> findAll() {
        return jpaRepo.findAllByOrderBySettledDateDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CityLedgerRecordEntity> findByChannelType(BookingChannelInfo.ChannelType channelType) {
        return jpaRepo.findByChannelType(channelType);
    }

    @Override
    @Transactional
    public void clear() {
        jpaRepo.deleteAll();
    }
}