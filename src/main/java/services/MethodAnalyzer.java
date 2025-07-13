package services;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.nio.file.Files;

/**
 * Estrae metriche statiche e storiche solo per i metodi modificati in uno specifico commit.
 */
public class MethodAnalyzer {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: MethodAnalyzer <commitHash> <methodName>");
            System.exit(1);
        }
        try {
            extractMetricsFromCommit(args[0], args[1]);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void extractMetricsFromCommit(String commitHash, String targetMethodName) throws Exception {
        // Inline logic from MetricExtractor.getRepoDirectory()
        String ENV_PROJECT_NAME = "PROJECT_NAME";
        String DEFAULT_PROJECT = "bookkeeper_ref";
        String ENV_REPO_BASE = "REPO_BASE";
        String DEFAULT_REPO_BASE = "/Users/colaf/Documents/ISW2/";
        String REPO_SUBFOLDER_FORMAT = "%s/%s/";
        String projectName = System.getenv().getOrDefault(ENV_PROJECT_NAME, DEFAULT_PROJECT);
        String basePath = System.getenv().getOrDefault(ENV_REPO_BASE, DEFAULT_REPO_BASE);
        String repoPath = basePath + String.format(REPO_SUBFOLDER_FORMAT, projectName, projectName);
        File repoDir = new File(repoPath);
        try (Git git = Git.open(new File(repoDir, ".git"))) {
            Repository repository = git.getRepository();
            RevWalk revWalk = new RevWalk(repository);
            RevCommit commit = revWalk.parseCommit(ObjectId.fromString(commitHash));
            if (commit.getParentCount() == 0) {
                System.err.println("Commit has no parent (root commit), nothing to diff.");
                return;
            }
            RevCommit parent = revWalk.parseCommit(commit.getParent(0).getId());

            // Trova i file modificati .java
            List<DiffEntry> diffs;
            try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
                df.setRepository(repository);
                df.setDetectRenames(true);
                diffs = df.scan(parent.getTree(), commit.getTree());
            }

            JavaParser parser = new JavaParser();

            for (DiffEntry diff : diffs) {
                String path = null;
                if (diff.getChangeType() == ChangeType.DELETE) {
                    path = diff.getOldPath();
                } else {
                    path = diff.getNewPath();
                }
                if (!path.endsWith(".java")) continue;
                // Recupera contenuto file dal commit (o parent per DELETE)
                String fileContent = readFileContentFromTree(repository,
                        diff.getChangeType() == ChangeType.DELETE ? parent : commit, path);
                if (fileContent == null) continue;

                ParseResult<CompilationUnit> parseResult = parser.parse(fileContent);
                if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) continue;
                CompilationUnit cu = parseResult.getResult().get();

                // Per ogni metodo, verifica se modificato in questo commit
                List<MethodDeclaration> methods = cu.findAll(MethodDeclaration.class);
                for (MethodDeclaration method : methods) {
                    if (!method.getNameAsString().equals(targetMethodName)) continue;
                    int methodStart = method.getBegin().map(pos -> pos.line).orElse(-1);
                    int methodEnd = method.getEnd().map(pos -> pos.line).orElse(-1);
                    // Prende gli edit per questo file
                    List<Edit> edits = getEditsForFile(repository, parent, commit, path);
                    boolean methodModified = false;
                    for (Edit edit : edits) {
                        int editStart = edit.getBeginB();
                        int editEnd = edit.getEndB();
                        if (editEnd >= methodStart && editStart <= methodEnd) {
                            methodModified = true;
                            break;
                        }
                    }
                    if (!methodModified) continue;
                    // Calcola metriche e stampa
                    computeAndPrintMetrics(method, path, commit, git);
                }
            }
        }
    }

    private static String readFileContentFromTree(Repository repo, RevCommit commit, String path) throws IOException {
        try (org.eclipse.jgit.treewalk.TreeWalk treeWalk = org.eclipse.jgit.treewalk.TreeWalk.forPath(repo, path, commit.getTree())) {
            if (treeWalk == null) return null;
            ObjectId objectId = treeWalk.getObjectId(0);
            try (org.eclipse.jgit.lib.ObjectReader reader = repo.newObjectReader()) {
                byte[] bytes = reader.open(objectId).getBytes();
                return new String(bytes);
            }
        }
    }

    private static List<Edit> getEditsForFile(Repository repo, RevCommit parent, RevCommit commit, String path) throws IOException {
        List<Edit> edits = new ArrayList<>();
        try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
            df.setRepository(repo);
            df.setDetectRenames(true);
            List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
            for (DiffEntry diff : diffs) {
                String diffPath = diff.getChangeType() == ChangeType.DELETE ? diff.getOldPath() : diff.getNewPath();
                if (diffPath.equals(path)) {
                    edits.addAll(df.toFileHeader(diff).toEditList());
                }
            }
        }
        return edits;
    }

    // Methods reimplemented locally, no longer depend on MetricExtractor private methods.
    private static int countStatements(MethodDeclaration method) {
        return method.findAll(com.github.javaparser.ast.stmt.Statement.class).size();
    }
    private static int countCyclomaticComplexity(MethodDeclaration method) {
        return method.findAll(com.github.javaparser.ast.stmt.IfStmt.class).size()
                + method.findAll(com.github.javaparser.ast.stmt.ForStmt.class).size()
                + method.findAll(com.github.javaparser.ast.stmt.WhileStmt.class).size()
                + method.findAll(com.github.javaparser.ast.stmt.SwitchEntry.class).size()
                + 1; // +1 for default path
    }
    private static int countMaxNestingDepth(MethodDeclaration method) {
        return getMaxNesting(method.getBody().orElse(null), 0);
    }
    private static int getMaxNesting(com.github.javaparser.ast.Node node, int depth) {
        if (node == null) return depth;
        int max = depth;
        for (com.github.javaparser.ast.Node child : node.getChildNodes()) {
            if (child instanceof com.github.javaparser.ast.stmt.BlockStmt) {
                max = Math.max(max, getMaxNesting(child, depth + 1));
            } else {
                max = Math.max(max, getMaxNesting(child, depth));
            }
        }
        return max;
    }
    private static int countPMDSmells(String code) {
        try {
            File tempFile = File.createTempFile("pmd_temp", ".java");
            Files.writeString(tempFile.toPath(), code);
            ProcessBuilder pb = new ProcessBuilder(
                "/Users/colaf/Downloads/pmd-bin-7.14.0/bin/pmd", "check",
                "--dir", tempFile.getAbsolutePath(),
                "--rulesets", "rulesets/java/quickstart.xml",
                "--format", "text"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            tempFile.delete();
            return (int) output.lines().filter(line -> line.contains(tempFile.getName())).count();
        } catch (Exception e) {
            return -1;
        }
    }

    private static void computeAndPrintMetrics(MethodDeclaration method, String filePath, RevCommit commit, Git git) {
        String methodName = method.getNameAsString();
        int paramCount = method.getParameters().size();
        int loc = method.toString().split("\n").length;
        int statements = countStatements(method);
        int cyclomatic = countCyclomaticComplexity(method);
        int nesting = countMaxNestingDepth(method);
        int cognitive = cyclomatic + nesting;
        int smells = countPMDSmells(method.toString());
        int nameLength = methodName.length();
        int fanOut = method.findAll(MethodCallExpr.class).size();
        // TSLC: giorni dal commit precedente su questo file
        long tslc = calcTSLC(filePath, commit, git);
        // Buggy: non rilevante per singolo commit
        boolean buggy = false;
        System.out.printf(
                "Method: %s | LOC: %d | Params: %d | Statements: %d | Cyclomatic: %d | Nesting: %d | Cognitive: %d | Smells: %d | NameLen: %d | TSLC: %d | FanOut: %d | File: %s | Commit: %s%n",
                methodName, loc, paramCount, statements, cyclomatic, nesting, cognitive, smells, nameLength, tslc, fanOut, filePath, commit.getName()
        );
    }

    private static long calcTSLC(String filePath, RevCommit commit, Git git) {
        try {
            String relPath = filePath.replace("\\", "/");
            Iterable<RevCommit> commits = git.log().addPath(relPath).call();
            Date commitDate = commit.getAuthorIdent().getWhen();
            for (RevCommit c : commits) {
                Date cDate = c.getAuthorIdent().getWhen();
                if (cDate.before(commitDate)) {
                    long diffMillis = commitDate.getTime() - cDate.getTime();
                    return diffMillis / (1000 * 60 * 60 * 24);
                }
            }
        } catch (Exception e) {
            // ignora
        }
        return -1;
    }
}
