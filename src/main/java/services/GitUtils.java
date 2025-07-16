package services;

import java.util.Set;
import java.util.HashSet;

public class GitUtils {

    public static Set<String> getModifiedMethodsBetweenCommits(String repoPath, String oldCommitHash, String newCommitHash) {
        Set<String> modifiedMethods = new HashSet<>();
        try (org.eclipse.jgit.lib.Repository repository = org.eclipse.jgit.api.Git.open(new java.io.File(repoPath)).getRepository()) {
            org.eclipse.jgit.lib.ObjectId oldCommitId = repository.resolve(oldCommitHash);
            org.eclipse.jgit.lib.ObjectId newCommitId = repository.resolve(newCommitHash);

            try (org.eclipse.jgit.lib.ObjectReader reader = repository.newObjectReader()) {
                org.eclipse.jgit.treewalk.CanonicalTreeParser oldTreeIter = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                oldTreeIter.reset(reader, new org.eclipse.jgit.revwalk.RevWalk(repository).parseCommit(oldCommitId).getTree());
                org.eclipse.jgit.treewalk.CanonicalTreeParser newTreeIter = new org.eclipse.jgit.treewalk.CanonicalTreeParser();
                newTreeIter.reset(reader, new org.eclipse.jgit.revwalk.RevWalk(repository).parseCommit(newCommitId).getTree());

                java.util.List<org.eclipse.jgit.diff.DiffEntry> diffs = new org.eclipse.jgit.api.Git(repository).diff()
                    .setOldTree(oldTreeIter)
                    .setNewTree(newTreeIter)
                    .call();

                com.github.javaparser.JavaParser parser = new com.github.javaparser.JavaParser();
                for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                    if (diff.getChangeType() == org.eclipse.jgit.diff.DiffEntry.ChangeType.MODIFY && diff.getNewPath().endsWith(".java")) {
                        String oldPath = repoPath.replace(".git", "") + "/" + diff.getOldPath();
                        String newPath = repoPath.replace(".git", "") + "/" + diff.getNewPath();

                        java.io.File oldFile = new java.io.File(oldPath);
                        java.io.File newFile = new java.io.File(newPath);

                        if (!oldFile.exists() || !newFile.exists()) continue;

                        com.github.javaparser.ast.CompilationUnit oldCu = parser.parse(oldFile).getResult().orElse(null);
                        com.github.javaparser.ast.CompilationUnit newCu = parser.parse(newFile).getResult().orElse(null);

                        if (oldCu == null || newCu == null) continue;

                        Set<String> oldMethods = new HashSet<>();
                        oldCu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)
                              .forEach(m -> oldMethods.add(
                                  m.getDeclarationAsString() + m.getBody().map(Object::toString).orElse("")
                              ));

                        Set<String> newMethods = new HashSet<>();
                        newCu.findAll(com.github.javaparser.ast.body.MethodDeclaration.class)
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

    public static org.eclipse.jgit.revwalk.RevCommit findMethodIntroductionCommit(String repoPath, String methodSignature, String untilCommitHash) {
        try (org.eclipse.jgit.api.Git git = org.eclipse.jgit.api.Git.open(new java.io.File(repoPath))) {
            Iterable<org.eclipse.jgit.revwalk.RevCommit> commits = git.log().add(git.getRepository().resolve(untilCommitHash)).call();
            com.github.javaparser.JavaParser parser = new com.github.javaparser.JavaParser();

            for (org.eclipse.jgit.revwalk.RevCommit commit : commits) {
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
        return null;
    }
}
