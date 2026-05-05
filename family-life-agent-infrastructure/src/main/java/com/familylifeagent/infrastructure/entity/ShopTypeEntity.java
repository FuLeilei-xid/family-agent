package com.familylifeagent.infrastructure.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "shop_type")
public class ShopTypeEntity {

    @Id
    private Long id;

    @Column(name = "type_code", nullable = false, unique = true)
    private String typeCode;

    @Column(name = "type_name", nullable = false)
    private String typeName;

    @Column(name = "description")
    private String description;
}
