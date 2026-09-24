package com.hotel.repository.rdb;

import com.hotel.domain.StaffAccount;
import com.hotel.entity.StaffAccountEntity;
import com.hotel.repository.StaffRepository;
import com.hotel.repository.jpa.SpringDataStaffRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;

@Repository
@Primary
public class JpaStaffRepository implements StaffRepository {

    private final SpringDataStaffRepository jpaRepo;

    public JpaStaffRepository(SpringDataStaffRepository jpaRepo) {
        this.jpaRepo = Objects.requireNonNull(jpaRepo);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffAccount> findByStaffId(String staffId) {
        if (staffId == null || staffId.isBlank()) return Optional.empty();
        return jpaRepo.findById(staffId.trim().toLowerCase())
                .map(StaffAccountEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(StaffAccount account) {
        if (account == null) return;
        jpaRepo.save(StaffAccountEntity.fromDomain(account));
    }
}