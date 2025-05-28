package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

public final class CheckoutReleases {

    protected static final Logger LOGGER = Logger.getLogger(CheckoutReleases.class.getName());

    public static void main(String[] args) throws IOException, GitAPIException, ParseException {
        // Il percorso del repository può ora essere sovrascritto con -Drepo.path=tuo/percorso/.git
        final String repoPath = System.getProperty("repo.path", "/Users/colaf/Documents/ISW2/bookkeeper/bookkeeper_ISW2/.git");
        String csvPath = "BOOKKEEPERVersionInfo.csv"; // Assicurati che sia nel path giusto

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Git git = new Git(builder.setGitDir(new File(repoPath))
                                 .readEnvironment()
                                 .findGitDir()
                                 .build());

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line;
            br.readLine(); // salta intestazione

            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                String version = values[2];
                String dateStr = values[3];
                if (dateStr.contains("T")) {
                    dateStr = dateStr.split("T")[0];
                }
                Date releaseDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);

                // Ripristina il branch 'master' prima di effettuare il checkout di ogni release
                git.checkout().setName("master").call();

                RevCommit bestCommit = getBestCommitBeforeDate(git, releaseDate);

                if (bestCommit != null) {
                    long daysDiff = (releaseDate.getTime() - bestCommit.getAuthorIdent().getWhen().getTime()) / (1000 * 60 * 60 * 24);
                    String message = String.format("Checking out version: %s at commit: %s (commit date: %s, release date: %s, diff: %d days)",
                            version, bestCommit.getName(), bestCommit.getAuthorIdent().getWhen(), releaseDate, daysDiff);
                    LOGGER.info(message);
                    git.checkout().setName(bestCommit.getName()).call();
                } else {
                    LOGGER.warning("No commit found before " + releaseDate + " for version: " + version);
                    continue;
                }
            }
        }
        git.close();
    }

    private static RevCommit getBestCommitBeforeDate(Git git, Date releaseDate) throws GitAPIException {
        Iterable<RevCommit> commits = git.log().call();
        RevCommit bestCommit = null;
        for (RevCommit commit : commits) {
            Date commitDate = commit.getAuthorIdent().getWhen();
            LOGGER.fine("Commit: " + commit.getName() + " | Date: " + commitDate + " | Before release: " + commitDate.before(releaseDate));
            if (commitDate.before(releaseDate)) {
                if (bestCommit == null || commitDate.after(bestCommit.getAuthorIdent().getWhen())) {
                    bestCommit = commit;
                }
            }
        }
        return bestCommit;
    }
}
