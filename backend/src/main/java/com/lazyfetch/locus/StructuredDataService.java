package com.lazyfetch.locus;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class StructuredDataService {
    private final JdbcTemplate jdbc;

    public StructuredDataService(DataSource dataSource) {
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public List<Map<String, Object>> queryMetrics(
            String ticker,
            List<String> metrics,
            LocalDate startDate,
            LocalDate endDate) {

        StringBuilder sql = new StringBuilder(
            "SELECT ticker, metric, metric_value, date FROM fundamental WHERE ticker = ?"
        );
        List<Object> params = new ArrayList<>();
        params.add(ticker);

        if (metrics != null && !metrics.isEmpty()) {
            sql.append(" AND LOWER(metric) IN (");
            for (int i = 0; i < metrics.size(); i++) {
                if (i > 0) {
                    sql.append(", ");
                }
                sql.append("?");
                params.add(metrics.get(i).toLowerCase());
            }
            sql.append(")");
        }

        if (startDate != null) {
            sql.append(" AND date >= ?");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND date <= ?");
            params.add(endDate);
        }

        sql.append(" ORDER BY date DESC");
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    public List<Map<String, Object>> searchCompanies(String namePattern) {
        return jdbc.queryForList(
            "SELECT * FROM company WHERE name LIKE ?",
            "%" + namePattern + "%"
        );
    }

    public List<Map<String, Object>> listCompanies() {
        return jdbc.queryForList("SELECT ticker, name FROM company");
    }
}