package services;

import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Set;
import java.util.HashSet;

public class TicketInfo {
    private final String ticketID;
    private final String resolutionDate;
    private final String author;
    private final Set<RevCommit> associatedCommits = new HashSet<>();

    public TicketInfo(String ticketID, String resolutionDate, String author) {
        this.ticketID = ticketID;
        this.resolutionDate = resolutionDate;
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
    }
    public String getTicketID() {
        return ticketID;
    }


}
