package com.offbyone.repository;

import com.offbyone.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update User u set u.username = concat('~expired~', u.email) where u.sessionExpiresAt <= :now and u.username not like '~expired~%'")
    int releaseExpiredNames(@org.springframework.data.repository.query.Param("now") java.time.LocalDateTime now);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> lockById(@org.springframework.data.repository.query.Param("id") UUID id);
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
