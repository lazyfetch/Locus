package com.lazyfetch.locus.data.mf.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "mf_holdings")
public class MfHolding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scheme_code", nullable = false)
    private Integer schemeCode;

    @Column(name = "holding_date", nullable = false)
    private LocalDate holdingDate;

    @Column(name = "stock_name", nullable = false, length = 200)
    private String stockName;

    @Column(name = "percentage", precision = 5, scale = 2)
    private BigDecimal percentage;

    // Default constructor
    public MfHolding() {}

    // All-args constructor
    public MfHolding(Integer schemeCode, LocalDate holdingDate, String stockName, BigDecimal percentage) {
        this.schemeCode = schemeCode;
        this.holdingDate = holdingDate;
        this.stockName = stockName;
        this.percentage = percentage;
    }

    // Getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getSchemeCode() { return schemeCode; }
    public void setSchemeCode(Integer schemeCode) { this.schemeCode = schemeCode; }
    public LocalDate getHoldingDate() { return holdingDate; }
    public void setHoldingDate(LocalDate holdingDate) { this.holdingDate = holdingDate; }
    public String getStockName() { return stockName; }
    public void setStockName(String stockName) { this.stockName = stockName; }
    public BigDecimal getPercentage() { return percentage; }
    public void setPercentage(BigDecimal percentage) { this.percentage = percentage; }
}