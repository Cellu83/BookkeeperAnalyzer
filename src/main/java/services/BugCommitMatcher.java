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
        Map<String, TicketInfo> ticketToCommitsMap = new HashMap<>();
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
                ticketInfo.getAffectedMethods(repoPath);
                String resolutionDate = ticketInfo.getResolutionDate();
                String jiraAuthor = ticketInfo.getAuthor();
                Set<String> methodSignatures = ticketInfo.getAffectedMethods(repoPath);

                boolean isDirectMatch = matchesTicket(commitMessage, ticketId);
                if (isDirectMatch) {
                }
                boolean isHeuristicMatch = !matchedTickets.contains(ticketId) &&
                    isHeuristicMatch(commit, resolutionDate, jiraAuthor, methodSignatures);
                if (isHeuristicMatch) {
                } else {
                }

                if (isDirectMatch || isHeuristicMatch) {
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
    private static boolean isHeuristicMatch(RevCommit commit, String ticketResolutionDate, String jiraAuthor, Set<String> methodSignatures) {
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

            if (methodSignatures.stream().noneMatch(sig -> commit.getFullMessage().contains(sig))) {
                return false;
            }

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
    // --- MAIN METHOD FOR COMMAND LINE USAGE ---
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: BugCommitMatcher <projectKey> <repoPath>");
            return;
        }

        String projectKey = args[0];
        String repoPath = args[1];

        try (Git git = Git.open(new File(repoPath))) {
            Map<String, TicketInfo> jiraTickets = JiraTicketFetcher.fetchFixedBugTickets(projectKey);
            Map<String, TicketInfo> matchedTickets = new HashMap<>();

            for (RevCommit commit : git.log().all().call()) {
                String message = commit.getFullMessage().toLowerCase();
                for (Map.Entry<String, TicketInfo> entry : jiraTickets.entrySet()) {
                    String ticketId = entry.getKey();
                    if (matchesTicket(message, ticketId)) {
                        matchedTickets
                            .computeIfAbsent(ticketId, k -> new TicketInfo(
                                ticketId,
                                entry.getValue().getResolutionDate(),
                                entry.getValue().getAuthor(),
                                entry.getValue().getAffectedMethods(repoPath)))
                            .addAssociatedCommit(commit);
                    }
                }
            }

            for (Map.Entry<String, TicketInfo> entry : matchedTickets.entrySet()) {
                System.out.println("Ticket: " + entry.getKey());
                for (RevCommit commit : entry.getValue().getAssociatedCommits()) {
                    System.out.println("  Commit: " + commit.getName() + " - " + commit.getShortMessage());
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
