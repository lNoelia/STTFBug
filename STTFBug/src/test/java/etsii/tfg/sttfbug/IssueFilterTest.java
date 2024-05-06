package etsii.tfg.sttfbug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
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
            try {
                IssueFilter.getListAllIssues(properties);
            } catch (InvalidNameException e) {
                e.printStackTrace();
            }
        } catch (IOException | InvalidNameException e) {
            e.printStackTrace();
        }
    }

    @Test
    @Order(1)
    void testAllFieldsNotNulls() {
        String filteredIssuesFilePath = properties.getProperty("filteredissue.path");
        File file = new File(filteredIssuesFilePath);
        assertTrue(file.exists() && file.isFile());

        try (BufferedReader reader = new BufferedReader(new FileReader(filteredIssuesFilePath))) {
            assertEquals(187, reader.lines().count());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    @Order(2)
    void filterTest() {
        // Issue 25191 Should not be in the result list because the duration (in
        // minutes) is less than 5
        // Issue 67731 description is NULL, so it should not be in the result list
        String filteredIssuesFilePath = properties.getProperty("filteredissue.path");
        File file = new File(filteredIssuesFilePath);
        assertTrue(file.exists() && file.isFile());
        try (BufferedReader reader = new BufferedReader(new FileReader(filteredIssuesFilePath))) {
            List<String> ids = getIDsFromDocument(filteredIssuesFilePath);
            assertFalse(ids.contains("25191"));
            assertFalse(ids.contains("67731"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    @Order(3)
    void calculateStartAndEndDateTest() {
        String filteredIssuesFilePath = properties.getProperty("filteredissue.path");
        File file = new File(filteredIssuesFilePath);
        assertTrue(file.exists() && file.isFile());
        try (BufferedReader reader = new BufferedReader(new FileReader(filteredIssuesFilePath))) {
            reader.readLine(); // Skip the header
            String[] firstIssue = reader.readLine().split(",");
            assertEquals("8549",firstIssue[0].replace("\"", ""));
            assertEquals("2002-01-31 14:30 EST", firstIssue[1].replace("\"", ""));
            assertEquals("2002-04-17 17:18 EDT", firstIssue[2].replace("\"", ""));
            assertEquals("org.eclipse.core.resources 4 Unhandled exception caught in event loop", firstIssue[3].replace("\"", ""));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    @Order(4)
    void testUnfinishedIssues() {
        Properties propertiesTest = (Properties) properties.clone();
        propertiesTest.setProperty("issues.list.documents", "OU");
        propertiesTest.setProperty("filteredissue.path", propertiesTest.getProperty("filteredissue.path").replace(".csv", "OUtest.csv"));
        propertiesTest.setProperty("notnull.EndDate", "false");
        propertiesTest.setProperty("time.isvalid", "false");
        try {
            WebScraper.searchDocs(propertiesTest);
        } catch (IOException | InvalidNameException e) {
            e.printStackTrace();
        }
        try {
            IssueFilter.getListAllIssues(propertiesTest);
        } catch (InvalidNameException e) {
            e.printStackTrace();
        }
        String urlMain = propertiesTest.getProperty("url.main");
        Document doc = WebScraper.tryConnection(urlMain);
        Integer row = WebScraper.getRow('U');
        Integer column = WebScraper.getColumn('-');
        Element expectedNumberCell = doc.selectFirst("div div table table tbody").select("tr").get(row).select("td")
                .get(column).selectFirst("a");
        if (expectedNumberCell != null) {
            Long expectedNumber = Long.valueOf(expectedNumberCell.text()) + 1; // We add the header row
            try (BufferedReader reader = new BufferedReader(
                    new FileReader(propertiesTest.getProperty("filteredissue.path")))) {
                assertEquals(expectedNumber, reader.lines().count());
                File file = new File(propertiesTest.getProperty("filteredissue.path"));
                file.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    @Order(5)
    void testInvalidMaxIssues() {
        Properties propertiesTest = (Properties) properties.clone();
        propertiesTest.setProperty("issues.list.documents", "-U");
        propertiesTest.setProperty("filteredissue.path", propertiesTest.getProperty("filteredissue.path").replace(".csv", "-UInvalidMaxtest.csv"));
        propertiesTest.setProperty("notnull.EndDate", "false");
        propertiesTest.setProperty("time.isvalid", "false");
        propertiesTest.setProperty("max.issues", "-10");
        try {
            WebScraper.searchDocs(propertiesTest);
        } catch (IOException | InvalidNameException e) {
            e.printStackTrace();
        }
        try {
            IssueFilter.getListAllIssues(propertiesTest);
        } catch (InvalidNameException e) {
            e.printStackTrace();
        }
        String urlMain = propertiesTest.getProperty("url.main");
        Document doc = WebScraper.tryConnection(urlMain);
        Integer row = WebScraper.getRow('U');
        Integer column = WebScraper.getColumn('-');
        Element expectedNumberCell = doc.selectFirst("div div table table tbody").select("tr").get(row).select("td")
                .get(column).selectFirst("a");
        if (expectedNumberCell != null) {
            Long expectedNumber = Long.valueOf(expectedNumberCell.text()) + 1; // We add the header row
            try (BufferedReader reader = new BufferedReader(
                    new FileReader(propertiesTest.getProperty("filteredissue.path")))) {
                assertEquals(expectedNumber, reader.lines().count());
                File file = new File(propertiesTest.getProperty("filteredissue.path"));
                file.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    @Order(6)
    void testMaxValueIssues() {
        Properties propertiesTest = (Properties) properties.clone();
        propertiesTest.setProperty("issues.list.documents", "MR");
        propertiesTest.setProperty("filteredissue.path", propertiesTest.getProperty("filteredissue.path").replace(".csv", "MRMaxValtest.csv"));
        propertiesTest.setProperty("notnull.EndDate", "false");
        propertiesTest.setProperty("notnull.Description", "false");
        propertiesTest.setProperty("time.isvalid", "false");
        propertiesTest.setProperty("max.issues", "-1");
        try {
            WebScraper.searchDocs(propertiesTest);
        } catch (IOException | InvalidNameException e) {
            e.printStackTrace();
        }
        try {
            IssueFilter.getListAllIssues(propertiesTest);
        } catch (InvalidNameException e) {
            e.printStackTrace();
        }
        String urlMain = propertiesTest.getProperty("url.main");
        Document doc = WebScraper.tryConnection(urlMain);
        Integer row = WebScraper.getRow('R');
        Integer column = WebScraper.getColumn('M');
        Element expectedNumberCell = doc.selectFirst("div div table table tbody").select("tr").get(row).select("td")
                .get(column).selectFirst("a");
        if (expectedNumberCell != null) {
            Long expectedNumber = Long.valueOf(expectedNumberCell.text()) + 1; // We add the header row
            try (BufferedReader reader = new BufferedReader(
                    new FileReader(propertiesTest.getProperty("filteredissue.path")))) {
                assertEquals(expectedNumber, reader.lines().count());
                File file = new File(propertiesTest.getProperty("filteredissue.path"));
                file.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Test
    @Order(7)
    void testInvalidNameIssues() {
        Properties propertiesTest = (Properties) properties.clone();
        propertiesTest.setProperty("issues.list.documents", "AA");
        propertiesTest.setProperty("filteredissue.path", propertiesTest.getProperty("filteredissue.path").replace(".csv", "AAtest.csv"));
        try {
            IssueFilter.getListAllIssues(propertiesTest);
            assertThrows(InvalidNameException.class, () -> WebScraper.searchDocs(properties));  
        } catch (InvalidNameException e) {
            e.printStackTrace();
        }
        
    }

    @Test
    @Order(8)
    void testFilterNoValidIssues() {
        Properties propertiesTest = (Properties) properties.clone();
        propertiesTest.setProperty("issues.list.documents", "-U");
        propertiesTest.setProperty("filteredissue.path", propertiesTest.getProperty("filteredissue.path").replace(".csv", "-Utest.csv"));
        try {
            WebScraper.searchDocs(propertiesTest);
        } catch (IOException | InvalidNameException e) {
            e.printStackTrace();
        }
        try {
            IssueFilter.getListAllIssues(propertiesTest);
        } catch (InvalidNameException e) {
            e.printStackTrace();
        }

        try (BufferedReader reader = new BufferedReader(
            new FileReader(propertiesTest.getProperty("filteredissue.path")))) {
            assertEquals(1, reader.lines().count());
            File file = new File(propertiesTest.getProperty("filteredissue.path"));
            file.delete();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public List<String> getIDsFromDocument(String path){
        List<String> ids = null;
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            ids = reader.lines().skip(1).map(line -> line.split(",")[0]).toList();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return ids;
    }
}
