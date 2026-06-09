package com.lazyfetch.locus.data.mf.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MfApiScheme {
    @JsonProperty("schemeCode")
    private Integer schemeCode;

    @JsonProperty("schemeName")
    private String schemeName;

    @JsonProperty("isinGrowth")
    private String isinGrowth;

    @JsonProperty("isinDivReinvestment")
    private String isinDivReinvestment;

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

    public String getIsinGrowth() {
        return isinGrowth;
    }

    public void setIsinGrowth(String isinGrowth) {
        this.isinGrowth = isinGrowth;
    }

    public String getIsinDivReinvestment() {
        return isinDivReinvestment;
    }

    public void setIsinDivReinvestment(String isinDivReinvestment) {
        this.isinDivReinvestment = isinDivReinvestment;
    }

    
}