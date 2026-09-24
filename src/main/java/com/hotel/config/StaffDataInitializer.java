package com.hotel.config;

import com.hotel.domain.StaffAccount;
import com.hotel.domain.StaffRole;
import com.hotel.repository.StaffRepository;
import com.hotel.util.EnvLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class StaffDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StaffDataInitializer.class);

    private final StaffRepository staffRepository;
    private final PasswordEncoder passwordEncoder;

    public StaffDataInitializer(StaffRepository staffRepository, PasswordEncoder passwordEncoder) {
        this.staffRepository = staffRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        // 1. 총지배인 (ROLE_ADMIN) 주입
        initAccountFromEnv("INIT_ADMIN_ID", "INIT_ADMIN_PASSWORD", "INIT_ADMIN_NAME", StaffRole.ROLE_ADMIN);

        // 2. 정규사원 (ROLE_STAFF) 주입
        initAccountFromEnv("INIT_STAFF_ID", "INIT_STAFF_PASSWORD", "INIT_STAFF_NAME", StaffRole.ROLE_STAFF);

        // 3. 아르바이트 (ROLE_PART_TIME) 주입
        initAccountFromEnv("INIT_PART_TIME_ID", "INIT_PART_TIME_PASSWORD", "INIT_PART_TIME_NAME", StaffRole.ROLE_PART_TIME);
    }

    private void initAccountFromEnv(String idKey, String pwKey, String nameKey, StaffRole role) {
        String staffId = EnvLoader.get(idKey);
        String rawPassword = EnvLoader.get(pwKey);
        String staffName = EnvLoader.get(nameKey);

        if (staffId == null || staffId.isBlank() || rawPassword == null || rawPassword.isBlank()) {
            return;
        }

        String normalizedId = staffId.trim().toLowerCase();
        String name = (staffName != null && !staffName.isBlank()) ? staffName.trim() : role.getDescription();

        if (staffRepository.findByStaffId(normalizedId).isEmpty()) {
            staffRepository.save(new StaffAccount(
                    normalizedId,
                    passwordEncoder.encode(rawPassword.trim()),
                    name,
                    role
            ));
            log.info("🛡️ [StaffDataInitializer] .env 환경 변수로부터 계정 생성 완료: {} ({})", normalizedId, role);
        }
    }
}