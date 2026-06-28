package com.lazyfetch.locus.search.engine;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import com.lazyfetch.locus.Filters.CustomAnalyzer;
// import com.lazyfetch.locus.data.company.TickerTagger;
import com.lazyfetch.locus.search.embedding.EmbeddingService;

@Service
public class SearchEngineService {

    private final Directory indexDirectory;
    private final Analyzer analyzer;
    private final EmbeddingService embeddingService;
    // private final TickerTagger tickerTagger;

    public Directory getIndexDirectory() { return indexDirectory; }

    public SearchEngineService(EmbeddingService embeddingService) throws IOException {
        this.indexDirectory = FSDirectory.open(Paths.get("index"));
        this.analyzer = new CustomAnalyzer();
        this.embeddingService = embeddingService;
        // this.tickerTagger = tickerTagger;
    }

    public void indexDocument(String title, String body, List<String> tickers) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
            Document doc = new Document();
            doc.add(new TextField("title", title, Field.Store.YES));
            doc.add(new TextField("body", body, Field.Store.YES));

            List<String> resolvedTickers = tickers;
            // if (resolvedTickers == null || resolvedTickers.isEmpty()) {
            //     resolvedTickers = tickerTagger.extractTickers(title + " " + body);
            // }
            if (resolvedTickers != null) {
                for (String t : resolvedTickers) {
                    if (t != null && !t.isBlank()) {
                        doc.add(new StringField("ticker", t.toUpperCase(), Field.Store.YES));
                    }
                }
            }

            float[] vector;
            try {
                vector = embeddingService.embed(title + " " + body);
            } catch (Exception e) {
                throw new IOException("Embedding failed", e);
            }

            doc.add(new KnnFloatVectorField("embedding", vector, VectorSimilarityFunction.COSINE));
            writer.addDocument(doc);
            writer.commit();
        }
    }

    public List<Map<String, String>> search(String queryText, int maxHits) throws Exception {
        return search(queryText, maxHits, (List<String>) null);
    }

    public List<Map<String, String>> search(String queryText, int maxHits, String ticker) throws Exception {
        if (ticker == null || ticker.isBlank()) {
            return search(queryText, maxHits, (List<String>) null);
        }
        return search(queryText, maxHits, List.of(ticker));
    }

    public List<Map<String, String>> search(String queryText, int maxHits, List<String> tickers) throws Exception {
        List<Map<String, String>> results = new ArrayList<>();

        try (DirectoryReader reader = DirectoryReader.open(indexDirectory)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            SimpleQueryParser parser = new SimpleQueryParser(analyzer, "body");
            Query textQuery = parser.parse(queryText);

            Query tickerFilter = buildTickerFilter(tickers);
            TopDocs topDocs;

            if (tickerFilter != null) {
                BooleanQuery.Builder filtered = new BooleanQuery.Builder();
                filtered.add(textQuery, BooleanClause.Occur.MUST);
                filtered.add(tickerFilter, BooleanClause.Occur.FILTER);

                topDocs = searcher.search(filtered.build(), maxHits);

                if (topDocs.scoreDocs.length == 0) {
                    topDocs = searcher.search(textQuery, maxHits);
                }
            } else {
                topDocs = searcher.search(textQuery, maxHits);
            }

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
        }
        return results;
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

    public void updateDocument(String id, String title, String body) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
            Document doc = new Document();
            doc.add(new StringField("id", id, Field.Store.YES));
            doc.add(new TextField("title", title, Field.Store.YES));
            doc.add(new TextField("body", body, Field.Store.YES));
            writer.updateDocument(new Term("id", id), doc);
            writer.commit();
        }
    }

    public void deleteDocument(String id) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
            writer.deleteDocuments(new Term("id", id));
            writer.commit();
        }
    }
}