package com.familylifeagent.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "voucher")
public class VoucherEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private ShopEntity shop;

    @Column(name = "voucher_code", nullable = false, unique = true)
    private String voucherCode;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "applicable_scope")
    private String applicableScope;

    @Column(name = "discount_type", nullable = false)
    private String discountType;

    @Column(name = "threshold_amount")
    private BigDecimal thresholdAmount;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "valid_from", nullable = false)
    private LocalDateTime validFrom;

    @Column(name = "valid_to", nullable = false)
    private LocalDateTime validTo;

    @Column(name = "status", nullable = false)
    private String status;
}
