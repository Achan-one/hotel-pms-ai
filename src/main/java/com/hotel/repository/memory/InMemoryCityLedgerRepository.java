package com.hotel.repository.memory;

import com.hotel.domain.BookingChannelInfo;
import com.hotel.entity.CityLedgerRecordEntity;
import com.hotel.repository.CityLedgerRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class InMemoryCityLedgerRepository implements CityLedgerRepository {

    private final List<CityLedgerRecordEntity> records = new CopyOnWriteArrayList<>();

    @Override
    public void save(CityLedgerRecordEntity record) {
        if (record != null) {
            records.add(record);
        }
    }

    @Override
    public List<CityLedgerRecordEntity> findAll() {
        List<CityLedgerRecordEntity> list = new ArrayList<>(records);
        list.sort(Comparator.comparing(CityLedgerRecordEntity::getSettledDate).reversed());
        return Collections.unmodifiableList(list);
    }

    @Override
    public List<CityLedgerRecordEntity> findByChannelType(BookingChannelInfo.ChannelType channelType) {
        return records.stream()
                .filter(r -> r.getChannelType() == channelType)
                .toList();
    }

    @Override
    public void clear() {
        records.clear();
    }
}