package etsii.tfg.sttfbug;

import java.io.IOException;
import java.util.Comparator;
import java.util.Map;
import java.util.Scanner;

import etsii.tfg.sttfbug.issues.WebScraper;

public class Application {

    public static void main(String[] args) {
        System.out.println("Hello World!");
        Scanner scanner = new Scanner(System.in);
        while(true){
            Integer actionSelected = selectValue(scanner);
            System.out.println("Selected value: "+ actionSelected);
            switch(actionSelected){
                case 0: System.out.println("Stopping execution");
                        scanner.close();
                        return;
                case 1: try {
                        WebScraper.searchDocs();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } break;
                case 2: WebScraper.getListAllIssues(); break;
                default: throw new IllegalArgumentException("Unexpected value: " + actionSelected);
            }
        }
    }
    private static Integer selectValue(Scanner scanner){
        Map<Integer,String> validValues = Map.of(0,"-> Stop execution",
        1,"-> Obtain a .csv file with the list of issues",
        2,"-> Use the .csv files obtained in method 1 to get a filtered csv file with the needed information about the issues",
        3,"-> Use the filtered csv file to ... #WorkInProgress");
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
    private static void printValidValues(Map<Integer,String> map){
        map.entrySet().stream()
        .sorted(Comparator.comparing(Map.Entry::getKey))
        .forEach(entry -> System.out.println(entry.getKey()+" "+entry.getValue()));
    }
}
