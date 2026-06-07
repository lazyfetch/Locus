package com.lazyfetch.locus.search.vector;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.springframework.stereotype.Service;

import com.lazyfetch.locus.search.embedding.EmbeddingService;
import com.lazyfetch.locus.search.engine.SearchEngineService;

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
        return vectorSearch(queryText, maxHits, (List<String>) null);
    }

    public List<Map<String, String>> vectorSearch(String queryText, int maxHits, String ticker) throws Exception {
        if (ticker == null || ticker.isBlank()) {
            return vectorSearch(queryText, maxHits, (List<String>) null);
        }
        return vectorSearch(queryText, maxHits, List.of(ticker));
    }

    public List<Map<String, String>> vectorSearch(String queryText, int maxHits, List<String> tickers) throws Exception {
        float[] queryVector = embeddingService.embed(queryText);

        try (DirectoryReader reader = DirectoryReader.open(searchEngineService.getIndexDirectory())) {
            IndexSearcher searcher = new IndexSearcher(reader);

            Query filter = buildTickerFilter(tickers);
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

    private Query buildTickerFilter(List<String> tickers) {
        if (tickers == null || tickers.isEmpty()) {
            return null;
        }
        BooleanQuery.Builder b = new BooleanQuery.Builder();
        for (String t : tickers) {
            if (t != null && !t.isBlank()) {
                b.add(new TermQuery(new Term("ticker", t.toUpperCase())), BooleanClause.Occur.SHOULD);
            }
        }
        b.setMinimumNumberShouldMatch(1);
        return b.build();
    }
}