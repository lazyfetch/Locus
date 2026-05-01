package com.lazyfetch.locus;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.store.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

import com.lazyfetch.locus.Filters.CustomAnalyzer;

@Service
public class SearchEngineService {

    private final Directory indexDirectory;
    private final Analyzer analyzer;

    public SearchEngineService() throws IOException {
        // Store index on disk in a folder called "index"
        this.indexDirectory = FSDirectory.open(Paths.get("index"));
        this.analyzer = new CustomAnalyzer();
    }

    // Add a document to the index
    public void indexDocument(String title, String body) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) {
            Document doc = new Document();
            doc.add(new TextField("title", title, Field.Store.YES));  // stored + indexed
            doc.add(new TextField("body", body, Field.Store.YES));    // stored + indexed
            writer.addDocument(doc);
            writer.commit();
        }
    }

    // Search the index
    public List<Map<String, String>> search(String queryText, int maxHits) throws Exception {
        List<Map<String, String>> results = new ArrayList<>();

        try (DirectoryReader reader = DirectoryReader.open(indexDirectory)) 
        {
            IndexSearcher searcher = new IndexSearcher(reader);
            SimpleQueryParser parser = new SimpleQueryParser(analyzer,"body");
            Query query = parser.parse(queryText);

            TopDocs topDocs = searcher.search(query, maxHits);

            for (ScoreDoc scoreDoc : topDocs.scoreDocs) 
            {
                Document doc = searcher.doc(scoreDoc.doc);
                Map<String, String> result = new HashMap<>();
                result.put("title", doc.get("title"));
                result.put("body", doc.get("body"));
                result.put("score", String.valueOf(scoreDoc.score));
                results.add(result);
            }
        }
        return results;
    }
}