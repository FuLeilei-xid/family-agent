package com.familylifeagent.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "shop")
public class ShopEntity {

    @Id
    private Long id;

    @Column(name = "shop_name", nullable = false)
    private String shopName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_type_id", nullable = false)
    private ShopTypeEntity shopType;

    @Column(name = "area", nullable = false)
    private String area;

    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "average_price", nullable = false)
    private Integer averagePrice;

    @Column(name = "rating", nullable = false)
    private Double rating;

    @Column(name = "open_now", nullable = false)
    private Boolean openNow;

    @Column(name = "suitable_for_elderly", nullable = false)
    private Boolean suitableForElderly;

    @Column(name = "has_children_play_area", nullable = false)
    private Boolean hasChildrenPlayArea;

    @Column(name = "business_hours", nullable = false)
    private String businessHours;

    @Column(name = "tags")
    private String tags;

    @OneToOne(mappedBy = "shop", fetch = FetchType.LAZY)
    private ShopDetailEntity detail;
}
