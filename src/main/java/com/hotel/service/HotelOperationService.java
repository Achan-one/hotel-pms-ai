package com.hotel.service;

import com.hotel.entity.HotelOperationStatusEntity; // 👈 entity 패키지 참조
import com.hotel.repository.jpa.SpringDataHotelOperationStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Transactional
public class HotelOperationService {

    public static final String DEFAULT_PROPERTY_ID = "DEFAULT";
    private static final LocalDate DEFAULT_INITIAL_DATE = LocalDate.of(2026, 9, 20);

    private final SpringDataHotelOperationStatusRepository statusRepository;

    public HotelOperationService(SpringDataHotelOperationStatusRepository statusRepository) {
        this.statusRepository = statusRepository;
    }

    @Transactional(readOnly = true)
    public LocalDate getCurrentBusinessDate() {
        return statusRepository.findById(DEFAULT_PROPERTY_ID)
                .map(HotelOperationStatusEntity::getBusinessDate)
                .orElse(DEFAULT_INITIAL_DATE);
    }

    public LocalDate rolloverToNextDate() {
        HotelOperationStatusEntity status = statusRepository.findById(DEFAULT_PROPERTY_ID)
                .orElseGet(() -> statusRepository.save(HotelOperationStatusEntity.defaultStatus(DEFAULT_INITIAL_DATE)));

        LocalDate nextDate = status.getBusinessDate().plusDays(1);
        status.rollover(nextDate);
        statusRepository.save(status);
        return nextDate;
    }

    public void setBusinessDate(LocalDate newDate) {
        HotelOperationStatusEntity status = statusRepository.findById(DEFAULT_PROPERTY_ID)
                .orElseGet(() -> HotelOperationStatusEntity.defaultStatus(newDate));

        status.updateBusinessDate(newDate);
        statusRepository.save(status);
    }
}