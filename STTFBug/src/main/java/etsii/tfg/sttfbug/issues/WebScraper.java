package etsii.tfg.sttfbug.issues;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

import com.opencsv.CSVWriter;

public class WebScraper{
    private static final String LRF_FILE_PATH = "resources/LRF.csv";//File with a list of resolved and fixed issues
    private static final String LVF_FILE_PATH = "resources/LVF.csv";//File with a list of verified and fixed issues
    private static final String LCF_FILE_PATH = "resources/LCF.csv";//File with a list of closed and fixed issues
    private static final List<String> lfiles= List.of(LRF_FILE_PATH,LVF_FILE_PATH,LCF_FILE_PATH);
    private static final String ISSUE_URL = "https://bugs.eclipse.org/bugs/show_bug.cgi?id=";
    private static final String ISSUE_HISTORY_URL = "https://bugs.eclipse.org/bugs/show_activity.cgi?id=";
    private static final String FILTERED_ISSUES_FILE ="resources/FILTERED_ISSUES.csv";

    /**
     * @return Creates 3 different .csv files with the list of issues that are fixed and resolved(LRF), verified(LVF) or closed(LCF).
     */
    public static void searchDocs(Properties properties) throws IOException{
        String urlMain= properties.getProperty("url.main");
        List<String> listDocuments= List.of(properties.getProperty("url.listDocuments").split(","));
        Integer row = 0;
        Integer column = 0;
        for(String f:listDocuments){
            File file = new File("resources/"+f+".csv");
            Files.deleteIfExists(file.toPath());// To avoid unique name issues, we delete the .csv if it already exists 
            Document doc = Jsoup.connect(urlMain).get();
                switch (f) {
                    case LRF_FILE_PATH:
                        getIssuesDocument(doc, LRF_FILE_PATH, 4, 2);// 4 = Resolved, 2 = Fixed
                        break;
                    case LVF_FILE_PATH:
                        getIssuesDocument(doc,LVF_FILE_PATH, 5, 2);// 5 = Verified, 2 = Fixed
                        break;
                    case LCF_FILE_PATH:
                        getIssuesDocument(doc, LCF_FILE_PATH, 6, 2);// 6 = Closed, 2 = Fixed
                        break;
                    default:
                        break;
                }
    
        }
    }
    
    /**
     * Creates a .csv file with a list of issues with the information we need for the TTF estimator. 
     */
    public static void getListAllIssues(Properties properties){
        Integer maxIssues = Integer.parseInt(properties.getProperty("issues.max"));
        List<Issue> issuesList = new ArrayList<>();
        boolean stop = false;
        Integer i= 0;
        for (String filePath : lfiles) {//We iterate through the 3 files
            if(stop){
                break;
            }
            try{
                List<String> lines = Files.readAllLines(new File(filePath).toPath());
                lines.remove(0);//Header line
                for(String line : lines){
                    i++;
                    int progress = (int) ((double) i / maxIssues * 100);
                    String id = line.split(",")[0];
                    String link = ISSUE_URL + id;
                    Document doc = tryConnection(link);
                    Issue issue = getIssue(doc, id, IssueType.TRAINING);
                    printProgressBar(progress);
                    issuesList.add(issue);
                    if(issuesList.size() == maxIssues){//Stop condition for development purposes
                        stop = true;
                        System.out.println("");
                        break;
                    }
                }         
            } catch (IOException e) {
                System.err.println("An error has occured while reading the file: " + e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("Issues list size: " + issuesList.size());
        List<Issue> filteredIssues = filterIssues(issuesList);
        System.out.println("Filtered issues list size: " + filteredIssues.size());
        writeCSV(filteredIssues,FILTERED_ISSUES_FILE); 
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

    private static void writeCSV(List<Issue> issues, String path){
        System.out.println("Generating CSV file");
        String csvFilePath = path;
        try (CSVWriter writer = new CSVWriter(new FileWriter(csvFilePath), ',', '"', '"', "\r\n")){
            String[] header = {"ID", "Start Date", "End Date", "Title", "Description"};
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
            System.out.println("CSV file created at: " + csvFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * @param doc Document of the Report: Status/Solution page 
     * @param path Path where the .csv file will be saved
     * @param row Row of the table where the url to the list of issues is located
     * @param column Column of the table where the url to the list of issues is located
     */
    private static void getIssuesDocument(Document doc, String path, Integer row , Integer column){
        Element table = doc.selectFirst("div div table table tbody");
        Element resolvedFixedCell = table.select("tr").get(row).select("td").get(column);
        String auxLink = resolvedFixedCell.selectFirst("a").attr("abs:href");
        Document auxDoc = tryConnection(auxLink);
        String link = auxDoc.getElementsByClass("buglist_menu").first().getElementsByClass("bz_query_links").first().selectFirst("a").attr("abs:href");
        downloadCSV(link, path);
    }

    
    private static void downloadCSV(String url, String path){
        try {
            FileUtils.copyURLToFile(new URL(url), new File(path), 90*1000, 90*1000);
        } catch (Exception e) {
            System.err.println("An error has occured while downloading the file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Document tryConnection(String link){
        try{
            return Jsoup.connect(link).timeout(60*1000).get();
        } catch (IOException e) {
            System.err.println("No se pudo conectar a " + link + ": " + e.getMessage());
            return new Document("");
        }
    }
    
    /**
     * @param issue Issue object with the information we need for the TTF estimator
     * @param historyDoc JSoup Document of the issue history page
     * @return The same issue object with the end date calculated and the start date reviewed
     */
    private static Issue calculateEndDate(Issue issue, Document historyDoc){
        Elements historyTableRows = historyDoc.select("div table tbody tr");
        historyTableRows.remove(0);//Header row
        String who = null;//Auxiliar var to help us identify the attribute "who" from the past row
        ZonedDateTime when = null;//Auxiliar var to help us identify the attribute "when" from the past row
        List<HistoryRow> history = new ArrayList<>();
        for(Element row : historyTableRows){ // Create a list of "historyRows" from the table to filter the information    
            Elements cellsElements = row.select("td");
            List<String> cells = new ArrayList<>();//Elements.eachText() deletes empty elements, but we need them
            for(Element cell : cellsElements){
                cells.add(cell.text());
            }
            if(cells.size()==5){//If the row has 6 cells, it means that it contains the who and when attributes
                HistoryRow historyRow = new HistoryRow(cells);
                who = historyRow.getWho();//we update the auxiliar vars
                when = historyRow.getWhen(); 
                history.add(historyRow);
            }else{
                HistoryRow historyRow = new HistoryRow(cells, who, when);
                history.add(historyRow);
            }
        }
        List<HistoryRow> lResolutionFixed = new ArrayList<>();
        for(HistoryRow historyRow : history){ // Select the rows where the resolution attribute changed to fixed
            if(historyRow.getWhat().equals("Resolution") && historyRow.getAdded().equals("FIXED")){
                  lResolutionFixed.add(historyRow);
            }
        }
        if(lResolutionFixed.size()==1){//if there's only one time where the issue was set to fixed, that's it's end date
            HistoryRow rowFixed = lResolutionFixed.get(0);
            issue.setEndDate(rowFixed.getWhen());
            issue = reviewStartDate(issue, history, rowFixed);
        }else if(lResolutionFixed.isEmpty()){//if the issue was never set to fixed, we set the end date to null so we can discard it
            issue.setEndDate(null);
        }else{// REVIEW THIS PART
            HistoryRow lastChange = lResolutionFixed.get(lResolutionFixed.size()-1);
            issue.setEndDate(lastChange.getWhen());//The final date is the last time it's resolution was set to fixed.
            issue = reviewStartDate(issue, history, lastChange);
        }
        return issue;
    }

    /**
     * Auxiliar method to review the start date of an issue
     * @param issue issue object with the information we need for the TTF estimator
     * @param history List of historyRows of the issue (changes made on the issue report)
     * @param rowFixed HistoryRow where the resolution was set to fixed
     * @return The same issue object with the start date reviewed 
     */
    private static Issue reviewStartDate(Issue issue, List<HistoryRow> history, HistoryRow rowFixed) {
        ZonedDateTime auxstartDate = issue.getStartDate();
        List<HistoryRow> lAssigneeChanged = new ArrayList<>();//Auxiliar list with the historyRows where the asignee changed
        for(HistoryRow historyRow : history){
            if(historyRow.getWhat().equals("Assignee")){
                lAssigneeChanged.add(historyRow);
            }
        }
        if (lAssigneeChanged.isEmpty()) {//If the assignee was never changed, then the start date is correct
            return issue;
        }else{
            for(HistoryRow assigneeRow : lAssigneeChanged){//We iterate through the list of assignee changes to get the correct start date
                if(assigneeRow.getWhen().compareTo(auxstartDate)>0 && assigneeRow.getWhen().compareTo(rowFixed.getWhen())<0){
                    auxstartDate = assigneeRow.getWhen();
                }
            }
            issue.setStartDate(auxstartDate);
            return issue;
        }
        
    }

    /**
     * @param doc JSoup Document of the issue page
     * @param id  ID of the issue
     * @param type Type of issue (TRAINING or PREDICT)
     * @return Issue object with the information we need for the TTF estimator
     */
    public static Issue getIssue(Document doc, String id, IssueType type){
        Issue issue = new Issue();
        Element formElement = doc.getElementById("changeform"); // Central form that contains all the information
        //ID
        issue.setId(Integer.parseInt(id));
        // Title
        Element titleElement = formElement.getElementById("summary_container");
        String title = titleElement.text().replace("-","").trim();
        issue.setTitle(title);
        // Description
        Element commentTable = formElement.getElementsByClass("bz_comment_table").get(0);
        Element descriptionElement = commentTable.getElementById("c0").select("pre").get(0);
        issue.setDescription(descriptionElement.text());
        if(type.equals(IssueType.TRAINING)){
            // Start date
            Element centralTable = formElement.selectFirst("table tbody tr");
            String startDate = centralTable.getElementById("bz_show_bug_column_2").selectFirst("table tbody tr td")
            .text().split("by")[0].trim();
            issue.setStartDateStr(startDate);
            // End date 
            String historyURL = ISSUE_HISTORY_URL + id;
            issue = calculateEndDate(issue, tryConnection(historyURL));
        }
        return issue;
    }

    private static List<Issue> filterIssues(List<Issue> issues){
        List<Issue> filteredIssues = new ArrayList<>();
        for(Issue issue : issues){
            if(issue.getId()!=null && issue.getTitle()!=null && issue.getDescription()!=null 
            && issue.getStartDate()!=null && issue.getEndDate()!=null
            && isAValidTime(issue)){
                filteredIssues.add(issue);
            }
        }
        return filteredIssues;
    }

    private static boolean isAValidTime(Issue issue){
        return issue.getStartDate().compareTo(issue.getEndDate()) < 0 && issue.getEndDate().compareTo(ZonedDateTime.now()) < 0 
            && issue.getTimeSpent() > 5;
    }
}