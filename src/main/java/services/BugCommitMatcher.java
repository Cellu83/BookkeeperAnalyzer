package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.io.File;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.StaticJavaParser;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.patch.FileHeader;
import org.eclipse.jgit.patch.HunkHeader;
import org.eclipse.jgit.util.io.DisabledOutputStream;
/** import java.util.logging.Level;
 import java.util.logging.Logger; */

/**
 * Classe utility per associare i ticket di bug ai relativi commit.
 */
public class BugCommitMatcher {

    private static final int MAX_HEURISTIC_DATE_DIFF_DAYS = 2;
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    private BugCommitMatcher() {
        // Costruttore privato per evitare l'istanziazione della classe utility
    }


    //Prende tutti i commit da git e passa a processcommit e crea una mappa
    public static Map<String, TicketInfo> mapTicketsToCommits(Map<String, TicketInfo> bugTickets, Git git, String repoPath) throws IOException, GitAPIException {
        Map<String, TicketInfo> ticketToCommitsMap = new HashMap<>(bugTickets);
        Set<String> matchedTickets = new HashSet<>();

        try {
            Iterable<RevCommit> commits = git.log().all().call();
            processCommits(commits, bugTickets, ticketToCommitsMap, matchedTickets, repoPath);
        } catch (GitAPIException e) {
            throw new GitAPIException("Errore durante la lettura del log dei commit Git", e) {};
        }

        return ticketToCommitsMap;
    }
    // per ogni commit del repo, controlla se è associabile a uno dei ticket.
    private static void processCommits(
            Iterable<RevCommit> commits,
            Map<String, TicketInfo> bugTickets,
            Map<String, TicketInfo> ticketToCommitsMap,
            Set<String> matchedTickets,
            String repoPath) {

        for (RevCommit commit : commits) {

            String commitMessage = commit.getFullMessage().toLowerCase();

            for (Map.Entry<String, TicketInfo> entry : bugTickets.entrySet()) {
                String ticketId = entry.getKey();
                TicketInfo ticketInfo = entry.getValue();
                String resolutionDate = ticketInfo.getResolutionDate();
                String jiraAuthor = ticketInfo.getAuthor();

                boolean isDirectMatch = matchesTicket(commitMessage, ticketId);
                if (isDirectMatch) {
                    // Associazione diretta trovata (ID ticket nel messaggio del commit)
                }

                boolean isHeuristicMatch = !matchedTickets.contains(ticketId) &&
                        isHeuristicMatch(commit, resolutionDate, jiraAuthor);
                if (isHeuristicMatch) {
                    // Associazione euristica trovata (autore e data compatibili)
                } else {
                    // Nessuna corrispondenza trovata
                }

                if (isDirectMatch || isHeuristicMatch) {
                    System.out.println("Associazione trovata: " + ticketId + " <-- " + commit.getName());
                    addCommitToTicket(ticketToCommitsMap, matchedTickets, ticketId, commit);
                }
            }
        }
    }

    // aggiorna la mappa ticket → commit e segna il ticket come già associato (matchedTickets)
    private static void addCommitToTicket(
            Map<String, TicketInfo> ticketToCommitsMap,
            Set<String> matchedTickets,
            String ticketId,
            RevCommit commit) {

        if (ticketToCommitsMap.containsKey(ticketId)) {
            ticketToCommitsMap.get(ticketId).addAssociatedCommit(commit);
        }
        matchedTickets.add(ticketId);
    }

    // verifica se un commit menziona il ticket nel messaggio.
    private static boolean matchesTicket(String commitMessage, String ticketId) {
        String normalizedTicketId = ticketId.toLowerCase();
        String alternativeFormat = normalizedTicketId.replace("-", "_");
        return commitMessage.contains(normalizedTicketId) || commitMessage.contains(alternativeFormat);
    }

    // Match euristico: commit entro ±2 giorni dalla risoluzione, autore corrispondente e riferimento al metodo nel messaggio
    private static boolean isHeuristicMatch(RevCommit commit, String ticketResolutionDate, String jiraAuthor) {
        if (ticketResolutionDate == null || ticketResolutionDate.isEmpty()) {
            return false;
        }

        try {
            Date commitDate = commit.getAuthorIdent().getWhen();
            Date resolutionDate = parseDate(ticketResolutionDate);

            long diffDays = calculateDayDifference(commitDate, resolutionDate);
            if (diffDays > MAX_HEURISTIC_DATE_DIFF_DAYS) return false;

            String commitAuthor = commit.getAuthorIdent().getName();
            if (!commitAuthor.equalsIgnoreCase(jiraAuthor)) return false;

            return true;
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
    public static void main(String[] args) throws Exception {
        String repoPath = "/Users/colaf/Documents/ISW2/zookeeper/zookeeper/.git";
        String projectKey = "ZOOKEEPER";

        System.out.println("Fetching tickets from JIRA...");
        Map<String, TicketInfo> bugTickets = JiraTicketFetcher.fetchFixedBugTickets(projectKey);
        for (Map.Entry<String, TicketInfo> entry : bugTickets.entrySet()) {
            String ticketId = entry.getKey();
            TicketInfo info = entry.getValue();
            System.out.println("Ticket ID: " + ticketId);
            System.out.println("  Author: " + info.getAuthor());
            System.out.println("  Resolution Date: " + info.getResolutionDate());
        }

        System.out.println("Matching commits to tickets...");
        // Tenta di associare i commit ai ticket, includendo anche le associazioni dirette (ID ticket nel messaggio del commit)
        Map<String, TicketInfo> commitToTicketMap = BugCommitMatcher.mapTicketsToCommits(
            bugTickets,
            Git.open(new File(repoPath)),
            repoPath
        );

        System.out.println("\n=== Risultati BugCommitMatcher ===");
        for (Map.Entry<String, TicketInfo> entry : commitToTicketMap.entrySet()) {
            System.out.println("Ticket: " + entry.getKey());
            System.out.println("  Associated Commits:");
            for (RevCommit commit : entry.getValue().getAssociatedCommits()) {
                System.out.println("    - " + commit.getName() + ": " + commit.getShortMessage());
            }
        }

        System.out.println("\n=== Riepilogo Finale: Ticket con Commit Associati ===");
        for (Map.Entry<String, TicketInfo> entry : commitToTicketMap.entrySet()) {
            String ticketId = entry.getKey();
            TicketInfo ticket = entry.getValue();
            System.out.println("Ticket: " + ticketId);
            for (RevCommit commit : ticket.getAssociatedCommits()) {
                System.out.println("  - Commit: " + commit.getName() + " | " + commit.getShortMessage());
            }
        }
    }
}
