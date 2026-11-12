package com.fincore.payment.repository;

import com.fincore.payment.domain.IdempotencyKey;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, String> {

    @Modifying
    @Query(value = "INSERT INTO idempotency_keys (key_value) VALUES (:key) ON CONFLICT (key_value) DO NOTHING",
            nativeQuery = true)
    void provision(@Param("key") String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT k FROM IdempotencyKey k WHERE k.keyValue = :key")
    Optional<IdempotencyKey> findForUpdate(@Param("key") String key);
}