package com.familylifeagent.infrastructure.repository;

import com.familylifeagent.infrastructure.entity.VoucherEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VoucherRepository extends JpaRepository<VoucherEntity, Long> {

    List<VoucherEntity> findByShopIdOrderByValidToAsc(Long shopId);
}
