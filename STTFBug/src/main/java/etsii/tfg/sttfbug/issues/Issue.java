package etsii.tfg.sttfbug.issues;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;

public class Issue {
    private String title;
    private String description;
    private Date startDate;
    private Date endDate;
    private String asignee;
     
    public String getTitle() {
        return title;
    }
    public String getDescription() {
        return description;
    }
    public Date getStartDate() {
        return startDate;
    }
    public Date getEndDate() {
        return endDate;
    }
    public String getAsignee() {
        return asignee;
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((title == null) ? 0 : title.hashCode());
        result = prime * result + ((description == null) ? 0 : description.hashCode());
        result = prime * result + ((startDate == null) ? 0 : startDate.hashCode());
        result = prime * result + ((endDate == null) ? 0 : endDate.hashCode());
        result = prime * result + ((asignee == null) ? 0 : asignee.hashCode());
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
        if (asignee == null) {
            if (other.asignee != null)
                return false;
        } else if (!asignee.equals(other.asignee))
            return false;
        return true;
    }
    public void setTitle(String title) {
        this.title = title;
    }
    public void setDescription(String description) {
        this.description = description;
    }
    public void setStartDate(String startDate) {
       this.startDate = convertDate(startDate);
    }
    public void setEndDate(String endDate) {
        this.endDate = convertDate(endDate);
    }
    public void setAsignee(String asignee) {
        this.asignee = asignee;
    }

    public Date convertDate(String strDate){
        Date date = null;
        SimpleDateFormat formatoFecha = new SimpleDateFormat("yyyy-MM-dd HH:mm z");
        try {
            date = formatoFecha.parse(strDate);
            LocalDateTime localDateTime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.of("America/New_York"));
            ZonedDateTime dateUTC = ZonedDateTime.of(localDateTime, ZoneId.of("UTC"));
            date= Date.from(dateUTC.toInstant());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return date;
    }
    @Override
    public String toString() {
        return "Issue [title=" + title + ", description=" + description + ", startDate=" + startDate + ", endDate="
                + endDate + ", asignee=" + asignee + "]";
    }
}
