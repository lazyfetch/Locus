package com.lazyfetch.locus.search.pgvector;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.lazyfetch.locus.records.VectorSearchResult;
import com.lazyfetch.locus.search.embedding.EmbeddingService;


@Service
public class PgVectorService 
{
    private final EmbeddingService embeddingService;
    private final JdbcTemplate jdbc;

    public PgVectorService(DataSource dataSource, EmbeddingService embeddingService)
    {
        this.embeddingService=embeddingService;
        this.jdbc = new JdbcTemplate(dataSource);
    }

    public List<VectorSearchResult> search(String queryText, int topK, List<Integer> schemeCodes) throws Exception
    {
        // converting the query to vector
        float[] queryVector = embeddingService.embed(queryText);

        // converting it into a sql string for searching in db
        String vectorStr = arraysToPgVector(queryVector);

        String sql;
        Object[] params;

        if(schemeCodes != null && !schemeCodes.isEmpty())
        {
            sql= """ 
                SELECT id, scheme_code,section_type,chunk_text,embedding <=> ? :: vector AS distance
                FROM mf_chunks
                WHERE scheme_code = ANY(?)
                ORDER BY distance
                LIMIT ?
            """;

            params =  new Object[] {vectorStr,schemeCodes.toArray(new Integer[0]),topK};
        }

        else
        {
            sql = """
                SELECT id, scheme_code, section_type, chunk_text,
                       embedding <=> ?::vector AS distance
                FROM mf_chunks
                ORDER BY distance
                LIMIT ?
                """;

            params = new Object[]{vectorStr, topK};
        }

        return jdbc.query(sql,params,(rs,rowNum) -> new VectorSearchResult(
            rs.getInt("id"),
            rs.getInt("scheme_code"),
            rs.getString("section_type"),
            rs.getString("chunk_text"),
            rs.getDouble("distance")
        ));
    }

    private String arraysToPgVector(float[] vec) 
    {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
