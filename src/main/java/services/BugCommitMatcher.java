package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
/** import java.util.logging.Level;
import java.util.logging.Logger; */

/**
 * Classe utility per associare i ticket di bug ai relativi commit.
 */
public class BugCommitMatcher {

    private static final int MAX_HEURISTIC_DATE_DIFF_DAYS = 2;
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd";

    private BugCommitMatcher() {
        // Costruttore privato per evitare l'istanziazione della classe utility
    }

    //Prende tutti i commit da git e passa a processcommit e crea una mappa
    public static Map<String, List<RevCommit>> mapTicketsToCommits(Map<String, String> bugTickets, Git git) throws IOException, GitAPIException {
        Map<String, List<RevCommit>> ticketToCommitsMap = new HashMap<>();
        Set<String> matchedTickets = new HashSet<>();

        try {
            Iterable<RevCommit> commits = git.log().all().call();
            processCommits(commits, bugTickets, ticketToCommitsMap, matchedTickets);
        } catch (GitAPIException e) {
            throw new GitAPIException("Errore durante la lettura del log dei commit Git", e) {};
        }

        return ticketToCommitsMap;
    }
    // per ogni commit del repo, controlla se è associabile a uno dei ticket.
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

                boolean isDirectMatch = matchesTicket(commitMessage, ticketId);
                boolean isHeuristicMatch = !matchedTickets.contains(ticketId) && isHeuristicMatch(commit, resolutionDate);

                if (isDirectMatch || isHeuristicMatch) {
                    addCommitToTicket(ticketToCommitsMap, matchedTickets, ticketId, commit);
                }
            }
        }
    }

    // aggiorna la mappa ticket → commit e segna il ticket come già associato (matchedTickets)
    private static void addCommitToTicket(
            Map<String, List<RevCommit>> ticketToCommitsMap,
            Set<String> matchedTickets,
            String ticketId,
            RevCommit commit) {

        ticketToCommitsMap.computeIfAbsent(ticketId, k -> new ArrayList<>()).add(commit);
        matchedTickets.add(ticketId);
    }

        // verifica se un commit menziona il ticket nel messaggio.
    private static boolean matchesTicket(String commitMessage, String ticketId) {
        String normalizedTicketId = ticketId.toLowerCase();
        String alternativeFormat = normalizedTicketId.replace("-", "_");
        return commitMessage.contains(normalizedTicketId) || commitMessage.contains(alternativeFormat);
    }

    // se non è stato trovato un match diretto, controlla se il commit è vicino (±2 giorni) alla resolutionDate del ticket.
    private static boolean isHeuristicMatch(RevCommit commit, String ticketResolutionDate) {
        if (ticketResolutionDate == null || ticketResolutionDate.isEmpty()) {
            return false;
        }

        try {
            Date commitDate = commit.getAuthorIdent().getWhen();
            Date resolutionDate = parseDate(ticketResolutionDate);

            long diffDays = calculateDayDifference(commitDate, resolutionDate);
            return diffDays <= MAX_HEURISTIC_DATE_DIFF_DAYS;
        } catch (ParseException _) {
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
