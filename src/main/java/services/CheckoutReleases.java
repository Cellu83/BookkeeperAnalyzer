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

public class CheckoutReleases {

    public static void main(String[] args) throws IOException, GitAPIException, ParseException {
        String repoPath = "/Users/colaf/Documents/ISW2/bookkeeper/bookkeeper_ISW2/.git"; // Modifica con il tuo path corretto
        String csvPath = "BOOKKEEPERVersionInfo.csv"; // Assicurati che sia nel path giusto

        Git git = new Git(new FileRepositoryBuilder()
                .setGitDir(new File(repoPath))
                .readEnvironment()
                .findGitDir()
                .build());

        BufferedReader br = new BufferedReader(new FileReader(csvPath));
        String line;
        br.readLine(); // salta intestazione

        while ((line = br.readLine()) != null) {
            String[] values = line.split(",");
            String version = values[2];
            String dateStr = values[3];
            if (dateStr.contains("T")) {
                dateStr = dateStr.split("T")[0];
            }
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            Date releaseDate = formatter.parse(dateStr);

            // Reset HEAD alla branch completa prima di analizzare ogni release
            git.checkout().setName("master").call();

            Iterable<RevCommit> commits = git.log().call();
            RevCommit bestCommit = null;

            for (RevCommit commit : commits) {
                Date commitDate = commit.getAuthorIdent().getWhen();
                System.out.println("Commit: " + commit.getName() + " | Date: " + commitDate + " | Before release: " + commitDate.before(releaseDate));
                if (commitDate.before(releaseDate)) {
                    if (bestCommit == null || commitDate.after(bestCommit.getAuthorIdent().getWhen())) {
                        bestCommit = commit;
                    }
                }
            }

            if (bestCommit != null) {
                long daysDiff = (releaseDate.getTime() - bestCommit.getAuthorIdent().getWhen().getTime()) / (1000 * 60 * 60 * 24);
                System.out.println("Checking out version: " + version +
                        " at commit: " + bestCommit.getName() +
                        " (commit date: " + bestCommit.getAuthorIdent().getWhen() +
                        ", release date: " + releaseDate +
                        ", diff: " + daysDiff + " days)");
                git.checkout().setName(bestCommit.getName()).call();
            } else {
                System.out.println("No commit found before " + releaseDate + " for version: " + version);
            }
        }

        git.close();
        br.close();
    }
}
