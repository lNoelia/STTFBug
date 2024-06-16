# Simple Time To Fix Bug (STTFBug)

## Overview 

  Simple Time to Fix Bug is an application that obtains issues and bugs from Bugzilla's projects (in this case we use as an example Eclipse Platform), and predicts the time to fix of a given list of issues.

  ### How do we predict the time to fix an issue?

  First, we obtain the total list of "FIXED" issues on the repository, then we filter them with all the boolean parameters on the configuration file set to true and when we obtain the file with the filtered issues, we use the predictor module.
  The predictor module uses Lucene to compare the similarity between the issue given to predict and the issues that we have obtained previously. In this case, we use the classic similarity approach, that implements a vector base approach(TF-IDF) and returns the k most similar issues and the mean of the time to fix, assuming that issues with a similiar title and description will have a similar time to fix.

## Features 
 
  This application contains the following features: 
  * Web Scraping methods such as obtaining the indicated list of issues from the repository.
  * Filter issues, where given a set of constraints selected on the configuration file
  * Predict issues, where given a list of IDs of already created issues on the repository, we return the time to fix for each one of them
  * Evaluate predictor, where given a list of filtered issues, it will evaluate the prediction method using cross-validation with 3, 5 and 7 folds.

## Usage 

  To use this app you will need to have Java Development Kit (in this case we have used the 17.0.9 version) and Maven (we used the 3.9.6 version). 
  Steps to run the program:
  * Step into the STTFBug folder 
  * mvn compile
  * mvn exec:java

## Tests

The test cases are run with: mvn test in the STTFBUG folder.
When running the test with said command, it will run the test of each module and give you the results of the tests.

## Properties configuration

The config.properties file is used to config the various parameters given to the methods already described. An example of this file can be found in the repository.

- analyzer.stopwords: words and symbols separated by commas that will not be taken into account when calculating similarity
- lucene.directorypath: path where the Lucene directory will be stored (used in the training set to predict time to fix)
- filteredissue.path: path of the file with the filtered issues (or where you want to store it). CSV File.
- eclipseissues.directorypath: path to the directory where the eclipse issue csv will be downloaded.
- predict.issue.file: File with the list of issues to be predicted. Mandatory to at least have ID.
- result.predictions.file: File where the results of the predictions will be stored.
- percentage.within: Margin of error allowed on time predictions. Will only be used on statistics. 
- folds: list of numbers of folds to be made when using evaluator module.
- issues.neighbor: number of neighbors to be considered when giving the k-closest neighbors.
- issues.max: max number of issues to be filtered. In case the issue files have more lines than this value, those issues won't be added to the "filtered issue" file. Choose -1 to obtain all the issues on the file.
- issues.list.documents: list of documents to be downloaded. The documents have this format: XY , where X is the initial of the Resolution(Columns) and Y is the initial of the Status(Rows).
- notnull.Title: true or false. If this value is true, it means that the title of the issue can NOT be null.
- notnull.Description: true or false. If this value is true, it means that the description of the issue can NOT be null.
- notnull.Id: true or false. If this value is true, it means that the ID of the issue can NOT be null. true value recommended
- notnull.StartDate: true or false. If this value is true, it means that the StartDate of the issue can NOT be null.
- notnull.EndDate: true or false. If this value is true, it means that the EndDate of the issue can NOT be null. 
- time.isvalid: true or false. If the value is true, it will check that the EndDate is before the StartDate and that the time between them is at least 5 minutes.
- max.clause.count: This value is the maximum number of clauses that lucene can handle. If the query is a longer text, you might need to increase this value.

### Non editable properties

If you edit this properties to adapt the application to use another Bugzilla repository, the application may need some modification depending on the implementation of Bugzilla.
 
- url.main: URL with table of issues by Resolution and status values.
- url.issue: URL to issue details.
- url.issue.history: URL to the history of an issue.
- issue.fields: fields of the class Issue.

### Values of resolution and status

- Resolution values: -(Resolution not set) , F(Fixed), I(Invalid), W(Wontfix), D(Duplicated), 4(worksforme), M(Moved), N(Not_eclipse), T(Total)
- Status values: U(Unconfirmed), N(New), A(Assigned), O(reOpen), R(Resolved), V(Verified), C(Closed), T(Total)
