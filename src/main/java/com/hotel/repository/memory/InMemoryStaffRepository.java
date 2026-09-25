//package com.hotel.repository.memory;
//
//import com.hotel.domain.StaffAccount;
//import com.hotel.domain.StaffRole;
//import com.hotel.repository.StaffRepository;
//import org.springframework.security.crypto.password.PasswordEncoder;
//import org.springframework.stereotype.Repository;
//
//import java.util.Map;
//import java.util.Optional;
//import java.util.concurrent.ConcurrentHashMap;
//
//@Repository
//public class InMemoryStaffRepository implements StaffRepository {
//
//    private final Map<String, StaffAccount> store = new ConcurrentHashMap<>();
//
//    public InMemoryStaffRepository(PasswordEncoder passwordEncoder) {
//        // 기본 초기화 계정 (비밀번호: 모두 "hotel1234")
//        save(new StaffAccount("admin", passwordEncoder.encode("hotel1234"), "총지배인", StaffRole.ROLE_ADMIN));
//        save(new StaffAccount("staff", passwordEncoder.encode("hotel1234"), "정규사원", StaffRole.ROLE_STAFF));
//        save(new StaffAccount("part_time", passwordEncoder.encode("hotel1234"), "아르바이트", StaffRole.ROLE_PART_TIME));
//        save(new StaffAccount("guest", passwordEncoder.encode("hotel1234"), "테스트고객", StaffRole.ROLE_GUEST));
//    }
//
//    @Override
//    public Optional<StaffAccount> findByStaffId(String staffId) {
//        if (staffId == null || staffId.isBlank()) return Optional.empty();
//        return Optional.ofNullable(store.get(staffId.trim().toLowerCase()));
//    }
//
//    @Override
//    public void save(StaffAccount account) {
//        if (account != null) {
//            store.put(account.staffId().trim().toLowerCase(), account);
//        }
//    }
//}