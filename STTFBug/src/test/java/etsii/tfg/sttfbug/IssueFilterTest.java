package etsii.tfg.sttfbug;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

import etsii.tfg.sttfbug.issues.IssueFilter;
import etsii.tfg.sttfbug.issues.WebScraper;

class IssueFilterTest {
    private static Properties properties;
    
    @BeforeAll
    public static void setUp() {
        try (InputStream input = new FileInputStream("testconfig.properties")) {
            properties = new Properties();
            properties.load(input);
            WebScraper.searchDocs(properties);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //Review
    @Test
    void testNotNulls() {
        IssueFilter.getListAllIssues(properties);
        String filteredIssuesFilePath= properties.getProperty("filteredissue.path");
        File file = new File(filteredIssuesFilePath);
        assertTrue(file.exists()&&file.isFile());

        try (BufferedReader reader = new BufferedReader(new FileReader(filteredIssuesFilePath))) {
            System.out.println(reader.lines());
                assertEquals(2, reader.lines().count()); 
            } catch (IOException e) {
                e.printStackTrace();
            }

        // Delete the file to avoid conflicts with other tests
        file.delete();
    }
}
