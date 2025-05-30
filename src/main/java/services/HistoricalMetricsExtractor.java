package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

public class HistoricalMetricsExtractor {

    public static class HistoricalMetrics {
        private int modifications;
        private final Set<String> authors;

        public HistoricalMetrics(int modifications, Set<String> authors) {
            this.modifications = modifications;
            this.authors = authors;
        }

        public int getModifications() {
            return modifications;
        }

        public Set<String> getAuthors() {
            return authors;
        }
    }

    public HistoricalMetrics extract(Path filePath, Date releaseDate, Git git) throws IOException, org.eclipse.jgit.api.errors.GitAPIException {
        Repository repo = git.getRepository();

        Path repoRoot = repo.getWorkTree().toPath().toRealPath();
        Path absoluteFile = filePath.toRealPath();

        if (!absoluteFile.startsWith(repoRoot)) {
            throw new IOException("File path is outside repository: " + filePath);
        }

        Path relative = repoRoot.relativize(absoluteFile);
        String relativePath = relative.toString().replace(java.io.File.separatorChar, '/');

        Iterable<RevCommit> allCommits = git.log()
                .addPath(relativePath)
                .call();

        List<RevCommit> commits = new ArrayList<>();
        for (RevCommit commit : allCommits) {
            if ((commit.getCommitTime() * 1000L) < releaseDate.getTime()) {
                commits.add(commit);
            }
        }

        int modifications = 0;
        Set<String> uniqueAuthors = new HashSet<>();

        for (RevCommit commit : commits) {
            RevCommit parent = null;
            if (commit.getParentCount() > 0) {
                parent = commit.getParent(0);
            }

            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(repo);
                diffFormatter.setDetectRenames(true);
                List<DiffEntry> diffs = (parent != null)
                        ? diffFormatter.scan(parent.getTree(), commit.getTree())
                        : diffFormatter.scan(null, commit.getTree());

                for (DiffEntry diff : diffs) {
                    if (diff.getNewPath().equals(relativePath)) {
                        modifications++;
                        uniqueAuthors.add(commit.getAuthorIdent().getName());
                    }
                }
            }
        }

        return new HistoricalMetrics(modifications, uniqueAuthors);
    }
}
