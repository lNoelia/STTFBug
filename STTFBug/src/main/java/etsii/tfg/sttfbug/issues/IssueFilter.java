package etsii.tfg.sttfbug.issues;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.opencsv.CSVWriter;

public class IssueFilter {

    private static Integer numberReopenedIssues = 0;

    /**
     * Creates a .csv file with a list of issues with the information we need for
     * the TTF estimator.
     */
    public static void getListAllIssues(Properties properties) throws InvalidNameException{
        numberReopenedIssues = 0; // Resets the number of reopened issues everytime this method is called
        List<String> lfiles;
        lfiles = getlistFiles(properties.getProperty("issues.list.documents"), properties);
        String issueUrl = properties.getProperty("url.issue");
        Integer maxIssues = calculateMaxIssues(properties, lfiles);
        System.out.println("Issues to be processed: " + maxIssues);
        String filteredIssueFile = properties.getProperty("filteredissue.path");
        List<Issue> issuesList = new ArrayList<>();
        boolean stop = false;
        Integer i = 0;
        for (String filePath : lfiles) {// We iterate through all given files
            if (stop) {
                break;
            }
            try {
                List<String> lines = Files.readAllLines(new File(filePath).toPath());
                lines.remove(0);// Header line
                for (String line : lines) {
                    i++;
                    int progress = (int) ((double) i / maxIssues * 100);
                    String id = line.split(",")[0];
                    String link = issueUrl + id;
                    Document doc = WebScraper.tryConnection(link);
                    Issue issue = getIssue(properties, doc, id, IssueType.TRAINING);
                    printProgressBar(progress);
                    issuesList.add(issue);
                    if (issuesList.size() == maxIssues) {// Stop condition for development purposes
                        stop = true;
                        System.out.println("");
                        break;
                    }
                }
            } catch (IOException e) {
                throw new InvalidNameException("File " + filePath + " does not exists : " + e.getMessage());
            }
        }
        System.out.println("Issues list size: " + issuesList.size());
        List<Issue> filteredIssues = filterIssues(issuesList, properties);
        System.out.println("Filtered issues list size: " + filteredIssues.size());
        System.out.println("Number of issues reopened:" + numberReopenedIssues);
        writeCSV(filteredIssues, filteredIssueFile);
    }

    private static Integer calculateMaxIssues(Properties properties, List<String> lfiles) {
        Integer maxIssues = Integer.parseInt(properties.getProperty("issues.max"));
        if (maxIssues < -1) {
            System.out.println("Invalid max issues value, setting it to max value possible.");
            maxIssues = -1;
        }
        Long totalIssues = 0L;
        for (String file : lfiles) {
            try {
                Stream<String> lines = Files.lines(new File(file).toPath());
                Long nlines = lines.count() - 1;
                lines.close();
                totalIssues += nlines;
            } catch (IOException e) {
                System.err.println("File " + file + " does not exists : " + e.getMessage());
                e.printStackTrace();
            }
        }
        if (maxIssues == -1) {
            return totalIssues.intValue();
        } else if (totalIssues < maxIssues) {
            return totalIssues.intValue();
        } else {
            return maxIssues;
        }
    }

    private static List<String> getlistFiles(String strDoc, Properties properties) throws InvalidNameException{
        String[] files = strDoc.split(",");
        List<String> lfiles = new ArrayList<>();
        for (String file : files) {
            if (file.length() == 2) {
                String resources = properties.getProperty("eclipseissues.directorypath");
                String res = resources + File.separator + file + ".csv";
                lfiles.add(res);
            } else {
                throw new InvalidNameException("Invalid name length in document " + file);
            }
        }
        return lfiles;
    }

    private static void printProgressBar(int progress) {
        int barLength = 50;
        System.out.print("Progress: [" + repeatCharacter('#', progress * barLength / 100));
        System.out.print(repeatCharacter('-', (100 - progress) * barLength / 100));
        System.out.print("] " + progress + "%\r");
    }

    private static String repeatCharacter(char character, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(character);
        }
        return sb.toString();
    }

    private static void writeCSV(List<Issue> issues, String path) {
        System.out.println("Generating CSV file");

        try (OutputStreamWriter oWriter = new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8)) {
            CSVWriter writer = new CSVWriter(oWriter, ',', '"', '"', "\r\n");
            String[] header = { "ID", "Start Date", "End Date", "Title", "Description" };
            writer.writeNext(header);
            for (Issue issue : issues) {
                String[] data = {
                        String.valueOf(issue.getId()),
                        issue.getStartDatetoString(),
                        issue.getEndDatetoString(),
                        issue.getTitle(),
                        issue.getDescription(),
                };
                writer.writeNext(data);
            }
            System.out.println("CSV file created at: " + path);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * @param issue      Issue object with the information we need for the TTF
     *                   predictor
     * @param historyDoc JSoup Document of the issue history page
     * @return The same issue object with the end date calculated and the start date
     *         reviewed
     */
    private static void calculateEndDate(Issue issue, Document historyDoc) {
        Elements historyTableRows = historyDoc.select("div table tbody tr");
        if (historyTableRows.isEmpty()) {
            issue.setEndDate(null);

        } else {
            List<HistoryRow> history = prepareHistoryRows(historyTableRows);
            List<HistoryRow> lResolutionFixed = new ArrayList<>();
            for (HistoryRow historyRow : history) { // Select the rows where the resolution attribute changed to fixed
                if (historyRow.getWhat().equals("Resolution") && historyRow.getAdded().equals("FIXED")) {
                    lResolutionFixed.add(historyRow);
                }
            }
            if (lResolutionFixed.size() == 1) {// if there's only one time where the issue was set to fixed, that's it's
                                               // end date
                HistoryRow rowFixed = lResolutionFixed.get(0);
                issue.setEndDate(rowFixed.getWhen());
                reviewStartDate(issue, history, rowFixed);
            } else if (lResolutionFixed.isEmpty()) {// if the issue was never set to fixed, we set the end date to null
                                                    // so we can discard it
                issue.setEndDate(null);
            } else {
                // In this case,some of the issue might have been "reopened", lets count them to
                // make statistics and take a decision based on that
                isReopendIssue(history);
                HistoryRow lastChange = lResolutionFixed.get(lResolutionFixed.size() - 1);
                issue.setEndDate(lastChange.getWhen());// The final date is the last time it's resolution was set to
                                                       // fixed.
                reviewStartDate(issue, history, lastChange);
            }
        }

    }

    private static List<HistoryRow> prepareHistoryRows(Elements historyTableRows) {
        historyTableRows.remove(0);// Header row
        List<HistoryRow> history = new ArrayList<>();
        String who = null;// Auxiliar var to help us identify the attribute "who" from the past row
        ZonedDateTime when = null;// Auxiliar var to help us identify the attribute "when" from the past row
        for (Element row : historyTableRows) { // Create a list of "historyRows" from the table to filter the
                                               // information
            Elements cellsElements = row.select("td");
            List<String> cells = new ArrayList<>();// Elements.eachText() deletes empty elements, but we need them
            for (Element cell : cellsElements) {
                cells.add(cell.text());
            }
            if (cells.size() == 5) {// If the row has 6 cells, it means that it contains the who and when attributes
                HistoryRow historyRow = new HistoryRow(cells);
                who = historyRow.getWho();// we update the auxiliar vars
                when = historyRow.getWhen();
                history.add(historyRow);
            } else {
                HistoryRow historyRow = new HistoryRow(cells, who, when);
                history.add(historyRow);
            }
        }
        return history;
    }

    private static void isReopendIssue(List<HistoryRow> history) {
        List<HistoryRow> lReopenedRows = new ArrayList<>();
        for (HistoryRow historyRow : history) { // Select the rows where the resolution attribute changed to reopened
            if (historyRow.getWhat().equals("Status") && historyRow.getAdded().equals("REOPENED")) {
                lReopenedRows.add(historyRow);
            }
        }
        if (!lReopenedRows.isEmpty()) {
            numberReopenedIssues++;
        }

    }

    /**
     * Auxiliar method to review the start date of an issue
     * 
     * @param issue    issue object with the information we need for the TTF
     *                 estimator
     * @param history  List of historyRows of the issue (changes made on the issue
     *                 report)
     * @param rowFixed HistoryRow where the resolution was set to fixed
     *                 "Returns" the same issue object with the start date reviewed
     */
    private static void reviewStartDate(Issue issue, List<HistoryRow> history, HistoryRow rowFixed) {
        ZonedDateTime auxstartDate = issue.getStartDate();
        List<HistoryRow> lAssigneeChanged = new ArrayList<>();// Auxiliar list with the historyRows where the asignee
                                                              // changed
        for (HistoryRow historyRow : history) {
            if (historyRow.getWhat().equals("Assignee")) {
                lAssigneeChanged.add(historyRow);
            }
        }
        if (!lAssigneeChanged.isEmpty()) {// If the assignee was never changed, then the start date is correct, if it's
                                          // not...
            for (HistoryRow assigneeRow : lAssigneeChanged) {// We iterate through the list of assignee changes to get
                                                             // the correct start date
                if (assigneeRow.getWhen().compareTo(auxstartDate) > 0
                        && assigneeRow.getWhen().compareTo(rowFixed.getWhen()) < 0) {
                    auxstartDate = assigneeRow.getWhen();
                }
            }
            issue.setStartDate(auxstartDate);
        }

    }

    /**
     * @param properties Properties object with the configuration of the application
     * @param doc        JSoup Document of the issue page
     * @param id         ID of the issue
     * @param type       Type of issue (TRAINING or PREDICT)
     * @return Issue object with the information we need for the TTF estimator
     */
    public static Issue getIssue(Properties properties, Document doc, String id, IssueType type) {
        String historyURL = properties.getProperty("url.issue.history") + id;
        Issue issue = new Issue();
        Element formElement = doc.getElementById("changeform"); // Central form that contains all the information
        // ID
        issue.setId(Integer.parseInt(id));
        // Title
        Element titleElement = formElement.getElementById("summary_container");
        String title = titleElement.text().replace("-", "").trim();
        issue.setTitle(title);
        // Description
        Element commentTable = formElement.getElementsByClass("bz_comment_table").get(0);
        Element descriptionElement = commentTable.getElementById("c0").select("pre").get(0);
        issue.setDescription(descriptionElement.text().replaceAll("\\s+", " "));
        if (type.equals(IssueType.TRAINING)) {
            // Start date
            Element centralTable = formElement.selectFirst("table tbody tr");
            String startDate = centralTable.getElementById("bz_show_bug_column_2").selectFirst("table tbody tr td")
                    .text().split("by")[0].trim();
            issue.setStartDateStr(startDate);
            // End date
            calculateEndDate(issue, WebScraper.tryConnection(historyURL));
        }
        return issue;
    }

    private static List<Issue> filterIssues(List<Issue> issues, Properties properties) {
        List<Issue> filteredIssues = new ArrayList<>();
        for (Issue issue : issues) {
            if (isIssueValid(issue, properties)) {
                if (isAValidTime(issue, properties)) {// This check has to be outside because of conflicts when
                                                      // comparing null values
                    filteredIssues.add(issue);
                }
            }
        }
        return filteredIssues;
    }

    private static boolean isIssueValid(Issue issue, Properties properties) {
        String[] fields = properties.getProperty("issue.fields").split(",");
        for (String key : fields) {
            boolean shouldNotBeNull = Boolean.parseBoolean(properties.getProperty("notnull." + key));
            if (shouldNotBeNull && getProperty(issue, key) == null) {
                return false; // Issue does not meet criteria
            }
        }
        return true;
    }

    private static Object getProperty(Issue issue, String attrName) {
        try {
            String methodName = "get" + attrName.substring(0, 1).toUpperCase() + attrName.substring(1);
            return Issue.class.getMethod(methodName).invoke(issue);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static boolean isAValidTime(Issue issue, Properties properties) {
        if (Boolean.parseBoolean(properties.getProperty("time.isvalid"))) {
            return issue.getStartDate().compareTo(issue.getEndDate()) < 0
                    && issue.getEndDate().compareTo(ZonedDateTime.now()) < 0
                    && issue.getTimeSpent() > 0.1; //Minimun time spent in a issue to be valid is 6 minutes
        } else {
            return true;
        }
    }

}
