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

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import etsii.tfg.sttfbug.issues.InvalidNameException;
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
        } catch (IOException | InvalidNameException e) {
            e.printStackTrace();
        }
    }

    @Test
    @Order(1)
    void testAllFieldsNotNulls() {
        try {
            IssueFilter.getListAllIssues(properties);
        } catch (InvalidNameException e) {
            e.printStackTrace();
        }
        String filteredIssuesFilePath = properties.getProperty("filteredissue.path");
        File file = new File(filteredIssuesFilePath);
        assertTrue(file.exists() && file.isFile());

        try (BufferedReader reader = new BufferedReader(new FileReader(filteredIssuesFilePath))) {
            assertEquals(178, reader.lines().count());
        } catch (IOException e) {
            e.printStackTrace();
        }
        // Delete the file to avoid conflicts with other tests
        file.delete();
    }

    @Test
    @Order(2)
    void testUnfinishedIssues() {
        properties.setProperty("issues.list.documents", "-U");
        properties.setProperty("notnull.EndDate", "false");
        properties.setProperty("time.isvalid", "false");
        try {
            WebScraper.searchDocs(properties);
        } catch (IOException | InvalidNameException e) {
            e.printStackTrace();
        }
        try {
            IssueFilter.getListAllIssues(properties);
        } catch (InvalidNameException e) {
            e.printStackTrace();
        }
        String urlMain = properties.getProperty("url.main");
        Document doc = WebScraper.tryConnection(urlMain);
        Integer row = WebScraper.getRow('U');
        Integer column = WebScraper.getColumn('-');
        Element expectedNumberCell = doc.selectFirst("div div table table tbody").select("tr").get(row).select("td")
                .get(column).selectFirst("a");
        if (expectedNumberCell != null) {
            Long expectedNumber = Long.valueOf(expectedNumberCell.text()) + 1; // We add the header row
            try (BufferedReader reader = new BufferedReader(
                    new FileReader(properties.getProperty("filteredissue.path")))) {
                assertEquals(expectedNumber, reader.lines().count());
                File file = new File(properties.getProperty("filteredissue.path"));
                file.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        
    }

    // @Test
    // @Order(3)
    // void calculateEndDateTest() {
    //     // Issue 25191 Should not be in the result list because the duration (in
    //     // minutes) is less than 5
    //     // Issue
    //     try {
    //         IssueFilter.getListAllIssues(properties);
    //     } catch (InvalidNameException e) {
    //         e.printStackTrace();
    //     }
    //     String filteredIssuesFilePath = properties.getProperty("filteredissue.path");
    //     File file = new File(filteredIssuesFilePath);
    //     assertTrue(file.exists() && file.isFile());
    //     try (BufferedReader reader = new BufferedReader(new FileReader(filteredIssuesFilePath))) {
    //         assertEquals(178, reader.lines().count());
    //     } catch (IOException e) {
    //         e.printStackTrace();
    //     }
    //     // Delete the file to avoid conflicts with other tests
    //     file.delete();
    // }
}
