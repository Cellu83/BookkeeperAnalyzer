package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ProportionAnalyzer {

    private static final String REPO_PATH = "/Users/colaf/Documents/ISW2/zookeeper/zookeeper/.git";

    public static void main(String[] args) {
        try {
            Git git = Git.open(new File(REPO_PATH));
            Map<String, TicketInfo> ticketMap = BugCommitMatcher.mapTicketsToCommits(
                    JiraTicketFetcher.fetchFixedBugTickets("ZOOKEEPER"), git, REPO_PATH);

            Map<String, Integer> methodTotalCommits = new HashMap<>();
            Map<String, Integer> methodBuggyCommits = new HashMap<>();

            for (TicketInfo ticket : ticketMap.values()) {
                Set<RevCommit> associatedCommits = ticket.getAssociatedCommits();
                for (RevCommit commit : associatedCommits) {
                    RevCommit[] parents = commit.getParents();
                    if (parents.length == 0) continue;
                    RevCommit parent = parents[0];

                    Set<String> modifiedMethods = GitUtils.getModifiedMethodsBetweenCommits(REPO_PATH, parent, commit);

                    for (String method : modifiedMethods) {
                        methodTotalCommits.put(method, methodTotalCommits.getOrDefault(method, 0) + 1);
                        methodBuggyCommits.put(method, methodBuggyCommits.getOrDefault(method, 0) + 1);
                    }
                }
            }

            // Analizza tutti i commit per metodo
            List<RevCommit> allCommits = new ArrayList<>();
            git.log().call().forEach(allCommits::add);
            for (RevCommit commit : allCommits) {
                RevCommit[] parents = commit.getParents();
                if (parents.length == 0) continue;
                RevCommit parent = parents[0];

                Set<String> modifiedMethods = GitUtils.getModifiedMethodsBetweenCommits(REPO_PATH, parent, commit);
                for (String method : modifiedMethods) {
                    methodTotalCommits.put(method, methodTotalCommits.getOrDefault(method, 0) + 1);
                }
            }

            Map<String, Double> methodBugProportionMap = new HashMap<>();
            for (String method : methodTotalCommits.keySet()) {
                int buggy = methodBuggyCommits.getOrDefault(method, 0);
                int total = methodTotalCommits.get(method);
                double proportion = total == 0 ? 0.0 : (double) buggy / total;
                methodBugProportionMap.put(method, proportion);
            }

            System.out.println("\n=== Metodo -> Bug Proportion ===");
            for (Map.Entry<String, Double> entry : methodBugProportionMap.entrySet()) {
                System.out.printf("%s -> %.2f%n", entry.getKey(), entry.getValue());
            }

            // Stampa tutti i metodi considerati buggy secondo il metodo della proportion
            System.out.println("\n=== Metodi buggy secondo il metodo della Proportion ===");
            Set<String> buggyMethods = getBuggyMethodsByProportion(ticketMap, git, REPO_PATH);
            for (String method : buggyMethods) {
                System.out.println(method);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Restituisce l'insieme dei metodi considerati buggy secondo il metodo della Proportion.
     */
    public static Set<String> getBuggyMethodsByProportion(Map<String, TicketInfo> ticketMap, Git git, String repoPath) throws Exception {
        Set<String> buggyMethods = new HashSet<>();

        // Parametro P iniziale (stimato)
        final double initialP = 0.5;

        for (TicketInfo ticket : ticketMap.values()) {
            System.out.println( ticket.toString());

            Date ov = ticket.getCreationDate();
            Date fv = ticket.getFixDate();

            long fvTime = fv.getTime();
            long ovTime = ov.getTime();
            long ivTimeEstimate = (long) (fvTime - initialP * (fvTime - ovTime));
            Date iv = new Date(ivTimeEstimate);
            System.out.println("Ticket: " + ticket.getTicketID() + " | ov: " + ov + " | fv: " + fv + " | iv: " + iv);

            RevCommit ivCommit = GitUtils.findNearestCommitBeforeDate(Git.wrap(git.getRepository()), iv);
            RevCommit fvCommit = GitUtils.findNearestCommitBeforeDate(Git.wrap(git.getRepository()), fv);




            Set<String> modifiedMethods = GitUtils.getModifiedMethodsBetweenCommits(repoPath, ivCommit, fvCommit);
            buggyMethods.addAll(modifiedMethods);
            System.out.println("Metodi modificati tra iv e fv: " + modifiedMethods);
        }

        return buggyMethods;
    }
}



