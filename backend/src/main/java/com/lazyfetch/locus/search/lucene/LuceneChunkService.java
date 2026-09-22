package com.lazyfetch.locus.search.lucene;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Service
public class LuceneChunkService {

    private final JdbcTemplate jdbc;
    private Directory directory;
    private Analyzer analyzer;

    public LuceneChunkService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostConstruct
    public void buildIndex() throws Exception {
        this.directory = new ByteBuffersDirectory();   
        this.analyzer = new ChunkAnalyzer();           

        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(directory, config)) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, scheme_code, section_type, chunk_text FROM mf_chunks");

            for (Map<String, Object> row : rows) {
                Document doc = new Document();
                doc.add(new StringField("id", String.valueOf(row.get("id")), Field.Store.YES));
                doc.add(new IntPoint("scheme_code", (Integer) row.get("scheme_code")));
                doc.add(new StoredField("scheme_code", (Integer) row.get("scheme_code")));
                doc.add(new StringField("section_type", 
                        String.valueOf(row.get("section_type")), Field.Store.YES));
                doc.add(new TextField("chunk_text", 
                        String.valueOf(row.get("chunk_text")), Field.Store.YES));
                writer.addDocument(doc);
            }
        }
    }

    public List<Map<String, Object>> search(String query, int topK, List<Integer> schemeCodes) 
            throws Exception {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            IndexSearcher searcher = new IndexSearcher(reader);

            QueryParser parser = new QueryParser("chunk_text", analyzer);
            Query textQuery = parser.parse(QueryParser.escape(query));

            Query finalQuery = textQuery;
            if (schemeCodes != null && !schemeCodes.isEmpty()) {
                BooleanQuery.Builder b = new BooleanQuery.Builder();
                b.add(textQuery, BooleanClause.Occur.MUST);
                b.add(buildSchemeFilter(schemeCodes), BooleanClause.Occur.FILTER);
                finalQuery = b.build();
            }

            TopDocs topDocs = searcher.search(finalQuery, topK);

            List<Map<String, Object>> results = new ArrayList<>();
            for (ScoreDoc sd : topDocs.scoreDocs) {
                Document doc = searcher.storedFields().document(sd.doc);
                Map<String, Object> m = new HashMap<>();
                m.put("id", Integer.parseInt(doc.get("id")));
                m.put("scheme_code", Integer.parseInt(doc.get("scheme_code")));
                m.put("section_type", doc.get("section_type"));
                m.put("chunk_text", doc.get("chunk_text"));
                m.put("bm25_score", sd.score);
                results.add(m);
            }
            return results;
        }
    }

    private Query buildSchemeFilter(List<Integer> codes) {
        BooleanQuery.Builder b = new BooleanQuery.Builder();
        for (Integer c : codes) {
            b.add(IntPoint.newExactQuery("scheme_code", c), BooleanClause.Occur.SHOULD);
        }
        return b.build();
    }
}
