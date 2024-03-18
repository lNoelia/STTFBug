package etsii.tfg.sttfbug.issues;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.jsoup.*;
import org.jsoup.nodes.*;

public class WebScraper{

    /**
     * @return Creates 3 different .csv files with the list of issues that are fixed and resolved(LRF), verified(LVF) or closed(LCF).
     */
    public static void searchDocs(Properties properties) throws IOException{
        String urlMain= properties.getProperty("url.main");
        List<String> listDocuments= List.of(properties.getProperty("issues.list.documents").split(","));
        for(String f:listDocuments){
            if(f.length()!=2){
                System.out.println("Invalid name length in document " + f);
            }else{
                Integer column = getColumn(f.charAt(0));
                Integer row = getRow(f.charAt(1));
                String resources = properties.getProperty("eclipseissues.directorypath");
                String filePath = resources + File.separator + f + ".csv";
                File file = new File(filePath);
                Files.deleteIfExists(file.toPath());// To avoid unique name conflicts, we delete the .csv if it already exists 
                Document doc = tryConnection(urlMain);
                if(row != -1 && column != -1){
                    getIssuesDocument(doc, filePath, row, column);
                }else{
                    System.out.println("Invalid type of document: " + f);
                }
            }
        }
    }
    
    private static Integer getRow(char f) {
        Integer res = -1;
        switch(f){
            case 'U': res = 0; break;
            case 'N': res = 1; break;
            case 'A': res = 2; break;
            case 'O': res = 3; break;
            case 'R': res = 4; break;
            case 'V': res = 5; break;
            case 'C': res = 6; break;
            case 'T': res = 7; break;
            default: return -1;
        }
        return res;
    }
    private static Integer getColumn(char f) {
        Integer res = -1;
        switch(f){
            case '-': res = 1; break;
            case 'F': res = 2; break;
            case 'I': res = 3; break;
            case 'W': res = 4; break;
            case 'D': res = 5; break;
            case '4': res = 6; break;
            case 'M': res = 7; break;
            case 'N': res = 8; break;
            case 'T': res = 9; break;
            default: return -1;
        }
        return res;
    }

    /**
     * @param doc Document of the Report: Status/Solution page 
     * @param path Path where the .csv file will be saved
     * @param row Row of the table where the url to the list of issues is located
     * @param column Column of the table where the url to the list of issues is located
     */
    private static void getIssuesDocument(Document doc, String path, Integer row , Integer column){
        Element table = doc.selectFirst("div div table table tbody");
        Element cell = table.select("tr").get(row).select("td").get(column);
        Element cellLink = cell.selectFirst("a");
        if(cellLink!=null){
            String url = cellLink.attr("abs:href");
            Document auxDoc = tryConnection(url);
            String downloadLink = auxDoc.getElementsByClass("buglist_menu").first().getElementsByClass("bz_query_links").first().selectFirst("a").attr("abs:href");
            downloadCSV(downloadLink, path);
        }else{
            System.out.println("No link found for the document "+ path.split("/")[1]);
        }
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
            Document doc = Jsoup.connect(link).timeout(60*1000).get();
            Thread.sleep(0);
            return doc;
        } catch (InterruptedException e) {
            System.err.println("The thread execution has been interrupted: " + e.getMessage());
            e.printStackTrace();
            Thread.currentThread().interrupt();
            return new Document("");
        }catch (IOException e) {
            System.err.println("Can't conect to  " + link + ": " + e.getMessage());
            return new Document("");
        }
    }
    
    
}