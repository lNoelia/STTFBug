package etsii.tfg.sttfbug.issues;

public enum IssueType {
    TRAINING,//Issues that have all the data that we need to train the model
    PREDICT //Issues that may not have all the data, we use them to predict the time to fix the issue
}
