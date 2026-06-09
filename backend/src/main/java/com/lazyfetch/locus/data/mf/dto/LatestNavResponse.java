package com.lazyfetch.locus.data.mf.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class LatestNavResponse {

    @JsonProperty("meta")
    private Meta meta;

    @JsonProperty("data")
    private List<DataItem> data;

    @JsonProperty("status")
    private String status;

    public Meta getMeta() {
        return meta;
    }

    public void setMeta(Meta meta) {
        this.meta = meta;
    }

    public List<DataItem> getData() {
        return data;
    }

    public void setData(List<DataItem> data) {
        this.data = data;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    
    public static class Meta {
        @JsonProperty("fund_house")
        private String fundHouse;

        @JsonProperty("scheme_type")
        private String schemeType;

        @JsonProperty("scheme_category")
        private String schemeCategory;

        @JsonProperty("scheme_code")
        private Integer schemeCode;

        @JsonProperty("scheme_name")
        private String schemeName;

        @JsonProperty("isin_growth")
        private String isinGrowth;

        @JsonProperty("isin_div_reinvestment")
        private String isinDivReinvestment;

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

    public static class DataItem {
        @JsonProperty("date")
        private String date;       // dd-MM-yyyy

        @JsonProperty("nav")
        private String nav;        

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public String getNav() {
            return nav;
        }

        public void setNav(String nav) {
            this.nav = nav;
        }

        
    }
}