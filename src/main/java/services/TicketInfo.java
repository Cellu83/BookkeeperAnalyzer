package services;

import org.eclipse.jgit.revwalk.RevCommit;

import java.util.Set;
import java.util.HashSet;

public class TicketInfo {
    private final String ticketID;
    private final String resolutionDate;
    private final String author;
    private final Set<String> affectedMethods;
    private final Set<RevCommit> associatedCommits = new HashSet<>();

    public TicketInfo(String ticketID, String resolutionDate, String author, Set<String> affectedMethods) {
        this.ticketID = ticketID;
        this.resolutionDate = resolutionDate;
        this.author = author;
        this.affectedMethods = affectedMethods != null ? affectedMethods : new HashSet<>();
    }

    public String getResolutionDate() {
        return resolutionDate;
    }

    public String getAuthor() {
        return author;
    }

    public void addAffectedMethod(String methodSignature) {
        this.affectedMethods.add(methodSignature);
    }

    public Set<String> getAffectedMethods(String repoPath) {
        return affectedMethods;
    }

    public Set<String> getBuggyMethods() {
        return affectedMethods;
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
