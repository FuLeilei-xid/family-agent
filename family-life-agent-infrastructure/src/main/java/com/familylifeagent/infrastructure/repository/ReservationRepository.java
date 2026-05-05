package com.familylifeagent.infrastructure.repository;

import com.familylifeagent.infrastructure.entity.ReservationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReservationRepository extends JpaRepository<ReservationEntity, Long> {

    List<ReservationEntity> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<ReservationEntity> findByShopIdAndStatus(Long shopId, String status);
}
