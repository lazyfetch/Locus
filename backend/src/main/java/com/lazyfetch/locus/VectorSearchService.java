package com.lazyfetch.locus;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.*;
// import org.apache.lucene.store.Directory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VectorSearchService {

    private final SearchEngineService searchEngineService;
    private final EmbeddingService embeddingService;

    public VectorSearchService(SearchEngineService searchEngineService, EmbeddingService embeddingService) {
        this.searchEngineService = searchEngineService;
        this.embeddingService = embeddingService;
    }

    public List<Map<String, String>> vectorSearch(String queryText, int maxHits) throws Exception {
        float[] queryVector = embeddingService.embed(queryText);

        try (DirectoryReader reader =
                 DirectoryReader.open(searchEngineService.getIndexDirectory())) {
            IndexSearcher searcher = new IndexSearcher(reader);

            // Build KNN query 
            KnnFloatVectorQuery knnQuery = new KnnFloatVectorQuery("embedding", queryVector, maxHits);

            // Execute
            TopDocs topDocs = searcher.search(knnQuery, maxHits);

            List<Map<String, String>> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                Map<String, String> result = new HashMap<>();
                result.put("title", doc.get("title"));
                result.put("body", doc.get("body"));
                result.put("score", String.valueOf(scoreDoc.score));
                results.add(result);
            }
            return results;
        }
    }
}