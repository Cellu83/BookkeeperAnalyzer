package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.filter.CommitTimeRevFilter;
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
        String relativePath = repo.getWorkTree().toPath().relativize(filePath).toString().replace("\\", "/");

        Iterable<RevCommit> commits = git.log()
                .addPath(relativePath)
                .setRevFilter(CommitTimeRevFilter.before(releaseDate))
                .call();

        int modificationCount = 0;
        Set<String> uniqueAuthors = new HashSet<>();

        for (RevCommit commit : commits) {
            if (commit.getParentCount() == 0) continue;
            RevCommit parent = commit.getParent(0);

            try (DiffFormatter diffFormatter = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                diffFormatter.setRepository(repo);
                diffFormatter.setDetectRenames(true);
                List<DiffEntry> diffs = diffFormatter.scan(parent.getTree(), commit.getTree());

                for (DiffEntry diff : diffs) {
                    if (diff.getNewPath().equals(relativePath)) {
                        modificationCount++;
                        uniqueAuthors.add(commit.getAuthorIdent().getName());
                    }
                }
            }
        }

        return new HistoricalMetrics(modificationCount, uniqueAuthors);
    }
}
