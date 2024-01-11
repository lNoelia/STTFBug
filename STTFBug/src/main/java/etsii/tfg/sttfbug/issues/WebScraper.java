package etsii.tfg.sttfbug.issues;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.*;
import org.jsoup.nodes.*;

public class WebScraper{
    public static void main(String[] args) {
        List<Document> lDocuments = searchDocs();
        ArrayList<Issue> lIssues = new ArrayList<>();
        for(Document doc:lDocuments){
            lIssues.addAll(getListIssues(doc));
        }
    }
    
    public static List<Document> searchDocs(){
        //TO-DO Implement condition to check if the HTML is available on local, if it isn't, then use Jsoup.connect
        List<Document> res = new ArrayList<>();
        try {
            Document doc = Jsoup.connect("https://bugs.eclipse.org/bugs/report.cgi?bug_file_loc=&bug_file_loc_type=allwordssubstr&bug_id=&bug_id_type=anyexact&chfieldfrom=&chfieldto=Now&chfieldvalue=&email1=&email2=&email3=&emailtype1=substring&emailtype2=substring&emailtype3=substring&field0-0-0=noop&keywords=&keywords_type=allwords&longdesc=&longdesc_type=allwordssubstr&short_desc=&short_desc_type=allwordssubstr&status_whiteboard=&status_whiteboard_type=allwordssubstr&type0-0-0=noop&value0-0-0=&votes=&votes_type=greaterthaneq&x_axis_field=resolution&y_axis_field=bug_status&z_axis_field=&width=600&height=350&action=wrap&format=table&product=Platform").get();
            res.add(getResolvedIssuesDocument(doc)); //RF: Resolved Fixed
        } catch (IOException e) {
            e.printStackTrace();
        }
        return res;
    }
    /**
     * @param doc Document of the Report: Status/Solution page 
     * @return The document with ALL the issues marked as resolved and fixed
     */
    private static Document getResolvedIssuesDocument(Document doc){
        //Documents.getElementById is not able to search for an ID inside of a table, so we are going to do it step-by-step
        Element table = doc.select("table").first().select("table").first().select("tbody").get(1);
        Element resolvedFixedCell = table.select("tr").get(4).select("td").get(2);//4=Resolved ,2=Fixed
        String auxLink = resolvedFixedCell.select("a").first().attr("abs:href");
        Document auxDoc = tryConnection(auxLink);//This is the document with the first 1000 issues, but we need to see all the issues.
        String link = auxDoc.getElementsByClass("bz_result_count").first().select("a").first().attr("abs:href");
        Document res = tryConnection(link);// Document with all the issues marked as resolved and fixed.
        System.out.println(res.select("table").first().select("tbody").first().text());//TEST
        return res;
    }
    private static Document tryConnection(String link){
        try{
            //On the connection we will use a timeout of 75 second because when we are loading the resolved issues, we are loading more than 37k issues
            return Jsoup.connect(link).timeout(75*1000).get();
        } catch (IOException e) {
            System.err.println("No se pudo conectar a " + link + ": " + e.getMessage());
            return new Document("");
        }
    }
    // private static Issue getIssue(Document doc){
    //     return null;
    // }
    private static ArrayList<Issue> getListIssues(Document doc) {
        Element lIssues = doc.select("table").first();//.select("tbody").first();
        System.out.println(doc.title());
        System.out.println(lIssues.text());
        return null;
    }

}