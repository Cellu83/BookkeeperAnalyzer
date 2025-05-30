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
import java.util.logging.Logger;

public class HistoricalMetricsExtractor {

    private static final Logger LOGGER = Logger.getLogger(HistoricalMetricsExtractor.class.getName());

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

        logDebugPaths(absoluteFile, repoRoot);

        if (!absoluteFile.startsWith(repoRoot)) {
            LOGGER.warning(String.format("❌ Il file non è dentro la repo! File: %s", absoluteFile));
            LOGGER.warning(String.format("❌ Repo root: %s", repoRoot));
            throw new IOException("File path is outside repository: " + filePath);
        }

        Path relative = repoRoot.relativize(absoluteFile);
        String relativePath = relative.toString().replace(java.io.File.separatorChar, '/');

        LOGGER.info("Analyzing file: " + filePath);
        LOGGER.info("Relative path for Git: " + relativePath);

        Iterable<RevCommit> allCommits = git.log()
                .addPath(relativePath)
                .call();

        List<RevCommit> commits = new ArrayList<>();
        for (RevCommit commit : allCommits) {
            if ((commit.getCommitTime() * 1000L) < releaseDate.getTime()) {
                commits.add(commit);
            }
        }

        if (commits.isEmpty()) {
            LOGGER.warning(String.format("⚠️ Nessun commit trovato per il file (filtro manuale): %s prima della data %s", relativePath, releaseDate));
        }

        int modifications = 0;
        Set<String> uniqueAuthors = new HashSet<>();

        for (RevCommit commit : commits) {
            LOGGER.info(String.format("✔ Commit: %s - autore: %s", commit.getName(), commit.getAuthorIdent().getName()));

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

        LOGGER.info(String.format("Found %d commits for %s", commits.size(), relativePath));
        logSummary(relativePath, modifications, uniqueAuthors);

        return new HistoricalMetrics(modifications, uniqueAuthors);
    }

    private void logDebugPaths(Path absoluteFile, Path repoRoot) {
        LOGGER.info(String.format("🔍 DEBUG - filePath: %s", absoluteFile));
        LOGGER.info(String.format("🔍 DEBUG - repoRoot: %s", repoRoot));
    }

    private void logSummary(String relativePath, int modificationCount, Set<String> authors) {
        LOGGER.info(String.format("Modifications: %d, Authors: %d for %s", modificationCount, authors.size(), relativePath));
    }
}
