package etsii.tfg.sttfbug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import etsii.tfg.sttfbug.issues.InvalidNameException;
import etsii.tfg.sttfbug.issues.WebScraper;

class WebScraperTest {
    private static Properties properties;

    @BeforeAll 
    public static void setUp() {
        try (InputStream input = new FileInputStream("testconfig.properties")) {
            properties = new Properties();
            properties.load(input);
            properties.setProperty("issues.list.documents", "-U,FC");
        } catch (Exception e) {
            e.printStackTrace(); 
        }
    }

    @Test
    void positiveTestGetDocument(){
        try {
            WebScraper.searchDocs(properties);
            String directory= properties.getProperty("eclipseissues.directorypath");
            for(String filestr: properties.getProperty("issues.list.documents").split(",")){
                File file = new File(directory, filestr+".csv");
                assertTrue(file.exists()&&file.isFile());
                // Delete the file to avoid conflicts with other tests
                file.delete();
            }
        } catch (IOException | InvalidNameException e) {
            e.printStackTrace();
        } 
    }
 
    @Test
    void getDocumentWithOneIssue() {
        //We are going to change the main.url to have a table with only one issue, this way we can test the webscrapper module
        properties.setProperty("issues.list.documents", "FU"); //FU = Fixed + First row (formerly called unconfirmed)
        properties.setProperty("url.main",
        "https://bugs.eclipse.org/bugs/report.cgi?x_axis_field=resolution&y_axis_field=bug_status&product=Platform&bug_status=CLOSED&resolution=FIXED&bug_id_type=any&chfield=bug_status&chfieldvalue=CLOSED&chfieldfrom=2001-10-15&chfieldto=2001-10-17&v1=&format=table&action=wrap");
        try {
            WebScraper.searchDocs(properties);
            String directory= properties.getProperty("eclipseissues.directorypath");
            String filestr = "FU.csv";
            File file = new File(directory, filestr);
            assertTrue(file.exists()&&file.isFile());
            try (BufferedReader reader = new BufferedReader(new FileReader(directory+File.separator+filestr))) {
                assertEquals(2, reader.lines().count()); //Header + Issue. 
            } catch (IOException e) {
                e.printStackTrace();
            }
        // Delete the file to avoid conflicts with other tests
            file.delete();
        } catch (IOException | InvalidNameException e) {
            e.printStackTrace();
        } 
    }

    @Test
    void getDocumentInvalidLenghtName() {
        properties.setProperty("issues.list.documents", "FUF"); 
        try {
            WebScraper.searchDocs(properties);
            assertThrows(InvalidNameException.class, () -> WebScraper.searchDocs(properties));
        } catch (IOException | InvalidNameException e) {
            assertEquals("Invalid name length in document FUF", e.getMessage());
        } 
    }

    @Test
    void getDocumentInvalidName() {
        properties.setProperty("issues.list.documents", "AA"); 
        try {
            WebScraper.searchDocs(properties);
            assertThrows(InvalidNameException.class, () -> WebScraper.searchDocs(properties));
        } catch (IOException | InvalidNameException e) {
            assertEquals("Invalid letter combination in the name of the document AA", e.getMessage());
        } 
    }

}
