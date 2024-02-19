# Simple Time To Fix Bug (STTFBug)
## Overview 
  ToDo
## Features 
  ToDo
## Usage 
  ToDo
## Properties configuration
The config.properties file is used to config the various parameters given to the methods already described.
- analyzer.stopwords: words and symbols separated by commas that will not be taken into account when calculating similarity
- lucene.directorypath: path where the Lucene directory will be stored (used in the training set to predict time to fix)
- filteredissue.path: path of the file with the filtered issues (or where you want to store it). Has to be a .csv file
- predict.issue.list: IDs of the issues that we want to predict, separated by comma
- issues.neighbor: number of neighbors to be considered when giving the k-closest neighbors.
- issues.filter: list of documents to be downloaded. The documents have this format: XY , where X is the initial of the Resolution and Y is the initial of the Status.
#### Values of resolution and status
- Resolution values: -(Resolution not set) , F(Fixed), I(Invalid), W(Wontfix), D(Duplicated), 4(worksforme), M(Moved), N(Not_eclipse), T(Total)
- Status values: U(Unconfirmed), N(New), A(Assigned), O(reOpen), R(Resolved), V(Verified), C(Closed), T(Total)
#### Non editable properties
- url.main: URL with table of issues by Resolution and status values.
- url.issue: URL to issue details
- url.issue.history: URL to the history of an issue
#### Sample config.properties file: 
ToDo
