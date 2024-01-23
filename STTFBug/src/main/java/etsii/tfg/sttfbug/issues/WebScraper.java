package etsii.tfg.sttfbug.issues;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

import com.opencsv.CSVWriter;

public class WebScraper{
    private static final String MAIN_URL = "https://bugs.eclipse.org/bugs/report.cgi?x_axis_field=resolution&y_axis_field=bug_status&z_axis_field=&width=600&height=350&action=wrap&format=table&product=Platform";
    private static final String LRF_FILE_PATH = "resources/LRF.csv";//File with a list of resolved and fixed issues
    private static final String LVF_FILE_PATH = "resources/LVF.csv";//File with a list of verified and fixed issues
    private static final String LCF_FILE_PATH = "resources/LCF.csv";//File with a list of closed and fixed issues
    private static final List<String> lfiles= List.of(LRF_FILE_PATH,LVF_FILE_PATH,LCF_FILE_PATH);
    private static final String ISSUES_URL = "https://bugs.eclipse.org/bugs/show_bug.cgi?id=";
    private static final String ISSUE_HISTORY_URL = "https://bugs.eclipse.org/bugs/show_activity.cgi?id=";
    private static final String FILTERED_ISSUES_FILE ="resources/FILTERED_ISSUES.csv";
    private static final Integer NUMBER_OF_ISSUES = 10;

    /**
     * @return Creates 3 different .csv files with the list of issues that are fixed and resolved(LRF), verified(LVF) or closed(LCF).
     */
    public static void searchDocs() throws IOException{
        for(String f:lfiles){
            File file = new File(f);
            Files.deleteIfExists(file.toPath());// To avoid unique name issues, we delete the .csv if it already exists 
            Document doc = Jsoup.connect(MAIN_URL).get();
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
    private static Document tryConnection(String link){
        try{
            return Jsoup.connect(link).timeout(60*1000).get();
        } catch (IOException e) {
            System.err.println("No se pudo conectar a " + link + ": " + e.getMessage());
            return new Document("");
        }
    }

    
    public static void getListAllIssues(){
        List<Issue> issues = new ArrayList<>();
        boolean stop = false;
        for (String filePath : lfiles) {//for each file
            if(stop){
                break;
            }
            try{
                List<String> lines = Files.readAllLines(new File(filePath).toPath());
                lines.remove(0);//Header line
                for(String line : lines){
                    String id = line.split(",")[0];
                    String link = ISSUES_URL + id;
                    Document doc = tryConnection(link);
                    Issue issue = getIssue(doc, id);
                    issues.add(issue);
                    System.out.println(issue);
                    if(issues.size() == NUMBER_OF_ISSUES){//Stop condition for development purposes
                        stop = true;
                        break;
                    }
                }    
                writeCSV(issues);       
            } catch (IOException e) {
                System.err.println("An error has occured while reading the file: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    public static void testListAllIssues(){
        try{
            List<String> lines = Files.readAllLines(new File(LRF_FILE_PATH).toPath());
            List<Issue> issues = new ArrayList<>();
            lines.remove(0);//Header line
            for(String line : lines){
                String id = line.split(",")[0];
                String link = ISSUES_URL + id;
                Document doc = tryConnection(link);
                Issue issue = getIssue(doc, id);
                issues.add(issue);
                System.out.println(issue);
                if(issues.size() == NUMBER_OF_ISSUES){
                    break;
                }
            }    
            writeCSV(issues);       
        } catch (IOException e) {
            System.err.println("An error has occured while reading the file: " + e.getMessage());
            e.printStackTrace();
        }
        
    }
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
        List<HistoryRow> lResolutionFixed = new ArrayList<>(); //Auxiliar list to store the historyRows that have the resolution set to fixed
        for(HistoryRow historyRow : history){
            if(historyRow.getWhat().equals("Resolution") && historyRow.getAdded().equals("FIXED")){ //If the resolution is set to fixed
                  lResolutionFixed.add(historyRow); //we add the rows when the resolution attribute changed to fixed
            }
        }
        if(lResolutionFixed.size()==1){//if there's only one time where the issue was set to fixed, that's it's end date
            HistoryRow rowFixed = lResolutionFixed.get(0);
            issue.setEndDate(rowFixed.getWhen());
            issue = reviewStartDate(issue, history, rowFixed);
        }else if(lResolutionFixed.isEmpty()){//if there's the issue was never set to fixed, we set the end date to null so we can discard it
            issue.setEndDate(null);
        }else{// REVIEW THIS PART
            HistoryRow lastChange = lResolutionFixed.get(lResolutionFixed.size()-1);
            issue.setEndDate(lastChange.getWhen());//The final date is the last time it's resolution was set to fixed.
            issue = reviewStartDate(issue, history, lastChange);
        }
        return issue;
    }
    private static Issue reviewStartDate(Issue issue, List<HistoryRow> history, HistoryRow rowFixed) {
        ZonedDateTime auxstartDate = issue.getStartDate();
        List<HistoryRow> lAssigneeChanged = new ArrayList<>();//Auxiliar list with the historyRows where the asignee changed
        for(HistoryRow historyRow : history){
            if(historyRow.getWhat().equals("Assignee")){
                lAssigneeChanged.add(historyRow);
            }
        }
        if (lAssigneeChanged.isEmpty()) {//If the assignee is the same as stated on the main page, then the start date is correct
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
    private static Issue getIssue(Document doc, String id){
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
        // Start date
        Element centralTable = formElement.selectFirst("table tbody tr");
        String startDate = centralTable.getElementById("bz_show_bug_column_2").selectFirst("table tbody tr td")
        .text().split("by")[0].trim();
        issue.setStartDateStr(startDate);
        // Asignee
        String assignee = centralTable.getElementById("bz_show_bug_column_1").select("table tbody tr")
        .get(12).selectFirst("td span span").text();
        issue.setAssignee(assignee);
        // End date 
        String historyURL = ISSUE_HISTORY_URL + id;
        // Since this attribute is not as easy to get, we are going to use the method setHistory to get it.
        issue = calculateEndDate(issue, tryConnection(historyURL));
        
        return issue;
    }

    public static void writeCSV(List<Issue> issues){
        String csvFilePath = FILTERED_ISSUES_FILE;
        try (CSVWriter writer = new CSVWriter(new FileWriter(csvFilePath), ',', '"', '"', "\r\n")){
            String[] header = {"ID", "Title", "Description", "Start Date", "End Date", "Assignee"};
            writer.writeNext(header);
            for (Issue issue : issues) {
                String[] data = {
                    String.valueOf(issue.getId()),
                    issue.getTitle(),
                    issue.getDescription(),
                    issue.getStartDatetoString(),
                    issue.getEndDatetoString(),
                    issue.getAssignee()
                };
                writer.writeNext(data);
            }
            System.out.println("CSV file created at: " + csvFilePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}