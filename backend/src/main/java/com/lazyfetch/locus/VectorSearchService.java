package com.lazyfetch.locus;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class VectorSearchService {

    private final SearchEngineService searchEngineService;
    private final EmbeddingService embeddingService;

    public VectorSearchService(SearchEngineService searchEngineService, EmbeddingService embeddingService) {
        this.searchEngineService = searchEngineService;
        this.embeddingService = embeddingService;
    }

    public List<Map<String, String>> vectorSearch(String queryText, int maxHits) throws Exception {
        return vectorSearch(queryText, maxHits, null);
    }

    public List<Map<String, String>> vectorSearch(String queryText, int maxHits, String ticker) throws Exception {
        float[] queryVector = embeddingService.embed(queryText);

        try (DirectoryReader reader = DirectoryReader.open(searchEngineService.getIndexDirectory())) {
            IndexSearcher searcher = new IndexSearcher(reader);

            Query filter = null;
            if (ticker != null && !ticker.isBlank()) {
                filter = new TermQuery(new Term("ticker", ticker.toUpperCase()));
            }

            KnnFloatVectorQuery knnQuery = new KnnFloatVectorQuery("embedding", queryVector, maxHits, filter);
            TopDocs topDocs = searcher.search(knnQuery, maxHits);

            if (topDocs.scoreDocs.length == 0 && filter != null) {
                knnQuery = new KnnFloatVectorQuery("embedding", queryVector, maxHits);
                topDocs = searcher.search(knnQuery, maxHits);
            }

            List<Map<String, String>> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                Map<String, String> result = new HashMap<>();
                result.put("title", doc.get("title"));
                result.put("body", doc.get("body"));
                result.put("score", String.valueOf(scoreDoc.score));
                String docTicker = doc.get("ticker");
                if (docTicker != null) {
                    result.put("ticker", docTicker);
                }
                results.add(result);
            }
            return results;
        }
    }
}