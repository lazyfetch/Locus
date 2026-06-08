package com.lazyfetch.locus.data.mf.repository;


import java.util.List;
import com.lazyfetch.locus.data.mf.entity.MfScheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;




public interface MfSchemeRepository extends JpaRepository<MfScheme, Integer> 
{

    @Query("SELECT m.schemeCode FROM MfScheme m")
    List<Integer> findAllSchemeCodes();

    // Optional find by fund house, category, etc later
}