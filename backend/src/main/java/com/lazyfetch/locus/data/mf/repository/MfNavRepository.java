package com.lazyfetch.locus.data.mf.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.lazyfetch.locus.data.mf.entity.MfNav;

public interface MfNavRepository extends JpaRepository<MfNav, Long> {

    Optional<MfNav> findBySchemeCodeAndNavDate(Integer schemeCode, LocalDate navDate);

    @Query("SELECT n.nav FROM MfNav n WHERE n.schemeCode = :code AND n.navDate <= :date ORDER BY n.navDate DESC")

    List<BigDecimal> findNavsOnOrBeforeDate(@Param("code") Integer code, @Param("date") LocalDate date, Pageable pageable);

}