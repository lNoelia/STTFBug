package etsii.tfg.sttfbug.predictor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import etsii.tfg.sttfbug.issues.Issue;
import etsii.tfg.sttfbug.issues.IssueFilter;
import etsii.tfg.sttfbug.issues.IssueType;
import etsii.tfg.sttfbug.issues.WebScraper;
import weka.classifiers.evaluation.Evaluation;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.CSVLoader;

public class Evaluator {

    public static void evaluatePredictor(Properties properties){
        try {
            String issueFile = getCleanFilePath(properties); // Prepares data to read it (removes first line)
            // Load the data from the CSV file
            //String issueFile = properties.getProperty("filteredissue.path");
            CSVLoader loader = new CSVLoader();
            loader.setSource(new File(issueFile));
            Instances dataset = loader.getDataSet();
            if (dataset.classIndex() == -1){ // Setting Weka's class index, but won't be used since we are not using Weka's classifiers
                dataset.setClassIndex(0);
            }
            //Creating the evaluation object
            Evaluation eval = new Evaluation(dataset);
            int numFolds = 3; // Static, should be a list of values (e.g 3,5,7)

            // EVALUATION OF EACH FOLD
            for (int fold = 0; fold < numFolds; fold++) { // Each fold, we will iterate and consider one of the folds 
                Instances trainingSet = dataset.trainCV(numFolds, fold); //Creates the training set for the current fold. 
                Instances testSet = dataset.testCV(numFolds, fold); 
                System.out.println("Number of instances for the training set: " + trainingSet.numInstances());
                System.out.println("Number of instances for the testing set: " + testSet.numInstances());
                populateTrainingSet(properties, trainingSet, testSet, fold); 

                // CALCULATE EVALUATION METRICS FOR EACH FOLD 
                // Use 
                // Aquí deberíamos eliminar los archivos de _EVALX.csv ya que no son utiles y ocupan espacio, sólo queremos
                // obtener los resultados, calcular las métricas y guardarlas en un archivo.
            }
            // Return the evaluation metrics for all the number of folds  (numFolds)
            // TO DO - FORMATO DE SALIDA DE LOS RESULTADOS 

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void populateTrainingSet(Properties properties, Instances trainingSet, Instances testSet, Integer fold ){ // I have duplicated this method from the Predictor class. Should it be a common method?
        //Preparing the Lucene directory
        String luceneDirPath = properties.getProperty("lucene.directorypath");// LUCENE DIRECTORY PATH
        File luceneDir = new File(luceneDirPath);
        if (luceneDir.exists() && luceneDir.isDirectory() && luceneDir.list().length > 0) {
            try {
                FileUtils.forceDelete(luceneDir);
                if (!luceneDir.exists()) {
                    System.out.println("Old Lucene directory deleted");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        //Preparing for lucene indexing 
        String[] stopWords = properties.getProperty("analyzer.stopwords").split(",");
        CharArraySet sWSet = new CharArraySet(Arrays.asList(stopWords), true);
        try (StandardAnalyzer analyzer = new StandardAnalyzer(sWSet)) {
            try (Directory dir = FSDirectory.open(Paths.get(luceneDirPath))) {
                IndexWriterConfig config = new IndexWriterConfig(analyzer);
                config.setSimilarity(new ClassicSimilarity());
                try {
                    IndexWriter writer = new IndexWriter(dir, config);
                    writeIssues(writer, trainingSet);
                    writer.close();
                    predictTTFIssues(properties, dir, analyzer, testSet,fold );
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                System.err.println("Error opening Lucene directory: " + e.getMessage());
            }
        }
    
    }
    private static void writeIssues(IndexWriter writer, Instances trainingSet) {
        Attribute issueID = trainingSet.attribute("ID");
        Attribute startDate = trainingSet.attribute("Start Date");
        Attribute endDate = trainingSet.attribute("End Date");
        Attribute title = trainingSet.attribute("Title");
        Attribute description = trainingSet.attribute("Description");
        for (int i = 0; i < trainingSet.numInstances(); i++) {
            Instance line = trainingSet.get(i);
            Issue issue = new Issue((int)line.value(issueID),
            line.stringValue(title).trim(), line.stringValue(description).trim(), line.stringValue(startDate).trim(), line.stringValue(endDate).trim());
            Document doc = new Document();
            Long time = issue.getTimeSpent();
            doc.add(new StringField("id", String.valueOf(issue.getId()), Field.Store.YES));
            doc.add(new TextField("title", Predictor.escapeSpecialCharacters(issue.getTitle()), Field.Store.YES));
            doc.add(new TextField("description", Predictor.escapeSpecialCharacters(issue.getDescription()),
                    Field.Store.YES));
            doc.add(new StoredField("ttf", time));
            try {
                writer.addDocument(doc);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Populated training set");
    }

    public static void predictTTFIssues(Properties properties, Directory dir, StandardAnalyzer analyzer, Instances testSet , Integer fold) {
        List<List<HashMap<String, String>>> result = new ArrayList<>();
        String issueUrl = properties.getProperty("url.issue");
        List<Integer> issuesID = new ArrayList<>();
        for(int i = 0; i<testSet.numInstances(); i++) { //Getting the list of IDs to predict
            Instance line = testSet.get(i);
            Integer id = (int)line.value(testSet.attribute("ID"));
            issuesID.add(id);
        }
        //Getting issue to predict's data
        for (Integer id : issuesID) {
            String link = issueUrl + id.toString();
            org.jsoup.nodes.Document doc = WebScraper.tryConnection(link);
            Issue issue = IssueFilter.getIssue(doc, id.toString(), IssueType.TRAINING);
            result.add(predictTimeToFix(issue, dir, analyzer, properties));
        }
        // PRINTING RESULTS + WRITING RESULTS IN FILE
        Integer k = Integer.valueOf(properties.getProperty("issues.neighbor"));
        String resultPath = properties.getProperty("result.predictions.file.evaluator").replace(".csv", fold.toString()+".csv");
        StringBuilder csvContent = new StringBuilder();
        for (int i = 0; i < issuesID.size(); i++) { // For each issue to predict
            String title = String.format("Showing the %d closests neighbors for issue %s", k, issuesID.get(i));
            csvContent.append(title+"\n");
            Float predictedTime = 0.0f;
            Float realTTF = 0.0f;
            for (HashMap<String, String> s : result.get(i)) { // For each neighbor
                String score = "Issue ID: " + s.get("id") + " Score: " + s.get("score") +
                        " Time to fix: " + s.get("ttf") + " hours";
                predictedTime += Float.valueOf(s.get("ttf"));
                realTTF = Float.valueOf(s.get("realTTF"));
                csvContent.append(score+"\n");//Adding each neighbor score to file
            }
            predictedTime = predictedTime / k; // hours
            String prediction = "The predicted time to fix for issue " + issuesID.get(i) + " is: " + predictedTime + " hours. ("
            + predictedTime/24 + " days)";
            csvContent.append(prediction+"\n");
            Float timediff= predictedTime - realTTF; // We dont use absolute cause we want to know if we are over or underestimating
            csvContent.append("Real TTF: "+ realTTF.toString() + " hours ( " + realTTF/24 + " days)\n");
            csvContent.append("\u2206TTF (Time difference): "+ timediff.toString() + " hours ( " + timediff/24 + " days)\n");
            csvContent.append("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
            //Writing the prediction in the file
            try {
                FileUtils.writeStringToFile(new File(resultPath), csvContent.toString(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        System.out.println("Result CSV file created successfully!");
    }

    private static List<HashMap<String, String>> predictTimeToFix(Issue issue, Directory dir, StandardAnalyzer analyzer,
            Properties properties) {
        List<HashMap<String, String>> results = new ArrayList<>();
        try {
            IndexReader reader = DirectoryReader.open(dir);
            IndexSearcher searcher = new IndexSearcher(reader);
            IndexSearcher.setMaxClauseCount(Integer.valueOf(properties.getProperty("max.clause.count")));
            searcher.setSimilarity(new ClassicSimilarity());
            String[] fields;
            String[] queries;
            // Since Descriptions can be an empty field, we need to check if is empty to not add it to the query
            if(issue.getDescription().isEmpty()){
                fields = new String[] { "title" };
                queries = new String[] { Predictor.escapeSpecialCharacters(issue.getTitle().trim()) };
            }else{
                fields = new String[] { "title", "description" };
                queries = new String[] { Predictor.escapeSpecialCharacters(issue.getTitle().trim()),
                    Predictor.escapeSpecialCharacters(issue.getDescription().trim()) };
            }
            Map<String, Float> boosts = Map.of("title", 1.0f, "description", 1.0f);
            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer, boosts);
            try {
                Query query = parser.parse(queries, fields, analyzer);
                TopDocs topHits;
                topHits = searcher.search(query, 3);
                ScoreDoc[] hits = topHits.scoreDocs;
                for (int i = 0; i < hits.length; i++) { // For each neighbor
                    HashMap<String, String> result = new HashMap<>();
                    Document doc = searcher.storedFields().document(hits[i].doc);
                    result.put("id", doc.get("id"));
                    result.put("score", String.valueOf(hits[i].score));
                    result.put("ttf", doc.getField("ttf").numericValue().toString());
                    result.put("realTTF", issue.getTimeSpent().toString());
                    results.add(result);
                }
                reader.close();
            } catch (ParseException e) {
                System.out.println("Error parsing the query: " + issue.getId() + " Description:" + issue.getDescription());
                e.printStackTrace();
            }

        } catch (IOException e) {
            System.err.println("Error opening Lucene directory: " + e.getMessage());
        }

        return results;
    }
    
    private static String getCleanFilePath(Properties properties){ // Formats the file to prevent " , ' and , taken as separators or special characters. 
        String issueFile = properties.getProperty("filteredissue.path");
        String outputFile = properties.getProperty("filteredissue.path").replace(".csv", "_cleaned.csv");//Auxiliar file without first line
            try (BufferedReader br = new BufferedReader(new FileReader(issueFile));
                    BufferedWriter bw = new BufferedWriter(new FileWriter(outputFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    List<String> lineSplit = Arrays.asList(line.split("\",\"")); // Splitting the line by ","
                    if(lineSplit.get(4).length() >20000) {
                        continue;} //If the description is too long, we skip this issue. 
                    // THIS SHOULD NOT BE DONE HERE, JUST FOR TESTING PURPOSES. SHOULD BE DONE BEFORE SPLITTING IN FOLDS
                    if(lineSplit.get(4).length()== 0) {
                        lineSplit.set(4 , " ");
                    }
                    lineSplit.set(3, lineSplit.get(3).replace(",", "\\,").replace("'","\\'")); // In the string, we replace every , with \, and every ' with \'
                    lineSplit.set(4, lineSplit.get(4).replace(",", "\\,").replace("'","\\'"));
                    for( int i = 0; i<lineSplit.size();i++) {
                        lineSplit.set(i, lineSplit.get(i).replace("\"", "\\\"")); // On every field we replace every " with \"
                        lineSplit.set(i, lineSplit.get(i).replace("\n", ""));
                    }
                    lineSplit.set(0, lineSplit.get(0).replace("\\\"", ""));
                    lineSplit.set(4, "\""+lineSplit.get(4).substring(0, lineSplit.get(4).length()-2)+"\"");
                    lineSplit.set(3, "\""+lineSplit.get(3)+"\""); 
                    String newLine = String.join(",",lineSplit).trim();
                    bw.write(newLine);
                    bw.newLine();
                }
            }catch (IOException e) { // Files are not generated correctly / read correctly
                e.printStackTrace();
            }
        return outputFile;
    }
}
