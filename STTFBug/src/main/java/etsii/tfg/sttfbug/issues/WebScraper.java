package etsii.tfg.sttfbug.issues;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.jsoup.*;
import org.jsoup.nodes.*;

public class WebScraper{
    private static final String MAIN_URL = "https://bugs.eclipse.org/bugs/report.cgi?x_axis_field=resolution&y_axis_field=bug_status&z_axis_field=&width=600&height=350&action=wrap&format=table&product=Platform";
    private static final String LRF_FILE_PATH = "resources/LRF.csv";//File with a list of resolved and fixed issues
    private static final String LVF_FILE_PATH = "resources/LVF.csv";//File with a list of verified and fixed issues
    private static final String LCF_FILE_PATH = "resources/LCF.csv";//File with a list of closed and fixed issues
    private static final List<String> lfiles= List.of(LRF_FILE_PATH,LVF_FILE_PATH,LCF_FILE_PATH);

    public static void main(String[] args) {
        try {
            searchDocs();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    /**
     * @return A list of the Jsoup document(HTML form) of the different bug list 
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
     * @param ldoc List of documents we are going to use to get the issues
     */
    public static void getListAllIssues(List<Document> ldoc){
            System.out.println("a");
    }
    /**
     * @param doc Document of the Report: Status/Solution page 
     */
    private static void getResolvedIssuesDocument(Document doc){
        //Documents.getElementById is not able to search for an ID inside of a table, so we are going to do it step-by-step
        Element table = doc.select("table").first().select("table").first().select("tbody").get(1);
        Element resolvedFixedCell = table.select("tr").get(4).select("td").get(2);//4=Resolved ,2=Fixed
        String auxLink = resolvedFixedCell.select("a").first().attr("abs:href");
        Document auxDoc = tryConnection(auxLink);
        String link = auxDoc.getElementsByClass("buglist_menu").first().getElementsByClass("bz_query_links").first().select("a").first().attr("abs:href");
        String downloadPath = LRF_FILE_PATH;
        downloadCSV(link, downloadPath);
    }

    private static void getVerifiedIssuesDocument(Document doc){
        Element table = doc.select("table").first().select("table").first().select("tbody").get(1);
        Element verifiedFixedCell = table.select("tr").get(5).select("td").get(2);//5=Resolved ,2=Fixed
        String auxLink = verifiedFixedCell.select("a").first().attr("abs:href");
        Document auxDoc = tryConnection(auxLink);
        String link = auxDoc.getElementsByClass("buglist_menu").first().getElementsByClass("bz_query_links").first().select("a").first().attr("abs:href");      
        String downloadPath = LVF_FILE_PATH;
        downloadCSV(link, downloadPath);
    }

    private static void getClosedIssuesDocument(Document doc){
        Element table = doc.select("table").first().select("table").first().select("tbody").get(1);
        Element closedFixedCell = table.select("tr").get(6).select("td").get(2);//6=Closed ,2=Fixed
        String auxLink = closedFixedCell.select("a").first().attr("abs:href");
        Document auxDoc = tryConnection(auxLink);
        String link = auxDoc.getElementsByClass("buglist_menu").first().getElementsByClass("bz_query_links").first().select("a").first().attr("abs:href");      
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
    // private static Issue getIssue(Document doc){
    //     return null;
    // }

}