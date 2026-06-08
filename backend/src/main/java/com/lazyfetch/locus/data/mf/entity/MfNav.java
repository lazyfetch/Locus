package com.lazyfetch.locus.data.mf.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "mf_nav_history", uniqueConstraints = @UniqueConstraint(columnNames = {"scheme_code", "nav_date"}))

public class MfNav {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_code", nullable = false)
    private Integer schemeCode;

    @Column(name = "nav_date", nullable = false)
    private LocalDate navDate;

    @Column(name = "nav", nullable = false, precision = 10, scale = 4)
    private BigDecimal nav;

    public MfNav() {}

    public MfNav(Long id, BigDecimal nav, LocalDate navDate, Integer schemeCode) {
        this.id = id;
        this.nav = nav;
        this.navDate = navDate;
        this.schemeCode = schemeCode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getSchemeCode() {
        return schemeCode;
    }

    public void setSchemeCode(Integer schemeCode) {
        this.schemeCode = schemeCode;
    }

    public LocalDate getNavDate() {
        return navDate;
    }

    public void setNavDate(LocalDate navDate) {
        this.navDate = navDate;
    }

    public BigDecimal getNav() {
        return nav;
    }

    public void setNav(BigDecimal nav) {
        this.nav = nav;
    }

    
}
