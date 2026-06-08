package com.lazyfetch.locus.data.mf.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.lazyfetch.locus.data.mf.entity.MfHolding;

public interface MfHoldingRepository extends JpaRepository<MfHolding, Long> {

    void deleteBySchemeCodeAndHoldingDate(Integer schemeCode, LocalDate holdingDate);

    List<MfHolding> findBySchemeCodeAndHoldingDate(Integer schemeCode, LocalDate holdingDate);
}