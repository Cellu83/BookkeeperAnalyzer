package services;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.util.*;

public class GitUtils {

    public static Set<String> getModifiedMethodsBetweenCommits(String repoPath, RevCommit oldCommit, RevCommit newCommit) {
        Set<String> modifiedMethods = new HashSet<>();
        try (Repository repository = org.eclipse.jgit.api.Git.open(new java.io.File(repoPath)).getRepository()) {
            ObjectId oldCommitId = oldCommit.getId();
            ObjectId newCommitId = newCommit.getId();

            try (ObjectReader reader = repository.newObjectReader()) {
                CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                oldTreeIter.reset(reader, new RevWalk(repository).parseCommit(oldCommitId).getTree());
                CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                newTreeIter.reset(reader, new RevWalk(repository).parseCommit(newCommitId).getTree());

                List<DiffEntry> diffs = new org.eclipse.jgit.api.Git(repository).diff()
                        .setOldTree(oldTreeIter)
                        .setNewTree(newTreeIter)
                        .call();

                JavaParser parser = new JavaParser();
                for (DiffEntry diff : diffs) {
                    if (diff.getChangeType() == DiffEntry.ChangeType.MODIFY && diff.getNewPath().endsWith(".java")) {
                        // Recupera i contenuti dei file dai due commit
                        String oldContent = getFileContentFromCommit(repository, oldCommit, diff.getOldPath());
                        String newContent = getFileContentFromCommit(repository, newCommit, diff.getNewPath());

                        if (oldContent == null || newContent == null) continue;

                        CompilationUnit oldCu = parser.parse(oldContent).getResult().orElse(null);
                        CompilationUnit newCu = parser.parse(newContent).getResult().orElse(null);

                        if (oldCu == null || newCu == null) continue;

                        Set<String> oldMethods = new HashSet<>();
                        oldCu.findAll(MethodDeclaration.class)
                                .forEach(m -> oldMethods.add(
                                        m.getDeclarationAsString() + m.getBody().map(Object::toString).orElse("")
                                ));

                        Set<String> newMethods = new HashSet<>();
                        newCu.findAll(MethodDeclaration.class)
                                .forEach(m -> newMethods.add(
                                        m.getDeclarationAsString() + m.getBody().map(Object::toString).orElse("")
                                ));

                        for (String method : newMethods) {
                            if (!oldMethods.contains(method)) {
                                modifiedMethods.add(method);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return modifiedMethods;
    }

    // Metodo di supporto per estrarre il contenuto di un file da un commit
    private static String getFileContentFromCommit(Repository repo, RevCommit commit, String path) {
        try (TreeWalk treeWalk = TreeWalk.forPath(repo, path, commit.getTree())) {
            if (treeWalk != null) {
                ObjectId objectId = treeWalk.getObjectId(0);
                try (ObjectReader reader = repo.newObjectReader()) {
                    ObjectLoader loader = reader.open(objectId);
                    return new String(loader.getBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static org.eclipse.jgit.revwalk.RevCommit findMethodIntroductionCommit(String repoPath, String methodSignature, String untilCommitHash) {
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(new java.io.File(repoPath))) {
            Iterable<org.eclipse.jgit.revwalk.RevCommit> commits = git.log().add(git.getRepository().resolve(untilCommitHash)).call();
            com.github.javaparser.JavaParser parser = new com.github.javaparser.JavaParser();

            for (org.eclipse.jgit.revwalk.RevCommit commit : commits) {
                System.out.println("Controllo commit: " + commit.getName());
                String treePath = repoPath.replace(".git", "");
                try (org.eclipse.jgit.treewalk.TreeWalk treeWalk = new org.eclipse.jgit.treewalk.TreeWalk(git.getRepository())) {
                    treeWalk.addTree(commit.getTree());
                    treeWalk.setRecursive(true);
                    while (treeWalk.next()) {
                        String filePath = treeWalk.getPathString();
                        org.eclipse.jgit.lib.ObjectId objectId = treeWalk.getObjectId(0);
                        try (org.eclipse.jgit.lib.ObjectReader reader = git.getRepository().newObjectReader()) {
                            org.eclipse.jgit.lib.ObjectLoader loader = reader.open(objectId);
                            byte[] bytes = loader.getBytes();
                            String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
                            com.github.javaparser.ast.CompilationUnit cu = parser.parse(content).getResult().orElse(null);
                            if (cu != null) {
                                for (com.github.javaparser.ast.body.MethodDeclaration method : cu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)) {
                                    String fullSignature = method.getDeclarationAsString() + method.getBody().map(Object::toString).orElse("");
                                    if (fullSignature.equals(methodSignature)) {
                                        System.out.println("Metodo trovato nel commit: " + commit.getName());
                                        System.out.println("Metodo trovato nel file: " + filePath);
                                        System.out.println("Contenuto firma trovata:");
                                        System.out.println(method.getDeclarationAsString());
                                        return commit;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Nessun commit introduttivo trovato per il metodo.");
        System.out.println("Commit analizzati fino a " + untilCommitHash + ", ma nessun metodo corrispondente trovato.");
        return null;
    }

    //checcato
    public static RevCommit findNearestCommitBeforeDate(org.eclipse.jgit.api.Git git, Date targetDate) throws Exception {
        RevCommit nearestCommit = null;
        for (RevCommit commit : git.log().call()) {
            Date commitDate = new Date(commit.getCommitTime() * 1000L);
            if (!commitDate.after(new Date())) {
                if (nearestCommit == null || commitDate.after(new Date(nearestCommit.getCommitTime() * 1000L))) {
                    nearestCommit = commit;
                }
            }
        }
        return nearestCommit;
    }

    public static void main(String[] args) {
        String repoPath = "/Users/colaf/Documents/ISW2/zookeeper/zookeeper/.git";
        String dateString = "2025-07-29"; // Sostituisci con la data desiderata (formato yyyy-MM-dd)
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(new java.io.File(repoPath))) {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            Date targetDate = sdf.parse(dateString);

            RevCommit commit = findNearestCommitBeforeDate(git, targetDate);
            if (commit != null) {
                System.out.println("Commit trovato: " + commit.getName());
                System.out.println("Data: " + new java.util.Date(commit.getCommitTime() * 1000L));
                System.out.println("Messaggio: " + commit.getFullMessage());
            } else {
                System.out.println("Nessun commit trovato prima della data specificata.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

