package etsii.tfg.sttfbug.predictor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

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
        /*
         * Planteamiento:
         * 
         * Tenemos que tener en cuenta 2 partes, la función y la evaluación de la función.
         * 
         * Para la función deberíamos seguir los siguientes puntos:
         * Esta parte a su vez se subdivide en 2 partes, primero debemos popular el training set y a continuación,
         * comenzamos la gestión de la nueva issue leemos la issue del fichero(conseguimos sus datos #PorDeterminarMetodo) 
         * y filtramos la lista de palabras comunes en ingles.
         * Después, convertimos la issue filtrada en una query(definición de la consulta) y a continuación 
         * aplicamos la comparación entre la query y el training set que tenemos y devolvemos las 3 issues (ID + Tiempo + Semejanza/similitud)
         * 
         * Para la evaluación de la función debemos tener en cuenta lo siguiente:
         * Primero, debemos leer el fichero de issues y filtrar las palabras comunes en ingles.(Igual)
         * Después convertimos la issue en una query y la comparamos con el training set, pero en este caso,
         * al finalizar tenemos que hacer el cálculo de la precisión de la predicción, es decir el AAR (Average absolute residual)
         * la diferencia entre el tiempo predicho y el tiempo real.
         * 
         * En la evaluación hay que tener en cuenta que el training set va creciendo a medida que se van añadiendo las issues y 
         * hay que seguir las indicaciones del paper para completar la evaluación.
         */
        public static void populateTrainingSet(Properties properties){
            String[] stopWords = properties.getProperty("analyzer.stopwords").split(",");
            CharArraySet sWSet = new CharArraySet(Arrays.asList(stopWords), true);
            try(StandardAnalyzer analyzer = new StandardAnalyzer(sWSet)) {
                try (Directory dir = FSDirectory.open(Paths.get(properties.getProperty("lucene.directorypath")))) {
                    IndexWriterConfig config = new IndexWriterConfig(analyzer);
                    config.setSimilarity(new ClassicSimilarity());//TF-IDF implementation for similarity
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
            try(BufferedReader br = new BufferedReader(new FileReader(filePath))){
                String line;
                Long i=0L;
                Long lines = Files.lines(new File(filePath).toPath()).count()-1;
                while ((line = br.readLine()) != null) {
                    i++;
                    if(!line.contains("\"ID\",\"Start Date\",\"End Date\",\"Title\",\"Description\"") && i<lines){
                        List<String> issue = List.of(line.split("\",\""));
                        StringBuilder description = new StringBuilder(issue.get(4));
                        description.deleteCharAt(description.length()-1);
                        Document doc = new Document();
                        doc.add(new StringField("id", issue.get(0).replace("\"", "") ,Field.Store.YES));
                        doc.add(new TextField("title", issue.get(3), Field.Store.YES));
                        doc.add(new TextField("description", description.toString(), Field.Store.YES));
                        writer.addDocument(doc);
                    }
                }
                System.out.println("Training set populated");
            }catch(IOException e){
                e.printStackTrace();
                System.err.println("Could not find \"FILTERED_ISSUES.csv\" file ");
            }
        }

        public static void predictTTFIssues(Properties properties, Directory dir, StandardAnalyzer analyzer){
            List<List<String>> result = new ArrayList<>();
            List<String> issuesID = List.of(properties.getProperty("predict.issue.list").split(","));
            String issueUrl = properties.getProperty("url.issue");
            for(String id: issuesID){
                String link = issueUrl+id;
                org.jsoup.nodes.Document doc = WebScraper.tryConnection(link);
                Issue issue = IssueFilter.getIssue(doc, id, IssueType.PREDICT);
                result.add(predictTimeToFix(issue, properties,dir,analyzer));
            }
            Integer k = Integer.valueOf(properties.getProperty("issues.neighbor"));
            for(int i=0;i<k;i++){
                System.out.println(String.format("Showing the %d closests neighbors for issue %s", k, issuesID.get(i)));
                for(String s: result.get(i)){
                    System.out.println(s);
                }
            }
        }

        private static List<String> predictTimeToFix(Issue issue, Properties properties, Directory dir, StandardAnalyzer analyzer) {            
            List<String> results = new ArrayList<>();
            try {
                IndexReader reader = DirectoryReader.open(dir);
                IndexSearcher searcher = new IndexSearcher(reader);
                searcher.setSimilarity(new ClassicSimilarity());
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                config.setSimilarity(new ClassicSimilarity());
                String[] fields = {"title", "description"};
                String[] queries =  {escapeSpecialCharacters(issue.getTitle()), escapeSpecialCharacters(issue.getDescription())};
                
                MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer);
                try {
                    Query query = parser.parse(queries,fields,analyzer);
                    TopDocs topHits;
                    topHits = searcher.search(query, 3);
                    ScoreDoc[] hits = topHits.scoreDocs;
                    System.out.println("Hits for issue "+issue.getId()+": "+ hits.length);
                    for(int i=0; i<hits.length; i++){
                        System.out.println(hits[i]);
                        Document doc = searcher.storedFields().document(i);
                        String score = "Issue ID: " + doc.get("id") + " Score: " + hits[i].score;
                        results.add(score);
                    }
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                
            } catch (IOException e) {
                System.err.println("Error opening Lucene directory: " + e.getMessage());
            }
            
            return results;
        }
        private static String escapeSpecialCharacters(String query) {
            query = query.replaceAll("([\\[\\](){}+\\-'\"/])", "\\\\$1");
            return query;
        }
    
}