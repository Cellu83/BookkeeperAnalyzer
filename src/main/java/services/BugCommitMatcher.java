package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.*;

public class BugCommitMatcher {

    private BugCommitMatcher() {
        // Utility class
    }

    /**
     * Mappa ciascun ticket ID a una lista di commit che contengono l'ID nel messaggio del commit.
     *
     * @param bugTickets mappa {ticketId → resolutionDate}
     * @param git istanza Git già inizializzata sul repo
     * @return mappa {ticketId → lista di commit che lo menzionano}
     */
    public static Map<String, List<RevCommit>> mapTicketsToCommits(Map<String, String> bugTickets, Git git) throws IOException {
        Map<String, List<RevCommit>> result = new HashMap<>();
        Set<String> matchedTickets = new HashSet<>();
        try {
            Iterable<RevCommit> commits = git.log().call();
            for (RevCommit commit : commits) {
                String message = commit.getFullMessage().toLowerCase();
                for (String ticket : bugTickets.keySet()) {
                    String normalizedTicket = ticket.toLowerCase();
                    String altFormat = normalizedTicket.replace("-", "_");

                    boolean matched = false;

                    if (message.contains(normalizedTicket) || message.contains(altFormat)) {
                        result.computeIfAbsent(ticket, k -> new ArrayList<>()).add(commit);
                        matchedTickets.add(ticket);
                        matched = true;
                    }

                    if (!matched && !matchedTickets.contains(ticket)) {
                        try {
                            Date commitDate = commit.getAuthorIdent().getWhen();
                            String ticketResolutionDate = bugTickets.get(ticket);

                            if (ticketResolutionDate != null) {
                                Date resolutionDate = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(ticketResolutionDate);
                                long diffMillis = Math.abs(commitDate.getTime() - resolutionDate.getTime());
                                long diffDays = diffMillis / (1000 * 60 * 60 * 24);

                                if (diffDays <= 2) {
                                    result.computeIfAbsent(ticket, k -> new ArrayList<>()).add(commit);
                                    matchedTickets.add(ticket);
                                }
                            }
                        } catch (Exception e) {
                            // Ignored parsing exception
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Errore durante la scansione dei commit: " + e.getMessage(), e);
        }
        return result;
    }
}
