package com.omakase.kok.store.domain.vo;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 프록시 생성용 — 외부 직접 생성 차단
@AllArgsConstructor
public class Address {

    @Column(name = "address_sido", nullable = false, length = 20)
    private String sido;

    @Column(name = "address_sigungu", nullable = false, length = 30)
    private String sigungu;

    @Column(name = "address_dong", length = 30)
    private String dong;

    @Column(name = "address_detail", length = 100)
    private String detail;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;
}
