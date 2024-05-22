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
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import etsii.tfg.sttfbug.predictor.Predictor;

class PredictorTest {

    private static Properties properties;
    
    @BeforeAll
    /*
     * To avoid time consuming task such as the filtering of issues, we are going to use a file already filtered
     * to mock the results from the IssueFilter module. (This file is generated in the IssueFilterTest)
     */
    public static void setUp() {
        try (InputStream input = new FileInputStream("testconfig.properties")) {
            properties = new Properties();
            properties.load(input);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    @Test
    @Order(1)
    void testPredictor() {
        Predictor.populateTrainingSet(properties);
        String resultFilePath = properties.getProperty("result.predictions.file");
        File file = new File(resultFilePath);
        assertTrue(file.exists() && file.isFile());
        try (BufferedReader reader = new BufferedReader(new FileReader(resultFilePath))) {
            reader.readLine(); // Skip the header
            String topPredictionForFirstIssue = reader.readLine();
            //Given that issue 8549 is on the training set, it should be the top prediction for itself
            assertTrue(topPredictionForFirstIssue.contains("Issue ID: 8549"));
            assertEquals(28, reader.lines().count()); // 6 lines per issue to be predicted - 2 that we have already read
        } catch (IOException e) {
            e.printStackTrace();
        }
        file.delete();
    }

    @Test
    @Order(2)
    void testFileDoesntExist() {
        properties.setProperty("filteredissue.path", "nonexistentfile.csv");
        Predictor.populateTrainingSet(properties);
        String resultFilePath = properties.getProperty("result.predictions.file");
        File file = new File(resultFilePath);
        assertTrue(file.exists() && file.isFile());
        try (BufferedReader reader = new BufferedReader(new FileReader(resultFilePath))) {
            reader.readLine(); // Skip the header
            //Since the file of filtered issues doesnt exist, the training set is empty,
            //so the result file should contain only the lines saying that time predicted is 0.0 hours
            assertEquals(14, reader.lines().count());
        } catch (IOException e) {
            e.printStackTrace();
        }
        file.delete();
    }
}
