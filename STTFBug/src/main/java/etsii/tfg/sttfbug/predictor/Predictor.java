package etsii.tfg.sttfbug.predictor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import org.apache.lucene.queryparser.classic.QueryParser;
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

public class Predictor {
    public static void predictListOfIssuesSet(Properties properties) {
        String[] stopWords = properties.getProperty("analyzer.stopwords").split(",");
        CharArraySet sWSet = new CharArraySet(Arrays.asList(stopWords), true);
        try (StandardAnalyzer analyzer = new StandardAnalyzer(sWSet)) {
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
        try (BufferedReader br = Files.newBufferedReader(new File(filePath).toPath(), StandardCharsets.UTF_8)) {
            String line;
            Long i = 0L;
            try (Stream<String> lines = Files.lines(Paths.get(filePath))) {
                Long nLines = lines.count();// Does not count the last empty line
                while ((line = br.readLine()) != null) {
                    i++;
                    if (!line.contains("\"ID\",\"Start Date\",\"End Date\",\"Title\",\"Description\"") && i <= nLines) {
                        List<String> issueArray = List.of(line.split("\",\""));
                        StringBuilder description = new StringBuilder(issueArray.get(4));
                        description.deleteCharAt(description.length() - 1);
                        Issue issue = new Issue(Integer.valueOf(issueArray.get(0).replace("\"", "")),
                                issueArray.get(3), description.toString(), issueArray.get(1), issueArray.get(2));
                        Document doc = new Document();
                        Long time = issue.getTimeSpent();
                        doc.add(new StringField("id", String.valueOf(issue.getId()), Field.Store.YES));
                        doc.add(new TextField("title", escapeSpecialCharacters(issue.getTitle()), Field.Store.YES)); //change to no store?
                        doc.add(new TextField("description", escapeSpecialCharacters(issue.getDescription()),
                                Field.Store.YES));
                        doc.add(new StoredField("ttf", time));
                        writer.addDocument(doc);
                    }
                }

                System.out.println("Number of issues processed: " + (i - 1)); // -1 because of the header
                System.out.println("Training set populated");
            } catch (IOException e) {
                System.err.println("Could not read " + filePath + " file ");
                e.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Could not find the filtered issue file");
        }
    }

    private static void predictTTFIssues(Properties properties, Directory dir, StandardAnalyzer analyzer) {
        List<List<HashMap<String, String>>> result = new ArrayList<>();
        String filePath = properties.getProperty("predict.issue.file"); // File where the issues to be predicted are
        try (BufferedReader br = Files.newBufferedReader(new File(filePath).toPath(), StandardCharsets.UTF_8)) {
            //ISSUE PREDICTION
            Stream<String> lines = br.lines().skip(1);
            List<String> issuesID = lines.map(line -> line.split(",")[0].replace("\"", "")).collect(Collectors.toList());
            String issueUrl = properties.getProperty("url.issue");
            for (String id : issuesID) {
                String link = issueUrl + id;
                org.jsoup.nodes.Document doc = WebScraper.tryConnection(link);
                Issue issue = IssueFilter.getIssue(properties,doc, id, IssueType.PREDICT);
                result.add(predictTimeToFix(issue, dir, analyzer, properties));
            }
            // PRINTING RESULTS + WRITING RESULTS IN FILE
            Integer k = Integer.valueOf(properties.getProperty("issues.neighbor"));
            String resultPath = properties.getProperty("result.predictions.file");
            StringBuilder csvContent = new StringBuilder();
            for (int i = 0; i < issuesID.size(); i++) { // For each issue to predict
                //Print on console
                System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
                String title = String.format("Showing the %d closests neighbors for issue %s", k, issuesID.get(i));
                System.out.println(title);
                csvContent.append(title+"\n");
                Float predictedTime = 0.0f;
                for (HashMap<String, String> s : result.get(i)) { // For each neighbor
                    String score = "Issue ID: " + s.get("id") + " Score: " + s.get("score") +
                            " Time to fix: " + s.get("ttf") + " hours";
                    predictedTime += Float.valueOf(s.get("ttf"));
                    System.out.println(score);
                    csvContent.append(score+"\n");//Adding each neighbor score to file
                }
                predictedTime = predictedTime / k; // hours
                String prediction = "The predicted time to fix for issue " + issuesID.get(i) + " is: " + predictedTime + " hours. ("
                + predictedTime/24 + " days)";
                System.out.println(prediction);
                csvContent.append(prediction+"\n");
                csvContent.append("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");
                //Writing the prediction in the file
                try {
                    FileUtils.writeStringToFile(new File(resultPath), csvContent.toString(), StandardCharsets.UTF_8);
                    System.out.println("Result CSV file created successfully!");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Could not find file with the issues to be predicted");
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
            // Since Descriptions can be an empty field(as seen in the generation of a report in eclipse),
            // we need to check if is empty to not add it to the query
            if (issue.getDescription().isEmpty()) {
                fields = new String[] { "title" };
                queries = new String[] { escapeSpecialCharacters(issue.getTitle().trim()) };
            } else {
                fields = new String[] { "title", "description" };
                queries = new String[] { escapeSpecialCharacters(issue.getTitle().trim()),
                    escapeSpecialCharacters(issue.getDescription().trim()) };
            }
            Map<String, Float> boosts = Map.of("title", 1.0f, "description", 1.0f);
            MultiFieldQueryParser parser = new MultiFieldQueryParser(fields, analyzer, boosts);
            try {
                Query query = parser.parse(queries, fields, analyzer);
                TopDocs topHits;
                Integer k = Integer.valueOf(properties.getProperty("issues.neighbor"));
                topHits = searcher.search(query, k);
                ScoreDoc[] hits = topHits.scoreDocs;
                for (int i = 0; i < hits.length; i++) {
                    HashMap<String, String> result = new HashMap<>();
                    Document doc = searcher.storedFields().document(hits[i].doc);
                    result.put("id", doc.get("id"));
                    result.put("score", String.valueOf(hits[i].score));
                    result.put("ttf", doc.getField("ttf").numericValue().toString());
                    results.add(result);
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

    public static String escapeSpecialCharacters(String query) {
        query = query.replaceAll("([\\[\\](){}+\"\\-'/!~&^<>:;?*])", "\\\\$1"); // escape special characters
        query = query.replace("OR", "\\\\OR").replace("AND", "\\\\AND").replace("NOT", "\\\\NOT");
        query = query.replace("\\n", " ").replace("\\u", "\\\\u"); // We also need to escape unicode indicator
        // This characters are not allowed in the query, so we have to escape them
        query = QueryParser.escape(query);
        return query;
    }

}