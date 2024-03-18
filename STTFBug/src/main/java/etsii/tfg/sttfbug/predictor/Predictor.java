package etsii.tfg.sttfbug.predictor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;

import etsii.tfg.sttfbug.issues.Issue;
import etsii.tfg.sttfbug.issues.IssueFilter;
import etsii.tfg.sttfbug.issues.IssueType;
import etsii.tfg.sttfbug.issues.WebScraper;

public class Predictor {
        public static void populateTrainingSet(Properties properties){
            String[] stopWords = properties.getProperty("analyzer.stopwords").split(",");
            CharArraySet sWSet = new CharArraySet(Arrays.asList(stopWords), true);
            try(StandardAnalyzer analyzer = new StandardAnalyzer(sWSet)) {
                String luceneDirPath = properties.getProperty("lucene.directorypath");//LUCENE DIRECTORY PATH
                File luceneDir = new File(luceneDirPath);
                if(luceneDir.exists() && luceneDir.isDirectory()&& luceneDir.list().length>0){
                    try{FileUtils.forceDelete(luceneDir);
                        if(!luceneDir.exists()){
                            System.out.println("Old Lucene directory deleted");
                        }
                    }
                    catch(IOException e){e.printStackTrace();}
                }
                try (Directory dir = FSDirectory.open(Paths.get(luceneDirPath))) {
                    IndexWriterConfig config = new IndexWriterConfig(analyzer);
                    config.setSimilarity(new ClassicSimilarity());
                    try {
                        IndexWriter writer = new IndexWriter(dir, config);
                        writeIssues(writer, properties.getProperty("filteredissue.path"));
                        writer.close();
                        predictTTFIssues(properties, dir, analyzer);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    System.err.println("Error opening Lucene directory: " + e.getMessage());
                }
            }

        }

        private static void writeIssues(IndexWriter writer, String filePath) {
            try(BufferedReader br = Files.newBufferedReader(new File(filePath).toPath(), StandardCharsets.UTF_8)){
                String line;
                Long i=0L;
                System.out.println(filePath);
                try (Stream<String> lines = Files.lines(Paths.get(filePath))) {
                    Long nLines = lines.count() - 1;
                    while ((line = br.readLine()) != null) {
                        i++;
                        if(!line.contains("\"ID\",\"Start Date\",\"End Date\",\"Title\",\"Description\"") && i<nLines){
                            List<String> issue = List.of(line.split("\",\""));
                            StringBuilder description = new StringBuilder(issue.get(4));
                            description.deleteCharAt(description.length()-1);
                            Document doc = new Document();
                            doc.add(new StringField("id", issue.get(0).replace("\"", "") ,Field.Store.YES));
                            doc.add(new TextField("title", escapeSpecialCharacters(issue.get(3)), Field.Store.YES));
                            doc.add(new TextField("description", escapeSpecialCharacters(description.toString()), Field.Store.YES));
                            writer.addDocument(doc);
                        }
                    }
                    
                    System.out.println("Number of issues processed: "+(i-1));
                    System.out.println("Training set populated");
                }catch(IOException e){
                    System.err.println("Could not read "+filePath +" file ");
                    e.printStackTrace();
                }
            } catch (IOException e) {
                e.printStackTrace();
                System.err.println("Could not find \"FILTERED_ISSUES.csv\" file ");
            }
        }

        public static void predictTTFIssues(Properties properties, Directory dir, StandardAnalyzer analyzer ){
            List<List<String>> result = new ArrayList<>();
            List<String> issuesID = List.of(properties.getProperty("predict.issue.list").split(","));
            String issueUrl = properties.getProperty("url.issue");
            for(String id: issuesID){
                String link = issueUrl+id;
                org.jsoup.nodes.Document doc = WebScraper.tryConnection(link);
                Issue issue = IssueFilter.getIssue(doc, id, IssueType.PREDICT);
                result.add(predictTimeToFix(issue,dir,analyzer,properties));
            }
            Integer k = Integer.valueOf(properties.getProperty("issues.neighbor"));
            for(int i=0;i<issuesID.size();i++){
                System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                System.out.println(String.format("Showing the %d closests neighbors for issue %s", k, issuesID.get(i)));
                for(String s: result.get(i)){
                    System.out.println(s);
                }
            }
        }

        private static List<String> predictTimeToFix(Issue issue, Directory dir, StandardAnalyzer analyzer, Properties properties) {            
            List<String> results = new ArrayList<>();
            try {
                IndexReader reader = DirectoryReader.open(dir);
                IndexSearcher searcher = new IndexSearcher(reader);
                IndexSearcher.setMaxClauseCount(Integer.valueOf(properties.getProperty("max.clause.count")));
                searcher.setSimilarity(new ClassicSimilarity());
                String[] fields = {"title", "description"};
                String[] queries =  {escapeSpecialCharacters(issue.getTitle()), escapeSpecialCharacters(issue.getDescription())};
                Map<String, Float> boosts = Map.of("title", 1.0f, "description", 1.0f);
                MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer,boosts);
                try {
                    Query query = parser.parse(queries,fields,analyzer);
                    TopDocs topHits;
                    topHits = searcher.search(query, 3);
                    ScoreDoc[] hits = topHits.scoreDocs;
                    System.out.println("Hits for issue "+issue.getId()+": "+ hits.length); 
                    for(int i=0; i<hits.length; i++){
                        Document doc = searcher.storedFields().document(hits[i].doc);
                        String score = "Issue ID: " + doc.get("id") + " Score: " + hits[i].score;
                        results.add(score);
                    }
                    reader.close();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                
            } catch (IOException e) {
                System.err.println("Error opening Lucene directory: " + e.getMessage());
            }
            
            return results;
        }
        
        private static String escapeSpecialCharacters(String query) {
            query = query.replaceAll("([\\[\\](){}+\\-'\"/<>:;])", "\\\\$1"); // escape special characters
            return query;
        }
    
}