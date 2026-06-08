package com.lazyfetch.locus.data.mf.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name="mf_scheme")

public class MfScheme 
{
    @Id    
    @Column(name="scheme_code")
    private Integer schemeCode;

    @Column(name = "scheme_name", nullable=false, length=500)
    private String schemeName;

    @Column(name="fund_house",length=200)
    private String fundHouse;

    @Column(name="scheme_type",length = 100)
    private String schemeType;

    @Column(name = "scheme_category", length = 100)
    private String schemeCategory;

    @Column(name = "isin_growth", length = 20)
    private String isinGrowth;

    @Column(name = "updated_at", insertable = false, updatable = false, columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;

    public MfScheme() {}

    public MfScheme(Integer schemeCode, String schemeName, String fundHouse,
                    String schemeType, String schemeCategory, String isinGrowth) {
        this.schemeCode = schemeCode;
        this.schemeName = schemeName;
        this.fundHouse = fundHouse;
        this.schemeType = schemeType;
        this.schemeCategory = schemeCategory;
        this.isinGrowth = isinGrowth;
    }

    public Integer getSchemeCode() {
        return schemeCode;
    }

    public void setSchemeCode(Integer schemeCode) {
        this.schemeCode = schemeCode;
    }

    public String getSchemeName() {
        return schemeName;
    }

    public void setSchemeName(String schemeName) {
        this.schemeName = schemeName;
    }

    public String getFundHouse() {
        return fundHouse;
    }

    public void setFundHouse(String fundHouse) {
        this.fundHouse = fundHouse;
    }

    public String getSchemeType() {
        return schemeType;
    }

    public void setSchemeType(String schemeType) {
        this.schemeType = schemeType;
    }

    public String getSchemeCategory() {
        return schemeCategory;
    }

    public void setSchemeCategory(String schemeCategory) {
        this.schemeCategory = schemeCategory;
    }

    public String getIsinGrowth() {
        return isinGrowth;
    }

    public void setIsinGrowth(String isinGrowth) {
        this.isinGrowth = isinGrowth;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
