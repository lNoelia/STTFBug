package etsii.tfg.sttfbug.issues;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class Issue {
    private Integer id;
    private String title;
    private String description;
    private ZonedDateTime startDate;
    private ZonedDateTime endDate;
    private String assignee;

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z");
    
    public Integer getId() {
        return id;
    }
    public String getTitle() {
        return title;
    }
    public String getDescription() {
        return description;
    }
    public ZonedDateTime getStartDate() {
        return startDate;
    }
    public String getStartDatetoString() {
        return startDate.format(formatter);
    }
    public ZonedDateTime getEndDate() {
        return endDate;
    }
    public String getEndDatetoString() {
        return endDate.format(formatter);
    }
    public String getAssignee() {
        return assignee;
    }
    public void setTitle(String title) {
        this.title = cleanText(title);
    }
    public void setDescription(String description) {
        this.description = cleanText(description);
    }
    public void setStartDateStr(String startDate) {
       this.startDate = convertDate(startDate);
    }
    public void setStartDate(ZonedDateTime startDate) {
        this.startDate = startDate;
    }
    public void setEndDate(ZonedDateTime endDate) {
        this.endDate = endDate;
    }
    public void setAssignee(String asignee) {
        this.assignee = asignee;
    }
    public void setId(Integer id) {
        this.id = id;
    }

    public ZonedDateTime convertDate(String strDate){
        LocalDateTime date = LocalDateTime.parse(strDate, formatter);
        ZoneId zoneNY = ZoneId.of("America/New_York");
        return ZonedDateTime.of(date, zoneNY);
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((title == null) ? 0 : title.hashCode());
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((startDate == null) ? 0 : startDate.hashCode());
        result = prime * result + ((endDate == null) ? 0 : endDate.hashCode());
        result = prime * result + ((assignee == null) ? 0 : assignee.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Issue other = (Issue) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (title == null) {
            if (other.title != null)
                return false;
        } else if (!title.equals(other.title))
            return false;
        if (description == null) {
            if (other.description != null)
                return false;
        } else if (!description.equals(other.description))
            return false;
        if (startDate == null) {
            if (other.startDate != null)
                return false;
        } else if (!startDate.equals(other.startDate))
            return false;
        if (endDate == null) {
            if (other.endDate != null)
                return false;
        } else if (!endDate.equals(other.endDate))
            return false;
        if (assignee == null) {
            if (other.assignee != null)
                return false;
        } else if (!assignee.equals(other.assignee))
            return false;
        return true;
    }
    @Override
    public String toString() {
        return "Issue [id=" + id + ", title=" + title + ", description=" + description + ", startDate=" + (startDate != null ? startDate.format(formatter) : null)
        + ", endDate=" + (endDate != null ? endDate.format(formatter) : null) + ", assignee=" + assignee + "]";
    }
    
    private static String cleanText(String text) {
        return text.replaceAll("\\r?\\n", " ");
    }

    public Long getTimeSpent(){
        return Duration.between(startDate, endDate).toMinutes();
    }
}
