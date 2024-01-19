package etsii.tfg.sttfbug.issues;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

public class WebScraper{
    private static final String MAIN_URL = "https://bugs.eclipse.org/bugs/report.cgi?x_axis_field=resolution&y_axis_field=bug_status&z_axis_field=&width=600&height=350&action=wrap&format=table&product=Platform";
    private static final String LRF_FILE_PATH = "STTFBug/resources/LRF.csv";//File with a list of resolved and fixed issues
    private static final String LVF_FILE_PATH = "STTFBug/resources/LVF.csv";//File with a list of verified and fixed issues
    private static final String LCF_FILE_PATH = "STTFBug/resources/LCF.csv";//File with a list of closed and fixed issues
    private static final List<String> lfiles= List.of(LRF_FILE_PATH,LVF_FILE_PATH,LCF_FILE_PATH);
    private static final String ISSUES_URL = "https://bugs.eclipse.org/bugs/show_bug.cgi?id=";
    private static final String ISSUE_HISTORY_URL = "https://bugs.eclipse.org/bugs/show_activity.cgi?id=";

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
                        getResolvedIssuesDocument(doc);
                        break;
                    case LVF_FILE_PATH:
                        getVerifiedIssuesDocument(doc);
                        break;
                    case LCF_FILE_PATH:
                        getClosedIssuesDocument(doc);
                        break;
                    default:
                        break;
                }
    
        }
    }
    /**
     * @param doc Document of the Report: Status/Solution page 
     */
    private static void getResolvedIssuesDocument(Document doc){
        //Documents.getElementById is not able to search for an ID inside of a table, so we are going to do it step-by-step
        Element table = doc.selectFirst("table").selectFirst("table").select("tbody").get(1);
        Element resolvedFixedCell = table.select("tr").get(4).select("td").get(2);//4=Resolved ,2=Fixed
        String auxLink = resolvedFixedCell.selectFirst("a").attr("abs:href");
        Document auxDoc = tryConnection(auxLink);
        String link = auxDoc.getElementsByClass("buglist_menu").first().getElementsByClass("bz_query_links").first().selectFirst("a").attr("abs:href");
        String downloadPath = LRF_FILE_PATH;
        downloadCSV(link, downloadPath);
    }
        // TO-DO group this 3 methods into one.
    private static void getVerifiedIssuesDocument(Document doc){
        Element table = doc.selectFirst("table").selectFirst("table").select("tbody").get(1);
        Element verifiedFixedCell = table.select("tr").get(5).select("td").get(2);//5=Resolved ,2=Fixed
        String auxLink = verifiedFixedCell.selectFirst("a").attr("abs:href");
        Document auxDoc = tryConnection(auxLink);
        String link = auxDoc.getElementsByClass("buglist_menu").first().getElementsByClass("bz_query_links").first().selectFirst("a").attr("abs:href");      
        String downloadPath = LVF_FILE_PATH;
        downloadCSV(link, downloadPath);
    }

    private static void getClosedIssuesDocument(Document doc){
        Element table = doc.selectFirst("table").selectFirst("table").select("tbody").get(1);
        Element closedFixedCell = table.select("tr").get(6).select("td").get(2);//6=Closed ,2=Fixed
        String auxLink = closedFixedCell.selectFirst("a").attr("abs:href");
        Document auxDoc = tryConnection(auxLink);
        String link = auxDoc.getElementsByClass("buglist_menu").first().getElementsByClass("bz_query_links").first().selectFirst("a").attr("abs:href");      
        String downloadPath = LCF_FILE_PATH;
        downloadCSV(link, downloadPath);
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
            //On the connection we will use a timeout of 90 second because when we are loading the resolved issues, we are loading more than 37k issues
            return Jsoup.connect(link).timeout(90*1000).get();
        } catch (IOException e) {
            System.err.println("No se pudo conectar a " + link + ": " + e.getMessage());
            return new Document("");
        }
    }

    
    // public static void getListAllIssues(){
    //     for (String filePath : lfiles) {//for each file
    //         try {
    //             List<String> lines = Files.readAllLines(new File(filePath).toPath());
    //             lines.remove(0);//Header line
    //             for (String line : lines) {//for each line on the csv
    //                 String[] values = line.split(",");
    //                 String id = values[0];
    //                 String link = ISSUES_URL + id;
    //                 Document doc = tryConnection(link);
    //                 Issue issue = getIssue(doc);
    //                 if(issue!=null){
    //                     String historyLink = ISSUE_HISTORY_URL + id;
    //                     Document historyDoc = tryConnection(historyLink);
    //                     issue.setHistory(getHistory(historyDoc));
    //                 }
    //             }
    //         } catch (IOException e) {
    //             System.err.println("An error has occured while reading the file: " + e.getMessage());
    //             e.printStackTrace();
    //         }
    //     }
    // }
    public static void main(String[] args) {
        TESTListAllIssues();
    }
    public static void TESTListAllIssues(){
        
        try{
            List<String> lines = Files.readAllLines(new File(LRF_FILE_PATH).toPath());
            String line = lines.get(1);
            String[] values = line.split(",");
            String id = values[0];
            String link = ISSUES_URL + id;
            Document doc = tryConnection(link);
            Issue issue = getIssue(doc, id);
            System.out.println(issue);
        } catch (IOException e) {
            System.err.println("An error has occured while reading the file: " + e.getMessage());
            e.printStackTrace();
        }
        
    }
    private static void calculateEndDate(Issue issue, Document historyDoc){
        //todo 
        String endDate = null;
        if(endDate!=null){
            issue.setEndDate(endDate);
        }
    }
    private static Issue getIssue(Document doc, String id){
        Issue issue = new Issue();
        Element formElement = doc.getElementById("changeform"); // Central form that contains all the information
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
        .text().replace(" by ", "");
        issue.setStartDate(startDate);
        // Asignee
        String asignee = centralTable.getElementById("bz_show_bug_column_1").select("table tbody tr")
        .get(12).selectFirst("td span span").text();
        issue.setAsignee(asignee);
        // End date 
        String historyURL = ISSUE_HISTORY_URL + id;
        // Since this attribute is not as easy to get, we are going to use the method setHistory to get it.
        calculateEndDate(issue, tryConnection(historyURL));
        
        return issue;
    }
}