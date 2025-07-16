package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import services.JavaFileMethodExtractor;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

public class SZZAnalyzer {

    private final String repoPath;

    public SZZAnalyzer(String repoPath) {
        this.repoPath = repoPath;
    }

    public Map<String, Set<String>> analyzeBuggyMethods(Map<String, List<RevCommit>> ticketFixCommits) throws IOException, GitAPIException {
        Map<String, Set<String>> buggyCommitsToMethods = new HashMap<>();

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(new File(repoPath + "/.git"))
                .readEnvironment()
                .findGitDir()
                .build();
             Git git = new Git(repository)) {

            for (Map.Entry<String, List<RevCommit>> entry : ticketFixCommits.entrySet()) {
                String ticketId = entry.getKey();
                List<RevCommit> fixCommits = entry.getValue();

                for (RevCommit fixCommit : fixCommits) {
                    RevCommit parent = fixCommit.getParentCount() > 0 ? fixCommit.getParent(0) : null;
                    if (parent == null) continue;

                    List<DiffEntry> diffs = getDiffs(repository, git, parent, fixCommit);
                    for (DiffEntry diff : diffs) {
                        if (!diff.getNewPath().endsWith(".java")) continue;

                        Set<String> modifiedMethods = JavaFileMethodExtractor.extractModifiedMethodsFromDiff(repository, diff);
                        for (String method : modifiedMethods) {
                            Iterable<RevCommit> history = git.log().add(parent.getId()).call();
                            for (RevCommit pastCommit : history) {
                                Set<String> pastMethods = JavaFileMethodExtractor.extractModifiedMethodsFromCommit(repoPath, pastCommit);
                                if (pastMethods.contains(method)) {
                                    buggyCommitsToMethods.computeIfAbsent(pastCommit.getName(), k -> new HashSet<>()).add(method);
                                }
                            }
                        }
                    }
                }
            }
        }

        return buggyCommitsToMethods;
    }

    private List<DiffEntry> getDiffs(Repository repository, Git git, RevCommit oldCommit, RevCommit newCommit) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            if (oldCommit.getTree() == null || newCommit.getTree() == null) return List.of();
            ObjectId oldTree = oldCommit.getTree().getId();
            try (ObjectReader reader = repository.newObjectReader()) {
                oldTreeIter.reset(reader, oldTree);
            }

            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            ObjectId newTree = newCommit.getTree().getId();
            try (ObjectReader reader = repository.newObjectReader()) {
                newTreeIter.reset(reader, newTree);
            }

            List<DiffEntry> diffs;
            try (ByteArrayOutputStream out = new ByteArrayOutputStream();
                 DiffFormatter diffFormatter = new DiffFormatter(out)) {
                diffFormatter.setRepository(repository);
                diffs = diffFormatter.scan(oldTreeIter, newTreeIter);
            }

            return diffs;
        }
    }

    public static Map<String, Set<String>> analyze(Map<String, List<RevCommit>> ticketFixCommits, Git git, String repoPath) throws IOException, GitAPIException {
        SZZAnalyzer analyzer = new SZZAnalyzer(repoPath);
        return analyzer.analyzeBuggyMethods(ticketFixCommits);
    }

    public static void main(String[] args) {
        String repoPath = "/Users/colaf/Documents/ISW2/zookeeper/zookeeper"; // Modifica con il tuo path
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            try (Repository repository = builder.setGitDir(new File(repoPath + "/.git"))
                    .readEnvironment()
                    .findGitDir()
                    .build();
                 Git git = new Git(repository)) {

                Map<String, List<RevCommit>> ticketFixCommits = new HashMap<>();

                // Inserisci qui un esempio di commit ID reale dal tuo repo
                String exampleCommitId = "ed70d2edcb78a7e30ae60170ea986fb85e862b4b";
                ObjectId commitId = repository.resolve(exampleCommitId);
                RevCommit commit;
                try (RevWalk walk = new RevWalk(repository)) {
                    commit = walk.parseCommit(commitId);
                    ticketFixCommits.put("TEST-1", List.of(commit));
                }
                ticketFixCommits.put("TEST-1", List.of(commit));

                Map<String, Set<String>> result = SZZAnalyzer.analyze(ticketFixCommits, git, repoPath);

                for (Map.Entry<String, Set<String>> entry : result.entrySet()) {
                    System.out.println("Commit: " + entry.getKey());
                    for (String method : entry.getValue()) {
                        System.out.println("  Buggy method: " + method);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
