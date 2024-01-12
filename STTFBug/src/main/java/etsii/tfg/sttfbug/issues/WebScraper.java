package etsii.tfg.sttfbug.issues;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.jsoup.*;
import org.jsoup.nodes.*;

public class WebScraper{
    private static final String LRF_FILE_PATH = "../../../../../../../resources/LRF.html";//Html file of the List of resolved and fixed issues
    private static final String LVF_FILE_PATH = "../../../../../../../LVF.html";//Html file of the List of verified and fixed issues
    private static final String LCF_FILE_PATH = "../../../../../../../LCF.html";//Html file of the List of closed and fixed issues
    private static final List<String> lfiles= List.of(LRF_FILE_PATH,LVF_FILE_PATH,LCF_FILE_PATH);
    
    /**
     * @return a list of the Jsoup document of the different bug list 
     */
    public static List<Document> searchDocs() throws IOException{
        //TO-DO Implement condition to check if the HTML is available on local, if it isn't, then use Jsoup.connect

        List<Document> res = new ArrayList<>();

        for(String f:lfiles){
            File file = new File(f);
            if (file.exists()) {//If we already have this file, we dont need to look for it again
                res.add(Jsoup.parse(file, "UTF-8"));
            }
            try {
                Document doc = Jsoup.connect("https://bugs.eclipse.org/bugs/report.cgi?bug_file_loc=&bug_file_loc_type=allwordssubstr&bug_id=&bug_id_type=anyexact&chfieldfrom=&chfieldto=Now&chfieldvalue=&email1=&email2=&email3=&emailtype1=substring&emailtype2=substring&emailtype3=substring&field0-0-0=noop&keywords=&keywords_type=allwords&longdesc=&longdesc_type=allwordssubstr&short_desc=&short_desc_type=allwordssubstr&status_whiteboard=&status_whiteboard_type=allwordssubstr&type0-0-0=noop&value0-0-0=&votes=&votes_type=greaterthaneq&x_axis_field=resolution&y_axis_field=bug_status&z_axis_field=&width=600&height=350&action=wrap&format=table&product=Platform").get();
                switch (f) {
                    case LRF_FILE_PATH:
                        Document lrfdoc = getResolvedIssuesDocument(doc);
                        generateDoc(lrfdoc, file);
                        res.add(lrfdoc);
                        break;
                    case LVF_FILE_PATH:
                        //ToDo 
                        break;
                    case LCF_FILE_PATH:
                        //ToDo
                        break;
                    default:
                        break;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    
        return res;
    }

    /**
     * @param ldoc List of documents we are going to use to get the issues
     */
    public static void getListAllIssues(List<Document> ldoc){
            System.out.println("a");
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

    private static void generateDoc(Document doc, File file){
        doc.outputSettings().charset("UTF-8");
        doc.outputSettings().prettyPrint(false);
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        doc.outputSettings().escapeMode(null);
        try {
            FileUtils.writeStringToFile(file, doc.outerHtml(), "UTF-8");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}