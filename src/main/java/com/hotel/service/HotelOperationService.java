package com.hotel.service;

import com.hotel.entity.HotelOperationStatusEntity; // entity 패키지 참조
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

    /**
     * 나이트 오딧을 시작하기 전에 호출한다. 영업일 행을 잠가 동시 실행을 막고,
     * 현재 영업일이 아니거나 이미 마감한 날짜면 거부한다. 잠금은 호출한 트랜잭션이 끝날 때까지 유지된다.
     */
    public void verifyAuditable(LocalDate auditDate) {
        HotelOperationStatusEntity status = lockStatus();
        if (!status.getBusinessDate().equals(auditDate)) {
            throw new IllegalStateException(String.format(
                    "나이트 오딧은 현재 영업일(%s)만 실행할 수 있습니다. 요청 일자: %s",
                    status.getBusinessDate(), auditDate));
        }
        if (status.getLastAuditedDate() != null && !auditDate.isAfter(status.getLastAuditedDate())) {
            throw new IllegalStateException(String.format("이미 나이트 오딧을 마감한 영업일입니다: %s", auditDate));
        }
    }

    /**
     * 감사한 영업일을 마감하고 다음 영업일로 넘긴다. {@link #verifyAuditable}과 같은 트랜잭션에서 호출해야 한다.
     */
    public LocalDate completeAudit(LocalDate auditDate) {
        HotelOperationStatusEntity status = lockStatus();
        status.completeAudit(auditDate);
        statusRepository.save(status);
        return status.getBusinessDate();
    }

    public void setBusinessDate(LocalDate newDate) {
        HotelOperationStatusEntity status = statusRepository.findById(DEFAULT_PROPERTY_ID)
                .orElseGet(() -> HotelOperationStatusEntity.defaultStatus(newDate));

        status.updateBusinessDate(newDate);
        statusRepository.save(status);
    }

    /**
     * 개발용 시뮬레이션 초기화. 영업일을 되돌리면서 마감 기록도 지운다.
     */
    public void resetBusinessDate(LocalDate newDate) {
        HotelOperationStatusEntity status = statusRepository.findById(DEFAULT_PROPERTY_ID)
                .orElseGet(() -> HotelOperationStatusEntity.defaultStatus(newDate));

        status.updateBusinessDate(newDate);
        status.resetAuditGuard();
        statusRepository.save(status);
    }

    private HotelOperationStatusEntity lockStatus() {
        return statusRepository.findByIdForUpdate(DEFAULT_PROPERTY_ID)
                .orElseGet(() -> statusRepository.save(HotelOperationStatusEntity.defaultStatus(DEFAULT_INITIAL_DATE)));
    }
}