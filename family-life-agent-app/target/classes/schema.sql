DROP TABLE IF EXISTS reservation;
DROP TABLE IF EXISTS voucher;
DROP TABLE IF EXISTS shop_detail;
DROP TABLE IF EXISTS shop;
DROP TABLE IF EXISTS shop_type;

CREATE TABLE shop_type (
    id BIGINT PRIMARY KEY,
    type_code VARCHAR(64) NOT NULL UNIQUE,
    type_name VARCHAR(64) NOT NULL,
    description VARCHAR(255) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店铺类型表';

CREATE TABLE shop (
    id BIGINT PRIMARY KEY,
    shop_name VARCHAR(128) NOT NULL,
    shop_type_id BIGINT NOT NULL,
    area VARCHAR(64) NOT NULL,
    address VARCHAR(255) NOT NULL,
    average_price INT NOT NULL,
    rating DECIMAL(2,1) NOT NULL,
    open_now TINYINT(1) NOT NULL DEFAULT 1,
    suitable_for_elderly TINYINT(1) NOT NULL DEFAULT 0,
    has_children_play_area TINYINT(1) NOT NULL DEFAULT 0,
    business_hours VARCHAR(64) NOT NULL,
    tags VARCHAR(255) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_shop_type FOREIGN KEY (shop_type_id) REFERENCES shop_type(id),
    INDEX idx_shop_area_type (area, shop_type_id),
    INDEX idx_shop_rating (rating),
    INDEX idx_shop_avg_price (average_price)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户主表';

CREATE TABLE shop_detail (
    shop_id BIGINT PRIMARY KEY,
    review_summary VARCHAR(500) DEFAULT NULL,
    signature_dishes VARCHAR(500) DEFAULT NULL,
    service_features VARCHAR(500) DEFAULT NULL,
    environment_desc VARCHAR(255) DEFAULT NULL,
    CONSTRAINT fk_shop_detail_shop FOREIGN KEY (shop_id) REFERENCES shop(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商户详情表';

CREATE TABLE voucher (
    id BIGINT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    voucher_code VARCHAR(64) NOT NULL UNIQUE,
    title VARCHAR(128) NOT NULL,
    applicable_scope VARCHAR(255) DEFAULT NULL,
    discount_type VARCHAR(32) NOT NULL,
    threshold_amount DECIMAL(10,2) DEFAULT NULL,
    discount_amount DECIMAL(10,2) DEFAULT NULL,
    valid_from DATETIME NOT NULL,
    valid_to DATETIME NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT fk_voucher_shop FOREIGN KEY (shop_id) REFERENCES shop(id),
    INDEX idx_voucher_shop (shop_id),
    INDEX idx_voucher_valid_to (valid_to)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券表';

CREATE TABLE reservation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    shop_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    reserve_time DATETIME NOT NULL,
    party_size INT NOT NULL DEFAULT 2,
    special_requests VARCHAR(255) DEFAULT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CONFIRMED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_reservation_shop FOREIGN KEY (shop_id) REFERENCES shop(id),
    INDEX idx_reservation_shop (shop_id),
    INDEX idx_reservation_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预约/排号表';
