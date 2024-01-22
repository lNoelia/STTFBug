package etsii.tfg.sttfbug.issues;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class HistoryRow {
    private static final AtomicInteger count = new AtomicInteger(0); 

    private final String id;
    private final String who;
    private final ZonedDateTime when;
    private final String what;
    private final String removed;
    private final String added;

    public HistoryRow(List<String> cells, String issueID) {
        this.id = issueID+"-"+count.incrementAndGet();
        this.who = cells.get(0);
        this.when = convertDate(cells.get(1));
        this.what = cells.get(2);
        this.removed = cells.get(3);
        this.added = cells.get(4);
    }

    public HistoryRow(List<String> cells, String issueID, String who, ZonedDateTime when) {
        this.id = issueID+"-"+count.incrementAndGet();
        this.who = who;
        this.when = when; //In this case, we dont need to convert the Date, cause we know it has already been converted See: WebScrapper.java (calculateEndDate)
        this.what = cells.get(0);
        this.removed = cells.get(1);
        this.added = cells.get(2);
    }

    public String getWho() {
        return who;
    }
    public ZonedDateTime getWhen() {
        return when;
    }
    public String getWhat() {
        return what;
    }
    public String getRemoved() {
        return removed;
    }
    public String getAdded() {
        return added;
    }
    public String getId() {
        return id;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        result = prime * result + ((who == null) ? 0 : who.hashCode());
        result = prime * result + ((when == null) ? 0 : when.hashCode());
        result = prime * result + ((what == null) ? 0 : what.hashCode());
        result = prime * result + ((removed == null) ? 0 : removed.hashCode());
        result = prime * result + ((added == null) ? 0 : added.hashCode());
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
        HistoryRow other = (HistoryRow) obj;
        if (id == null) {
            if (other.id != null)
                return false;
        } else if (!id.equals(other.id))
            return false;
        if (who == null) {
            if (other.who != null)
                return false;
        } else if (!who.equals(other.who))
            return false;
        if (when == null) {
            if (other.when != null)
                return false;
        } else if (!when.equals(other.when))
            return false;
        if (what == null) {
            if (other.what != null)
                return false;
        } else if (!what.equals(other.what))
            return false;
        if (removed == null) {
            if (other.removed != null)
                return false;
        } else if (!removed.equals(other.removed))
            return false;
        if (added == null) {
            if (other.added != null)
                return false;
        } else if (!added.equals(other.added))
            return false;
        return true;
    }

    public ZonedDateTime convertDate(String strDate){
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");
        LocalDateTime date = LocalDateTime.parse(strDate, formatter);
        ZoneId zoneNY = ZoneId.of("America/New_York");
        return ZonedDateTime.of(date, zoneNY);
    }
    
}