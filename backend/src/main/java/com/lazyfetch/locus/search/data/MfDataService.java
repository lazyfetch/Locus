package com.lazyfetch.locus.search.data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class MfDataService 
{
    private final JdbcTemplate jdbc;

    public MfDataService(DataSource dataSource)
    {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    
    public List<Map<String,Object>> searchFundByName(String namePattern)
    {
        String sql = """
                SELECT scheme_code, scheme_name, fund_house,scheme_category
                FROM mf_scheme
                WHERE LOWER(scheme_name) LIKE LOWER(?)
                LIMIT 10
                """;

        return jdbc.queryForList(sql,"%" + namePattern + "%");
    }

    public List<Map<String,Object>>  getReturns(List<Integer> schemeCodes)
    {
        if (schemeCodes == null || schemeCodes.isEmpty()) {
            return List.of();
        }

        String placeholders = schemeCodes.stream()
            .map(c -> "?")
            .collect(Collectors.joining(", "));

        String sql = String.format("""
                SELECT s.scheme_code, s.scheme_name, r.period, r.fund_return_pct
                FROM mf_returns r
                JOIN mf_scheme s ON r.scheme_code = s.scheme_code
                WHERE r.scheme_code IN (%s)
                ORDER BY s.scheme_name, r.period
                """, placeholders);

        return jdbc.queryForList(sql, schemeCodes.toArray());
    }


    public List<Map<String,Object>> getTopHoldings (Integer schemeCode, int limit)
    {
        String sql;
        sql = """
                  SELECT stock_name, percentage
                  FROM mf_holdings
                  WHERE scheme_code = ?
                  ORDER BY percentage DESC
                  LIMIT ?
              """;

        return jdbc.queryForList(sql,schemeCode,limit);
    }

    public Map<String,Object> getFundDetails(Integer schemeCode)
    {
        String sql = """
                SELECT scheme_code, scheme_name, fund_house, 
                       scheme_type, scheme_category
                FROM mf_scheme
                WHERE scheme_code=?
                """;

        List<Map<String,Object>> results = jdbc.queryForList(sql,schemeCode);

        return results.isEmpty() ? null : results.get(0);
    }

    public Map<String, Object> getLatestNav(Integer schemeCode) {
        String sql = """
                SELECT nav, nav_date
                FROM mf_nav_history
                WHERE scheme_code = ?
                ORDER BY nav_date DESC
                LIMIT 1
                """;
        List<Map<String, Object>> results = jdbc.queryForList(sql, schemeCode);
        return results.isEmpty() ? null : results.get(0);
    }

    public List<Map<String, Object>> getNavHistory(Integer schemeCode, LocalDate startDate, LocalDate endDate) {
        String sql = """
                SELECT nav_date, nav
                FROM mf_nav_history
                WHERE scheme_code = ? AND nav_date BETWEEN ? AND ?
                ORDER BY nav_date
                """;
        return jdbc.queryForList(sql, schemeCode, startDate, endDate);
    }

    public List<Map<String, Object>> searchFundsByHouse(String fundHouse) {
        String sql = 
                """
                    SELECT scheme_code, scheme_name, scheme_category
                    FROM mf_scheme
                    WHERE LOWER(fund_house) LIKE LOWER(?)
                    LIMIT 20
                """;
        return jdbc.queryForList(sql, "%" + fundHouse + "%");
    }

    public List<Map<String, Object>> getAllFunds() 
    {
        String sql = """
                SELECT scheme_code, scheme_name
                FROM mf_scheme
                """;
        return jdbc.queryForList(sql);
    }
}
