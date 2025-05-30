package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Classe utility per associare i ticket di bug ai relativi commit.
 */
public class BugCommitMatcher {

    private static final Logger LOGGER = Logger.getLogger(BugCommitMatcher.class.getName());
    private static final int MAX_HEURISTIC_DATE_DIFF_DAYS = 2;
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd";

    private BugCommitMatcher() {
        // Costruttore privato per evitare l'istanziazione della classe utility
    }

    /**
     * Mappa ciascun ticket ID a una lista di commit che contengono l'ID nel messaggio del commit.
     *
     * @param bugTickets mappa {ticketId → resolutionDate}
     * @param git istanza Git già inizializzata sul repo
     * @return mappa {ticketId → lista di commit che lo menzionano}
     * @throws IOException in caso di errore durante l'accesso al repository
     * @throws GitAPIException in caso di errore durante le operazioni Git
     */
    public static Map<String, List<RevCommit>> mapTicketsToCommits(Map<String, String> bugTickets, Git git) throws IOException, GitAPIException {
        Map<String, List<RevCommit>> ticketToCommitsMap = new HashMap<>();
        Set<String> matchedTickets = new HashSet<>();

        try {
            Iterable<RevCommit> commits = git.log().all().call();
            processCommits(commits, bugTickets, ticketToCommitsMap, matchedTickets);
        } catch (GitAPIException e) {
            throw e; // Rilancio l'eccezione originale mantenendo il tipo specifico
        }

        return ticketToCommitsMap;
    }

    private static void processCommits(
            Iterable<RevCommit> commits,
            Map<String, String> bugTickets,
            Map<String, List<RevCommit>> ticketToCommitsMap,
            Set<String> matchedTickets) {

        for (RevCommit commit : commits) {
            String commitMessage = commit.getFullMessage().toLowerCase();

            for (Map.Entry<String, String> entry : bugTickets.entrySet()) {
                String ticketId = entry.getKey();
                String resolutionDate = entry.getValue();

                if (matchesTicket(commitMessage, ticketId)) {
                    addCommitToTicket(ticketToCommitsMap, matchedTickets, ticketId, commit);
                } else if (!matchedTickets.contains(ticketId) && isHeuristicMatch(commit, resolutionDate)) {
                    addCommitToTicket(ticketToCommitsMap, matchedTickets, ticketId, commit);
                }
            }
        }
    }

    private static void addCommitToTicket(
            Map<String, List<RevCommit>> ticketToCommitsMap,
            Set<String> matchedTickets,
            String ticketId,
            RevCommit commit) {

        ticketToCommitsMap.computeIfAbsent(ticketId, k -> new ArrayList<>()).add(commit);
        matchedTickets.add(ticketId);
    }

    private static boolean matchesTicket(String commitMessage, String ticketId) {
        String normalizedTicketId = ticketId.toLowerCase();
        String alternativeFormat = normalizedTicketId.replace("-", "_");
        return commitMessage.contains(normalizedTicketId) || commitMessage.contains(alternativeFormat);
    }

    private static boolean isHeuristicMatch(RevCommit commit, String ticketResolutionDate) {
        if (ticketResolutionDate == null || ticketResolutionDate.isEmpty()) {
            return false;
        }

        try {
            Date commitDate = commit.getAuthorIdent().getWhen();
            Date resolutionDate = parseDate(ticketResolutionDate);

            long diffDays = calculateDayDifference(commitDate, resolutionDate);
            return diffDays <= MAX_HEURISTIC_DATE_DIFF_DAYS;
        } catch (ParseException ignored) {
            return false;
        }
    }

    private static Date parseDate(String dateString) throws ParseException {
        return new SimpleDateFormat(DATE_FORMAT_PATTERN).parse(dateString);
    }

    private static long calculateDayDifference(Date date1, Date date2) {
        long diffMillis = Math.abs(date1.getTime() - date2.getTime());
        return diffMillis / (1000 * 60 * 60 * 24);
    }
}
