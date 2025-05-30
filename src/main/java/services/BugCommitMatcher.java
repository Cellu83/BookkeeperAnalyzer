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
                for (Map.Entry<String, String> entry : bugTickets.entrySet()) {
                    String ticket = entry.getKey();
                    if (matchesTicket(message, ticket)) {
                        result.computeIfAbsent(ticket, k -> new ArrayList<>()).add(commit);
                        matchedTickets.add(ticket);
                    } else {
                        tryHeuristicMatch(result, matchedTickets, bugTickets, commit, ticket, entry);
                    }
                }
            }
        } catch (Exception e) {
            throw new IOException("Errore durante la scansione dei commit: " + e.getMessage(), e);
        }
        return result;
    }

    private static boolean matchesTicket(String message, String ticket) {
        String normalizedTicket = ticket.toLowerCase();
        String altFormat = normalizedTicket.replace("-", "_");
        return message.contains(normalizedTicket) || message.contains(altFormat);
    }

    private static boolean tryHeuristicMatch(Map<String, List<RevCommit>> result, Set<String> matchedTickets, Map<String, String> bugTickets, RevCommit commit, String ticket, Map.Entry<String, String> entry) {
        if (!matchedTickets.contains(ticket)) {
            String ticketResolutionDate = entry.getValue();
            if (ticketResolutionDate != null && isHeuristicMatch(commit, ticketResolutionDate)) {
                result.computeIfAbsent(ticket, k -> new ArrayList<>()).add(commit);
                matchedTickets.add(ticket);
                return true;
            }
        }
        return false;
    }

    private static boolean isHeuristicMatch(RevCommit commit, String ticketResolutionDate) {
        try {
            Date commitDate = commit.getAuthorIdent().getWhen();
            Date resolutionDate = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(ticketResolutionDate);
            long diffMillis = Math.abs(commitDate.getTime() - resolutionDate.getTime());
            long diffDays = diffMillis / (1000 * 60 * 60 * 24);
            return diffDays <= 2;
        } catch (Exception ignored) {
            return false;
        }
    }
}
