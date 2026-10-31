package com.offbyone.repository;

import com.offbyone.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update User u set u.username = concat('~expired~', u.email) where u.sessionExpiresAt <= :now and u.username not like '~expired~%'")
    int releaseExpiredNames(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
