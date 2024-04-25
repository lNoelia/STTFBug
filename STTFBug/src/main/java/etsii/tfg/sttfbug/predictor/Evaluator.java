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
import java.util.Collections;
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

public class Evaluator {

    public static void evaluatePredictor(Properties properties) {
        // First we create the file where we will store the results for every fold.
        String resultPath = properties.getProperty("result.predictions.file").replace(".csv",
                "_evaluation_results.csv");
        List<List<String>> rows = new ArrayList<>();
        List<String> headers = Arrays.asList("Mean absolute error", "Number of predictions within the range",
        "Percentage of predictions within the range");
        rows.add(headers); 
        // Load the data from the CSV file
        String issueFile = properties.getProperty("filteredissue.path");
        List<String[]> data = loadDataFromCSV(issueFile);

        Integer[] numFolds = Arrays.stream(properties.getProperty("folds").split(","))
                .map(Integer::parseInt)
                .toArray(Integer[]::new);
        for(Integer folds : numFolds) {
            System.out.println("NUMBER OF FOLDS: "  + folds.toString());
            // EVALUATION OF EACH FOLD
            // Variables to store the evaluation metrics
            Float totaldiff = 0.0f;
            Float inRange = 0.0f;
            Float nLines = 0.0f;

            List<List<String[]>> foldsList = splitDataIntoFolds(data, folds);
            StringBuilder csvContent = new StringBuilder();
            String auxEvalPath = properties.getProperty("result.predictions.file").replace(".csv", "_eval.csv");

            for (int fold = 0; fold < folds; fold++) {
                List<String[]> trainingSet = combineFolds(foldsList, fold);
                List<String[]> testSet = foldsList.get(fold);

                System.out.println("Number of issues for the training set: " + trainingSet.size());
                System.out.println("Number of issues for the testing set: " + testSet.size());

                populateTrainingSet(properties, trainingSet, testSet, fold, csvContent);
                // Writing the prediction in the file
                try {
                    FileUtils.writeStringToFile(new File(auxEvalPath), csvContent.toString(), StandardCharsets.UTF_8);
                    System.out.println("Content written in file sucessfully");
                } catch (IOException e) {
                    e.printStackTrace();
                }

                // READING THE FILE AND GETTING THE METRICS FOR THE EVALUATION

                try (BufferedReader br = new BufferedReader(new FileReader(auxEvalPath))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        nLines++;
                        String lineSplit = line.split("\\[")[1].replace("]", "");
                        Float actualTime = Float.valueOf(lineSplit.split("Actual time=")[1].split(";")[0].trim());
                        Float diffValue = Float.valueOf(lineSplit.split("TTF=")[1].trim());
                        totaldiff += diffValue;
                        if (isBetweenPercentage(actualTime, diffValue, properties)) {
                            inRange++;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            // Calculating the metrics and adding them to the list of rows
            totaldiff = totaldiff / nLines;
            Float percentInRange = (inRange / nLines) * 100;
            List<String> row = Arrays.asList("Number of folds = " + folds.toString(), totaldiff.toString(), inRange.toString(),
                    String.valueOf(percentInRange));
            rows.add(row);
        }
        
        // CREATION OF THE RESULT FILE:
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(resultPath))) {
            for (List<String> row : rows) {
                writer.write(String.join(",", row));
                writer.newLine();
            }
            System.out.println("Evaluation results file has been created successfully!");
        } catch (IOException e) {
            System.out.println("Error writing evaluation results file: " + e.getMessage());
        }

        // Deleting the auxiliar file
        File auxFile = new File(properties.getProperty("result.predictions.file").replace(".csv", "_eval.csv"));
        if (auxFile.exists()) {
            FileUtils.deleteQuietly(auxFile);
        }
    }

    public static List<List<String[]>> splitDataIntoFolds(List<String[]> data, int numFolds) {
        Collections.shuffle(data); // Shuffle the issues
        List<List<String[]>> folds = new ArrayList<>();
        int foldSize = data.size() / numFolds;

        for (int i = 0; i < numFolds; i++) { // Now we split the data into the folds
            int start = i * foldSize; // Index of the first element of the fold
            int end = (i == numFolds - 1) ? data.size() : (i + 1) * foldSize;
            List<String[]> fold = new ArrayList<>(data.subList(start, end));
            folds.add(fold);
        }
        return folds;
    }

    /*
     * Auxiliar function to combine all the folds except the one that is being
     * evaluated
     * This way we can easily get all the training set for the current fold
     */
    public static List<String[]> combineFolds(List<List<String[]>> folds, int excludeFoldIndex) {
        List<String[]> combined = new ArrayList<>();
        for (int i = 0; i < folds.size(); i++) {
            if (i != excludeFoldIndex) {
                combined.addAll(folds.get(i));
            }
        }
        return combined;
    }

    private static void populateTrainingSet(Properties properties, List<String[]> trainingSet, List<String[]> testSet,
            Integer fold, StringBuilder csvContent) {
        // Preparing the Lucene directory
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

        // Preparing for lucene indexing
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
                    predictTTFIssues(properties, dir, analyzer, testSet, fold, csvContent);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (IOException e) {
                System.err.println("Error opening Lucene directory: " + e.getMessage());
            }
        }
    }

    private static void writeIssues(IndexWriter writer, List<String[]> trainingSet) {
        for (String[] line : trainingSet) {
            Issue issue = new Issue(Integer.valueOf(line[0].replace("\"", "")), line[3].trim(), line[4].trim(),
                    line[1].trim(),
                    line[2].trim());
            Document doc = new Document();
            Long time = issue.getTimeSpent();
            doc.add(new StringField("id", String.valueOf(issue.getId()), Field.Store.YES));
            doc.add(new TextField("title", issue.getTitle(), Field.Store.YES));
            doc.add(new TextField("description", issue.getDescription(),
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

    public static void predictTTFIssues(Properties properties, Directory dir, StandardAnalyzer analyzer,
            List<String[]> testSet, Integer fold, StringBuilder csvContent) {
        List<List<HashMap<String, String>>> result = new ArrayList<>();
        String issueUrl = properties.getProperty("url.issue");
        List<Integer> issuesID = new ArrayList<>();
        for (String[] line : testSet) { // Getting the list of IDs to predict
            Integer id = Integer.valueOf(line[0].replace("\"", ""));
            issuesID.add(id);
        }
        // Getting issue to predict's data
        for (Integer id : issuesID) {
            String link = issueUrl + id.toString();
            org.jsoup.nodes.Document doc = WebScraper.tryConnection(link);
            Issue issue = IssueFilter.getIssue(doc, id.toString(), IssueType.TRAINING);
            result.add(predictTimeToFix(issue, dir, analyzer, properties));
        }
        // PRINTING RESULTS + WRITING RESULTS IN FILE
        /*
         * For readability, in the actual predictor we would print all the necesary data
         * to comprehend the prediction. In this case, since we are only using the file
         * as a way to store the results, we will only add the necessary data into it.
         */
        Integer k = Integer.valueOf(properties.getProperty("issues.neighbor"));
        for (int i = 0; i < issuesID.size(); i++) { // For each issue to predict
            Float predictedTime = 0.0f;
            Float realTTF = Float.valueOf(result.get(i).get(0).get("realTTF"));
            for (HashMap<String, String> s : result.get(i)) { // For each neighbor
                predictedTime += Float.valueOf(s.get("ttf"));
            }
            predictedTime = predictedTime / k; // Predicted hours (average of the k closest neighbors)
            Float timediff = Math.abs(predictedTime - realTTF); // We use absolute value since both underestimation and
            // overestimation are undersirable
            String prediction = String
                    .format("Prediction for issue %s: [Actual time= %.2f ; Predicted time= %.2f ; \u2206TTF= %.2f]",
                            issuesID.get(i), realTTF, predictedTime, timediff)
                    .replace(",", ".");
            csvContent.append(prediction + "\n");

        }
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
            // Since Descriptions can be an empty field, we need to check if is empty to not
            // add it to the query
            if (issue.getDescription().isEmpty()) {
                fields = new String[] { "title" };
                queries = new String[] { Predictor.escapeSpecialCharacters(issue.getTitle().trim()) };
            } else {
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
                System.out.println(
                        "Error parsing the query: " + issue.getId() + " Description:" + issue.getDescription());
                e.printStackTrace();
            }

        } catch (IOException e) {
            System.err.println("Error opening Lucene directory: " + e.getMessage());
        }

        return results;
    }

    /**
     * Get list of issues from csv file indicated as "filteredissue.path".
     *
     * @param filePath the path to the CSV file
     * @return a list of string arrays representing the issues from the CSV file
     */
    public static List<String[]> loadDataFromCSV(String filePath) {
        List<String[]> data = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            br.readLine(); // Skip header
            while ((line = br.readLine()) != null) {
                String[] lineSplit = line.split("\",\""); // Splitting the line by ","

                // If the description is too long, we skip this issue.
                if (lineSplit[4].length() > 20000) {
                    continue;
                }

                // We clean unnecessary characters created on split
                lineSplit[0] = lineSplit[0].replace("\"", "");
                lineSplit[4] = lineSplit[4].substring(0, lineSplit[4].length() - 1);

                // If the title or description are empty, we skip this issue since we cannot
                // have a empty query.
                if (lineSplit[3].equals("") || lineSplit[4].equals(""))
                    continue;
                // And we escape special characters
                for (String attribute : lineSplit) {
                    attribute = Predictor.escapeSpecialCharacters(attribute);
                }
                data.add(lineSplit);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data;
    }

    public static boolean isBetweenPercentage(Float actualTime, Float diffValue, Properties properties) {
        Float percentage = Float.valueOf(properties.getProperty("percentage.within"));
        Float lowerBound = actualTime - (actualTime * percentage);
        Float upperBound = actualTime + (actualTime * percentage);
        return diffValue >= lowerBound && diffValue <= upperBound;
    }

}