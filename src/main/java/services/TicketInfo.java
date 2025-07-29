package services;

import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Set;
import java.util.HashSet;
import java.util.Date;

public class TicketInfo {
    private final String ticketID;
    private final String resolutionDate;
    private final String creationDate;
    private final String author;
    private final Set<RevCommit> associatedCommits = new HashSet<>();

    public TicketInfo(String ticketID, String resolutionDate, String creationDate, String author) {
        this.ticketID = ticketID;
        this.resolutionDate = resolutionDate;
        this.creationDate = creationDate;
        this.author = author;
    }

    public String getResolutionDate() {
        return resolutionDate;
    }

    public String getAuthor() {
        return author;
    }

    public Set<RevCommit> getAssociatedCommits() {
        return associatedCommits;
    }

    public void addAssociatedCommit(RevCommit commit) {
        this.associatedCommits.add(commit);
        System.out.println("Aggiunto commit associato al ticket " + associatedCommits + ": " + commit.getName());
    }
    public String getTicketID() {
        return ticketID;
    }

    public Date getCreationDate() {
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").parse(creationDate);
        } catch (Exception e) {
            return null;
        }
    }

    public Date getFixDate() {
        try {
            return new java.text.SimpleDateFormat("yyyy-MM-dd").parse(resolutionDate);
        } catch (Exception e) {
            return null;
        }
    }
    public String toString() {
        return "TicketInfo{" +
                "ticketID='" + ticketID + '\'' +
                ", resolutionDate='" + resolutionDate + '\'' +
                ", creationDate='" + creationDate + '\'' +
                ", author='" + author + '\'' +
                ", associatedCommits=" + associatedCommits +
                '}';
    }
}
