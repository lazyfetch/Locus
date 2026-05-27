package com.lazyfetch.locus;

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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.springframework.stereotype.Service;

import com.lazyfetch.locus.Filters.CustomAnalyzer;

@Service
public class SearchEngineService {

    private final Directory indexDirectory;
    private final Analyzer analyzer;
    private final EmbeddingService embeddingService;
    
    public Directory getIndexDirectory() { return indexDirectory; }


    public SearchEngineService(EmbeddingService embeddingService) throws IOException 
    {
        // Store index on disk in a folder called "index"
        this.indexDirectory = FSDirectory.open(Paths.get("index"));
        this.analyzer = new CustomAnalyzer();
        this.embeddingService = embeddingService;
    }

    // Add a document to the index
    public void indexDocument(String title, String body) throws IOException 
    {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) 
        {
            Document doc = new Document();
            doc.add(new TextField("title", title, Field.Store.YES));
            doc.add(new TextField("body", body, Field.Store.YES));

            // Compute embedding
            float[] vector;
            try 
            {
                vector = embeddingService.embed(title + " " + body);
            } 
            catch (Exception e) 
            {
                throw new IOException("Embedding failed", e);
            }

            doc.add(new KnnFloatVectorField("embedding", vector, VectorSimilarityFunction.COSINE));

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


    // Update an existing document by id 
    public void updateDocument(String id, String title, String body) throws IOException 
    {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) 
        {
            Document doc = new Document();
            doc.add(new StringField("id", id, Field.Store.YES));   
            doc.add(new TextField("title", title, Field.Store.YES));
            doc.add(new TextField("body", body, Field.Store.YES));
            writer.updateDocument(new Term("id", id), doc);
            writer.commit();
        }
    }

    // Delete a document by id
    public void deleteDocument(String id) throws IOException 
    {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        try (IndexWriter writer = new IndexWriter(indexDirectory, config)) 
        {
            writer.deleteDocuments(new Term("id", id));
            writer.commit();
        }
    }
}