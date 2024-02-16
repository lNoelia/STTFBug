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
#### Non editable properties
- issues.url: Auxiliar url for web scrapping. (Should be left at it is) 

#### Sample config.properties file: 
ToDo
