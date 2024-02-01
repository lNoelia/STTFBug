package etsii.tfg.sttfbug.predictor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.document.Field;

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
        public static void populateTrainingSet(){
            
            try (InputStream input = new FileInputStream("config.properties")) {

            Properties properties = new Properties();
            properties.load(input);
            System.out.println("C:\\Universidad\\TFG\\STTFBug\\STTFBug\\resources\\FILTERED_ISSUES.csv");
            System.out.println(properties.getProperty("filteredissue.path"));
            Set<String> stopWords = Set.of(properties.getProperty("analyzer.stopwords").split(","));
            CharArraySet sWSet = new CharArraySet(stopWords, true);
            try(StandardAnalyzer analyzer = new StandardAnalyzer(sWSet)) {
                try (Directory dir = FSDirectory.open(Paths.get(properties.getProperty("lucene.directorypath")))) {
                    IndexWriterConfig config = new IndexWriterConfig(analyzer);
                    IndexWriter writer = new IndexWriter(dir, config);
                    writeIssues(writer, properties.getProperty("filteredissue.path"));
                }
            }

        } catch (IOException ex) {
            ex.printStackTrace();
            System.out.println("config.properties file not found");
        }
                
        }

        private static void writeIssues(IndexWriter writer, String filePath) {
            System.out.println("HERE");
            try{
                List<String> lines = Files.readAllLines(new File(filePath).toPath());
                lines.remove(0);
                for(String line : lines){
                    List<String> issue = List.of(line.replace("\"", "").split(","));
                    Document doc = new Document();
                    doc.add(new StringField("id", issue.get(0) ,Field.Store.YES));
                    doc.add(new TextField("title", issue.get(3), Field.Store.YES));
                    doc.add(new TextField("description", issue.get(4), Field.Store.YES));
                    writer.addDocument(doc);
                }
                System.out.println("Training set populated");
            }catch(IOException e){
                System.out.println("Could not find \"FILTERED_ISSUES.csv\" file ");
            }
    }
}