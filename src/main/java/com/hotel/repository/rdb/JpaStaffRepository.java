package com.hotel.repository.rdb;

import com.hotel.domain.StaffAccount;
import com.hotel.entity.LoginAttemptEntity;
import com.hotel.entity.StaffAccountEntity;
import com.hotel.repository.StaffRepository;
import com.hotel.repository.jpa.SpringDataLoginAttemptRepository;
import com.hotel.repository.jpa.SpringDataStaffRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.hotel.domain.StaffRole;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

@Repository
@Primary
public class JpaStaffRepository implements StaffRepository {

    private final SpringDataStaffRepository jpaRepo;
    private final SpringDataLoginAttemptRepository attemptRepo;

    public JpaStaffRepository(SpringDataStaffRepository jpaRepo, SpringDataLoginAttemptRepository attemptRepo) {
        this.jpaRepo = Objects.requireNonNull(jpaRepo);
        this.attemptRepo = Objects.requireNonNull(attemptRepo);
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

    @Override
    @Transactional(readOnly = true)
    public boolean isLoginLocked(String staffId, String clientIp, LocalDateTime now) {
        String id = normalize(staffId);
        if (id == null) return false;
        return attemptRepo.findById(new LoginAttemptEntity.Key(id, ip(clientIp)))
                .map(a -> a.getLockedUntil() != null && a.getLockedUntil().isAfter(now))
                .orElse(false);
    }

    @Override
    @Transactional
    public void recordLoginFailure(String staffId, String clientIp, int maxAttempts, LocalDateTime lockUntil) {
        String id = normalize(staffId);
        if (id == null) return;
        String address = ip(clientIp);
        attemptRepo.incrementFailures(id, address);
        attemptRepo.lockIfExceeded(id, address, maxAttempts, lockUntil);
    }

    @Override
    @Transactional
    public void recordLoginSuccess(String staffId, String clientIp) {
        String id = normalize(staffId);
        if (id == null) return;
        attemptRepo.deleteByStaffIdAndClientIp(id, ip(clientIp));
    }

    @Override
    @Transactional
    public boolean updateEnabled(String staffId, boolean enabled) {
        String id = normalize(staffId);
        if (id == null) return false;
        boolean found = jpaRepo.updateEnabled(id, enabled) > 0;
        if (found) {
            attemptRepo.deleteByStaffId(id);
        }
        return found;
    }

    @Override
    @Transactional
    public long lockAndCountEnabledByRole(StaffRole role) {
        return jpaRepo.findEnabledByRoleForUpdate(role).size();
    }

    private static String ip(String clientIp) {
        String value = (clientIp == null || clientIp.isBlank()) ? "unknown" : clientIp.trim();
        return value.length() > 64 ? value.substring(0, 64) : value;
    }

    private static String normalize(String staffId) {
        return (staffId == null || staffId.isBlank()) ? null : staffId.trim().toLowerCase();
    }
}
