package com.familylifeagent.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "shop_detail")
public class ShopDetailEntity {

    @Id
    @Column(name = "shop_id")
    private Long shopId;

    @MapsId
    @OneToOne(optional = false)
    @JoinColumn(name = "shop_id")
    private ShopEntity shop;

    @Column(name = "review_summary")
    private String reviewSummary;

    @Column(name = "signature_dishes")
    private String signatureDishes;

    @Column(name = "service_features")
    private String serviceFeatures;

    @Column(name = "environment_desc")
    private String environmentDesc;
}
