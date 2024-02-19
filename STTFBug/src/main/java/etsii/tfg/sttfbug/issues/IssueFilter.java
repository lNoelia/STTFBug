package etsii.tfg.sttfbug.issues;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.jsoup.nodes.Document;
import org.jsoup.select.Elements;

import com.opencsv.CSVWriter;

public class IssueFilter {

    /**
     * Creates a .csv file with a list of issues with the information we need for the TTF estimator. 
     */
    public static void getListAllIssues(Properties properties){
        Integer maxIssues = Integer.parseInt(properties.getProperty("issues.max"));
        List<String> lfiles = getlistFiles(properties.getProperty("issues.list.documents"));
        String issueUrl= properties.getProperty("issues.url");
        List<Issue> issuesList = new ArrayList<>();
        boolean stop = false;
        Integer i= 0;
        for (String filePath : lfiles) {//We iterate through all given files
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
                    String link = issueUrl + id;
                    Document doc = WebScraper.tryConnection(link);
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
                System.err.println("File "+filePath+" does not exists : " + e.getMessage());
                e.printStackTrace();
            }
        }
        System.out.println("Issues list size: " + issuesList.size());
        List<Issue> filteredIssues = filterIssues(issuesList);
        System.out.println("Filtered issues list size: " + filteredIssues.size());
        writeCSV(filteredIssues,FILTERED_ISSUES_FILE); 
    }

    private static List<String> getlistFiles(String strDoc) {
        String[] files = strDoc.split(",");
        List<String> lfiles = new ArrayList<>();
        for(String file : files){
            if(file.length() ==2){
                String res= "resources/"+file+".csv";
                lfiles.add(res);
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
