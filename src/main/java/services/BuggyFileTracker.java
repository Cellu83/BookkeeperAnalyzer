package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.util.*;

public class BuggyFileTracker {

    private BuggyFileTracker() {
        // Utility class
    }

    /**
     * Costruisce una mappa {filePath → lista di timestamp dei commit buggy}.
     * Utile per valutare se un file è buggy prima di una determinata release.
     * Per ogni commit di fix associato a un ticket, associa i file modificati
     * come buggy alla release precedente (tramite timestamp: il commitTime del parent).
     *
     * @param ticketCommits mappa {ticketID → lista di commit di fix}
     * @param git repository
     * @return mappa {filePath → lista di timestamp (millisecondi) in cui era buggy (release precedente al fix)}
     */
    public static Map<String, List<Long>> collectBuggyFileHistory(Map<String, List<RevCommit>> ticketCommits, Git git) throws IOException {
        Map<String, List<Long>> buggyFileHistory = new HashMap<>();

        for (Map.Entry<String, List<RevCommit>> entry : ticketCommits.entrySet()) {
            for (RevCommit commit : entry.getValue()) {
                // Skip merge commits (no parent 0)
                if (commit.getParentCount() == 0) continue;
                RevCommit parent = commit.getParent(0);
                try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                    df.setRepository(git.getRepository());
                    df.setDetectRenames(true);
                    List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
                    for (DiffEntry diff : diffs) {
                        String path = diff.getNewPath();
                        if (!path.endsWith(".java")) continue;
                        // Usa il timestamp del parent (release precedente al fix)
                        long buggyTimestamp = parent.getCommitTime() * 1000L;
                        buggyFileHistory.computeIfAbsent(path, k -> new ArrayList<>()).add(buggyTimestamp);
                    }
                }
            }
        }
        // Opzionale: ordina le liste di timestamp per file
        for (List<Long> tsList : buggyFileHistory.values()) {
            Collections.sort(tsList);
        }
        return buggyFileHistory;
    }
}
