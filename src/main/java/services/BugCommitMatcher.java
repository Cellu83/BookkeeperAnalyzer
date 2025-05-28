package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.*;

public class BugCommitMatcher {

    /**
     * Mappa ciascun ticket ID a una lista di commit che contengono l'ID nel messaggio del commit.
     *
     * @param bugTickets mappa {ticketId → resolutionDate}
     * @param git istanza Git già inizializzata sul repo
     * @return mappa {ticketId → lista di commit che lo menzionano}
     */
    public static Map<String, List<RevCommit>> mapTicketsToCommits(Map<String, String> bugTickets, Git git) throws IOException {
        Map<String, List<RevCommit>> result = new HashMap<>();
        try {
            Iterable<RevCommit> commits = git.log().call();
            for (RevCommit commit : commits) {
                String message = commit.getFullMessage().toLowerCase();
                for (String ticket : bugTickets.keySet()) {
                    if (message.contains(ticket.toLowerCase())) {
                        result.computeIfAbsent(ticket, k -> new ArrayList<>()).add(commit);
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Errore durante la scansione dei commit: " + e.getMessage(), e);
        }
        return result;
    }
}
