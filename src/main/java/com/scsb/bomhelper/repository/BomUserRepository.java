package com.scsb.bomhelper.repository;

import com.scsb.bomhelper.entity.BomUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BomUserRepository extends JpaRepository<BomUser, String> {
    java.util.Optional<BomUser> findByUserIdIgnoreCase(String userId);
    boolean existsByUserIdIgnoreCase(String userId);
}
