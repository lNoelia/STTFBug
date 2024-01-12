package etsii.tfg.sttfbug;

import java.io.IOException;
import java.util.Map;
import java.util.Scanner;

import etsii.tfg.sttfbug.issues.WebScraper;

public class Application {

    public static void main(String[] args) {
        System.out.println("Hello World!");
        Integer actionSelected = selectValue();
        System.out.println("Selected value: "+ actionSelected);
        switch(actionSelected){
            case 0: try {
                    WebScraper.searchDocs();
                } catch (IOException e) {
                    e.printStackTrace();
                } break;
            case 1: try {
                    WebScraper.searchDocs();
                } catch (IOException e) {
                    e.printStackTrace();
                } break;
            default: throw new IllegalArgumentException("Unexpected value: " + actionSelected);
        }
    }
    private static Integer selectValue(){
        Map<Integer,String> validValues = Map.of(0,"-> Obtain the documents with the list of issues",
        1,"Obtain de issues of the previous documents",2,"-> This is a preview text");
        Scanner in = new Scanner(System.in);
        System.out.printf("Select one of the following values: %n");
        printValidValues(validValues);
        Integer actionInteger;
        while (true) {
            try {
                actionInteger = Integer.parseInt(in.nextLine());
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
        in.close();
        return actionInteger;
        
    }
    private static void printValidValues(Map<Integer,String> map){
        map.entrySet().forEach(entry -> System.out.println(entry.getKey()+" "+entry.getValue()));
    }
    
}
