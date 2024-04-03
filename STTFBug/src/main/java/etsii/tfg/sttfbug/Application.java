package etsii.tfg.sttfbug;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Comparator;
import java.util.Map;
import java.util.Properties;
import java.util.Scanner;

import etsii.tfg.sttfbug.issues.IssueFilter;
import etsii.tfg.sttfbug.issues.WebScraper;
import etsii.tfg.sttfbug.predictor.Evaluator;
import etsii.tfg.sttfbug.predictor.Predictor;

public class Application {

    public static void main(String[] args) {
        Properties properties;
        try (InputStream input = new FileInputStream("config.properties")) {
            properties = new Properties();
            properties.load(input);
            Scanner scanner = new Scanner(System.in);
            while (true) {
                Integer actionSelected = selectValue(scanner);
                System.out.println("Selected value: " + actionSelected);
                switch (actionSelected) {
                    case 0:
                        System.out.println("Stopping execution");
                        scanner.close();
                        return;
                    case 1:
                        try {
                            WebScraper.searchDocs(properties);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        break;
                    case 2:
                        IssueFilter.getListAllIssues(properties);
                        break;
                    case 3:
                        Predictor.populateTrainingSet(properties);
                        break;
                    case 4:
                        Evaluator.evaluatePredictor(properties);
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected value: " + actionSelected);
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            System.out.println("config.properties file not found");
        }
    }

    private static Integer selectValue(Scanner scanner) {
        Map<Integer, String> validValues = Map.of(0, "-> Stop execution",
                1, "-> Obtain a .csv file with the list of issues",
                2, "-> Obtain a .csv file with the issues filtered using the .properties file conditions",
                3, "-> Populate the training and predict how long it will take to solve the given set of issues",
                4, "-> Evaluate the predictor using cross-validation");
        System.out.printf("Select one of the following values: %n");
        printValidValues(validValues);
        Integer actionInteger;
        while (true) {
            try {
                actionInteger = Integer.parseInt(scanner.nextLine());
                if (validValues.keySet().contains(actionInteger)) {
                    break;
                } else {
                    System.out.println("The indicated value is not valid, please choose beetween the following list:");
                    printValidValues(validValues);
                }
            } catch (NumberFormatException e) {
                System.out.println("Please, choose a valid value from the following list:");
                printValidValues(validValues);
            }
        }
        return actionInteger;

    }

    private static void printValidValues(Map<Integer, String> map) {
        map.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> System.out.println(entry.getKey() + " " + entry.getValue()));
    }
}
