package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import services.GitUtils;
import services.TicketInfo;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class SZZAnalyzer {

    private static final String REPO_PATH = "/Users/colaf/Documents/ISW2/zookeeper/zookeeper/.git";
    private static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSSZ";

    public static void main(String[] args) {
        try {
            Git git = Git.open(new File(REPO_PATH));
            Map<String, TicketInfo> ticketMap = BugCommitMatcher.mapTicketsToCommits(
                    JiraTicketFetcher.fetchFixedBugTickets("ZOOKEEPER"), git, REPO_PATH);

            Map<String, List<String>> affectedCommitsMap = new HashMap<>();
            Map<String, String> methodFixCimMap = new HashMap<>();

            for (TicketInfo ticket : ticketMap.values()) {
                String resolutionDateStr = ticket.getResolutionDate();
                if (resolutionDateStr == null || resolutionDateStr.isEmpty()) continue;

                Date resolutionDate = parseDate(resolutionDateStr);
                List<RevCommit> associatedCommits = new ArrayList<>(ticket.getAssociatedCommits());

                for (RevCommit commit : associatedCommits) {
                    RevCommit[] parents = commit.getParents();
                    if (parents.length == 0) continue;

                    RevCommit parent = parents[0];

                    List<String> modifiedMethods = new ArrayList<>(GitUtils.getModifiedMethodsBetweenCommits(REPO_PATH, parent.getName(), commit.getName()));
                    for (String method : modifiedMethods) {
                        RevCommit introducingCommit = GitUtils.findMethodIntroductionCommit(REPO_PATH, method, parent.getName());
                        if (introducingCommit != null) {
                            String commitId = introducingCommit.getName();
                            affectedCommitsMap.putIfAbsent(commitId, new ArrayList<>());
                            affectedCommitsMap.get(commitId).add(method);

                            // Nuova mappa: metodo -> data fix - data CIM
                            String methodKey = method;
                            String fixDate = formatDate(commit.getAuthorIdent().getWhen());
                            String cimDate = formatDate(introducingCommit.getAuthorIdent().getWhen());
                            methodFixCimMap.put(methodKey, fixDate + " - " + cimDate);
                        }
                    }
                }
            }

            System.out.println("\n=== Mappa dei commit affected con metodi ===");
            for (Map.Entry<String, List<String>> entry : affectedCommitsMap.entrySet()) {
                System.out.println("Affected commit: " + entry.getKey());
                for (String method : entry.getValue()) {
                    System.out.println("  - Metodo: " + method);
                }
            }

            System.out.println("\n=== Mappa metodo -> data fix - data CIM ===");
            for (Map.Entry<String, String> entry : methodFixCimMap.entrySet()) {
                System.out.println(entry.getKey() + " -> " + entry.getValue());
            }

        } catch (IOException | GitAPIException | ParseException e) {
            e.printStackTrace();
        }
    }

    private static Date parseDate(String dateString) throws ParseException {
        return new SimpleDateFormat(DATE_FORMAT_PATTERN).parse(dateString);
    }

    private static String formatDate(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd").format(date);
    }
}