package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.RawTextComparator;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JavaFileMethodExtractor {

    public static Set<String> extractModifiedMethodsFromCommit(String repoPath, RevCommit commit) {
        Set<String> modifiedMethods = new HashSet<>();
        try {
            FileRepositoryBuilder builder = new FileRepositoryBuilder();
            Repository repository = builder.setGitDir(new File(repoPath + "/.git"))
                                           .readEnvironment()
                                           .findGitDir()
                                           .build();
            try (Git git = new Git(repository)) {
                ObjectReader reader = repository.newObjectReader();
                CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                CanonicalTreeParser newTreeIter = new CanonicalTreeParser();

                if (commit.getParentCount() == 0) {
                    return modifiedMethods; // No parent to compare
                }

                RevCommit parent = git.getRepository().parseCommit(commit.getParent(0).getId());
                oldTreeIter.reset(reader, parent.getTree().getId());
                newTreeIter.reset(reader, commit.getTree().getId());

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                DiffFormatter diffFormatter = new DiffFormatter(out);
                diffFormatter.setRepository(repository);
                diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
                diffFormatter.setDetectRenames(true);

                List<DiffEntry> diffs = diffFormatter.scan(oldTreeIter, newTreeIter);
                for (DiffEntry entry : diffs) {
                    if (!entry.getNewPath().endsWith(".java")) continue;

                    try {
                        ObjectId blobId = entry.getNewId().toObjectId();
                        try (InputStream input = repository.open(blobId).openStream()) {
                            JavaParser parser = new JavaParser();
                            CompilationUnit cu = parser.parse(input).getResult().orElse(null);
                            if (cu != null) {
                                cu.findAll(MethodDeclaration.class).forEach(method ->
                                        modifiedMethods.add(method.getDeclarationAsString(false, false, true))
                                );
                            }
                        }
                    } catch (Exception e) {
                        // Skip parse error
                    }
                }
                diffFormatter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return modifiedMethods;
    }
    public static void main(String[] args) throws Exception {
        String repoPath = "/Users/colaf/Documents/ISW2/zookeeper/zookeeper"; // ← cambia con il tuo path locale
        String commitId = "bccc654e09d9e85fe3495487b9619173a2c54151";

        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        try (Repository repository = builder.setGitDir(new File(repoPath + "/.git"))
                .readEnvironment()
                .findGitDir()
                .build()) {
            try (Git git = new Git(repository)) {
                ObjectId commitObjectId = repository.resolve(commitId);
                RevCommit commit = repository.parseCommit(commitObjectId);
                Set<String> methods = JavaFileMethodExtractor.extractModifiedMethodsFromCommit(repoPath, commit);
                System.out.println("Metodi modificati nel commit " + commitId + ":");
                methods.forEach(System.out::println);
            }
        }
    }
    public static Set<String> extractModifiedMethodsFromDiff(Repository repository, DiffEntry diff) {
        Set<String> modifiedMethods = new HashSet<>();
        try (DiffFormatter diffFormatter = new DiffFormatter(new ByteArrayOutputStream())) {
            diffFormatter.setRepository(repository);
            diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
            diffFormatter.setDetectRenames(true);

            if (!diff.getNewPath().endsWith(".java")) {
                return modifiedMethods;
            }

            ObjectId blobId = diff.getNewId().toObjectId();
            try (InputStream input = repository.open(blobId).openStream()) {
                JavaParser parser = new JavaParser();
                CompilationUnit cu = parser.parse(input).getResult().orElse(null);
                if (cu != null) {
                    cu.findAll(MethodDeclaration.class).forEach(method ->
                            modifiedMethods.add(method.getDeclarationAsString(false, false, true))
                    );
                }
            } catch (Exception e) {
                // Skip parse error
            }
        }
        return modifiedMethods;
    }

    public static List<DiffEntry> getDiffsBetweenCommits(Repository repository, RevCommit parent, RevCommit child) {
        try {
            CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
            CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
            try (ObjectReader reader = repository.newObjectReader()) {
                oldTreeIter.reset(reader, parent.getTree().getId());
                newTreeIter.reset(reader, child.getTree().getId());
            }

            try (DiffFormatter diffFormatter = new DiffFormatter(new ByteArrayOutputStream())) {
                diffFormatter.setRepository(repository);
                diffFormatter.setDiffComparator(RawTextComparator.DEFAULT);
                diffFormatter.setDetectRenames(true);
                return diffFormatter.scan(oldTreeIter, newTreeIter);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return List.of();
        }
    }
}



