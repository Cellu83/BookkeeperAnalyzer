package services;

import services.TicketInfo;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.MethodCallExpr;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.rule.RuleSetLoader;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.diff.Edit;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.io.DisabledOutputStream;

import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Stream;

class MetricExtractionException extends Exception {
    public MetricExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * Classe responsabile per l'estrazione di metriche statiche e storiche dai metodi Java
 * all'interno di una repository Git, in base alle release specificate e ai commit legati a bug fix.
 *
 * Legge il file VersionInfo.csv (già preparato prima con ReleaseInfoFetcher)
 * 	•	Per ogni release:
 * 	•	Filtra i commit di fix anteriori alla data della release
 * 	•	Identifica il commit della release (più recente ma non oltre la data)
 * 	•	Fa il checkout a quel commit
 * 	•	Analizza tutti i .java → estrae le metriche per ogni metodo
 */
public class MetricExtractor {
    private static final String CSV_SUFFIX = "new"; // Cambia in "old" per generare il CSV vecchio
    private static final String JAVA_EXTENSION = ".java";
    private static final String CSV_DELIMITER = ",";
    private static final String METRICS_FILE_PREFIX = "metrics_";
    private static final String METRICS_FILE_SUFFIX = ".csv";
    private static final String VERSION_INFO_SUFFIX = "VersionInfo.csv";
    private static final String MASTER_BRANCH = "master";
    private static final String DEFAULT_PROJECT = "zookeeper";
    private static final String ENV_PROJECT_NAME = "PROJECT_NAME";
    private static final String ENV_REPO_BASE = "REPO_BASE";
    private static final String DEFAULT_REPO_BASE = "/Users/colaf/Documents/ISW2/";
    private static final String REPO_SUBFOLDER_FORMAT = "%s/%s/";

    private static final Logger LOGGER = Logger.getLogger(MetricExtractor.class.getName());

    public static void main(String[] args) throws Exception {
        extractMetrics();
    }

    private static void extractMetrics() throws MetricExtractionException {
        File repoDir = getRepoDirectory();
        String projectName = System.getenv().getOrDefault(ENV_PROJECT_NAME, DEFAULT_PROJECT);

        try (Git git = Git.open(new File(repoDir, ".git"))) {
            Map<String, TicketInfo> bugTickets = JiraTicketFetcher.fetchFixedBugTickets(projectName.toUpperCase());
            String repoPath = repoDir.getAbsolutePath();
            Map<String, TicketInfo> ticketCommits = BugCommitMatcher.mapTicketsToCommits(bugTickets, git, repoPath);

            JavaParser parser = new JavaParser();
            HistoricalMetricsExtractor historicalExtractor = new HistoricalMetricsExtractor();

            processReleases(projectName, repoDir, git, parser, historicalExtractor, ticketCommits);
        } catch (IOException e) {
            throw new MetricExtractionException("Errore durante l'apertura della repository Git", e);
        } catch (Exception e) {
            throw new MetricExtractionException("Errore imprevisto durante l'estrazione delle metriche", e);
        }
    }

    private static void processReleases(
            String projectName,
            File repoDir,
            Git git,
            JavaParser parser,
            HistoricalMetricsExtractor historicalExtractor,
            Map<String, TicketInfo> ticketCommits
    ) throws MetricExtractionException {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        String versionFilePath = projectName.toUpperCase() + VERSION_INFO_SUFFIX;
        // Permette di distinguere tra CSV "old" e "new" tramite CSV_SUFFIX
        String suffix = CSV_SUFFIX;
        String metricsFilePath = METRICS_FILE_PREFIX + projectName.toLowerCase() + suffix + METRICS_FILE_SUFFIX;

        try (
                BufferedReader versionReader = new BufferedReader(new FileReader(versionFilePath));
                PrintWriter writer = new PrintWriter(new FileWriter(metricsFilePath, false))
        ) {
            writeMetricsHeader(writer);

            versionReader.readLine();

            // Leggi le versioni dal file CSV
            String line;
            while ((line = versionReader.readLine()) != null) {
                String[] tokens = line.split(CSV_DELIMITER);
                if (tokens.length >= 4) {
                    String releaseId = tokens[2].trim();
                    String dateStr = tokens[3].split("T")[0];
                    Date releaseDate = sdf.parse(dateStr);

                    filterTicketCommitsByDate(ticketCommits, releaseDate);

                    RevCommit releaseCommit = findReleaseCommit(git, releaseDate);
                    if (releaseCommit != null) {
                        checkoutCommit(git, releaseCommit);
                        processJavaFiles(repoDir, parser, git,
                                new ReleaseContext(releaseDate, releaseId, ticketCommits),
                                historicalExtractor, writer);
                    } else {
                        LOGGER.warning(() -> String.format("No commit found for release %s", releaseId));
                    }
                }
            }
        } catch (Exception e) {
            throw new MetricExtractionException("Errore durante l'estrazione delle metriche", e);
        }
    }

    private static void writeMetricsHeader(PrintWriter writer) {
        writer.println("Method,ReleaseId,LOC,ParamCount,Statements,Cyclomatic,Nesting,Cognitive,Smells," +
                "Modifications,Authors,NameLength,TSLC,FanOut,Buggy,File,CommitHash");
    }

    private static void filterTicketCommitsByDate(Map<String, TicketInfo> ticketCommits, Date releaseDate) {
        ticketCommits.forEach((ticket, ticketInfo) -> {
            List<RevCommit> commits = (List<RevCommit>) ticketInfo.getAssociatedCommits();
            commits.removeIf(c -> c.getCommitTime() * 1000L > releaseDate.getTime());
        });
    }

    private static RevCommit findReleaseCommit(Git git, Date releaseDate) throws Exception {
        // Rimuove file problematici prima del checkout, se presenti
        File repoDir = git.getRepository().getWorkTree();
        Path fileToRemove = Paths.get(repoDir.getAbsolutePath(), "src", "lastRevision.bat");
        if (Files.exists(fileToRemove)) {
            Files.delete(fileToRemove);
        }
        git.checkout().setName(MASTER_BRANCH).setForced(true).call();
        RevCommit bestCommit = null;

        for (RevCommit commit : git.log().call()) {
            Date commitDate = commit.getAuthorIdent().getWhen();
            if ((commitDate.before(releaseDate) || commitDate.equals(releaseDate)) &&
                    (bestCommit == null || commitDate.after(bestCommit.getAuthorIdent().getWhen()))) {
                bestCommit = commit;
            }
        }

        return bestCommit;
    }

    private static void checkoutCommit(Git git, RevCommit commit) throws Exception {
        git.checkout()
                .setName(commit.getName())
                .setForced(true)
                .call();
        LOGGER.info(() -> String.format("Checked out commit %s", commit.getName()));
    }

    private static class ReleaseContext {
        final Date releaseDate;
        final String releaseId;
        final Map<String, TicketInfo> ticketCommits;

        ReleaseContext(Date releaseDate, String releaseId, Map<String, TicketInfo> ticketCommits) {
            this.releaseDate = releaseDate;
            this.releaseId = releaseId;
            this.ticketCommits = ticketCommits;
        }
    }

    private static void processJavaFiles(
            File repoDir,
            JavaParser parser,
            Git git,
            ReleaseContext context,
            HistoricalMetricsExtractor historicalExtractor,
            PrintWriter writer
    ) throws IOException {
        JavaProcessingContext processingContext = new JavaProcessingContext(git, context, historicalExtractor, writer);
        try (Stream<Path> paths = Files.walk(repoDir.toPath())) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(JAVA_EXTENSION))
                    .filter(p -> !p.toString().contains("/target/"))
                    .filter(p -> !p.toString().contains("/test/"))
                    .filter(p -> !p.toString().contains("/build/"))
                    .forEach(path -> processJavaFile(path, parser, processingContext));
        }
    }

    private static class JavaProcessingContext {
        final Git git;
        final ReleaseContext releaseContext;
        final HistoricalMetricsExtractor historicalExtractor;
        final PrintWriter writer;

        JavaProcessingContext(Git git, ReleaseContext releaseContext, HistoricalMetricsExtractor historicalExtractor, PrintWriter writer) {
            this.git = git;
            this.releaseContext = releaseContext;
            this.historicalExtractor = historicalExtractor;
            this.writer = writer;
        }
    }

    private static void processJavaFile(
            Path path,
            JavaParser parser,
            JavaProcessingContext context
    ) {
        try {
            CompilationUnit compilationUnit = parser.parse(path).getResult().orElse(null);
            if (compilationUnit == null) return;

            compilationUnit.findAll(MethodDeclaration.class).forEach(method ->
                processMethod(method, path, context.git, context.releaseContext, context.historicalExtractor, context.writer));

        } catch (Exception e) {
            LOGGER.warning("Errore nel parsing: " + path + " - " + e.getMessage());
        }
    }

    private static void processMethod(
            MethodDeclaration method,
            Path path,
            Git git,
            ReleaseContext context,
            HistoricalMetricsExtractor historicalExtractor,
            PrintWriter writer
    ) {
        String methodName = method.getNameAsString();
        int paramCount = method.getParameters().size();
        int loc = method.toString().split("\n").length;
        int statements = countStatements(method);
        int cyclomatic = countCyclomaticComplexity(method);
        int nesting = countMaxNestingDepth(method);
        int cognitive = cyclomatic + nesting;
        int smells = countPMDSmells(method.toString());
        int nameLength = methodName.length();
        long tslc = calculateTSLC(path, context.releaseDate, git);
        int fanOut = method.findAll(MethodCallExpr.class).size();

        boolean buggy = isBuggy(method, path, context.releaseDate, git, context.ticketCommits);

        MethodMetrics metrics = new MethodMetrics(
                methodName, context.releaseId, loc, paramCount, statements,
                cyclomatic, nesting, cognitive, smells, nameLength, tslc, fanOut, buggy
        );
        writeMethodMetrics(historicalExtractor, path, context.releaseDate, git, metrics, writer);
    }

private static int countStatements(MethodDeclaration method) {
    return method.getBody()
            .map(body -> body.toString().split(";").length - 1)
            .orElse(0);
}

    private static File getRepoDirectory() {
        String projectName = System.getenv().getOrDefault(ENV_PROJECT_NAME, DEFAULT_PROJECT);
        String basePath = System.getenv().getOrDefault(ENV_REPO_BASE, DEFAULT_REPO_BASE);
        String repoPath = basePath + String.format(REPO_SUBFOLDER_FORMAT, projectName, projectName);
        return new File(repoPath);
    }

    private static long calculateTSLC(Path path, Date releaseDate, Git git) {
        try {
            String relPath = path.toAbsolutePath().toString()
                .replace(git.getRepository().getWorkTree().getAbsolutePath(), "")
                .replace(File.separatorChar, '/')
                .replaceAll("^/", ""); // rimuove eventuale slash iniziale

            Iterable<RevCommit> commits = git.log()
                    .addPath(relPath)
                    .call();

            for (RevCommit c : commits) {
                Date commitDate = c.getAuthorIdent().getWhen();
                if (!commitDate.after(releaseDate)) {
                    long diffMillis = releaseDate.getTime() - commitDate.getTime();
                    return diffMillis / (1000 * 60 * 60 * 24);  // giorni
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Errore nel calcolo del TSLC per " + path + ": " + e.getMessage());
        }
        return -1;
    }

    private static int countCyclomaticComplexity(MethodDeclaration method) {
        String code = method.toString();
        int count = 1;
        count += code.split("if\\s*\\(").length - 1;
        count += code.split("for\\s*\\(").length - 1;
        count += code.split("while\\s*\\(").length - 1;
        count += code.split("case\\s").length - 1;
        count += code.split("catch\\s*\\(").length - 1;
        count += code.split("&&").length - 1;
        count += code.split("\\|\\|").length - 1;
        count += code.split("\\?").length - 1;
        return count;
    }

    private static int countMaxNestingDepth(MethodDeclaration method) {
        return countNesting(method, 0);
    }

    private static int countNesting(Node node, int depth) {
        int max = depth;
        for (Node child : node.getChildNodes()) {
            int d = countNesting(child, depth + 1);
            if (d > max) max = d;
        }
        return max;
    }

    private static int countPMDSmells(String code) {
        PMDConfiguration config = new PMDConfiguration();
        LanguageVersion javaVersion = LanguageRegistry.PMD.getLanguageVersionById("java", "17");
        config.setDefaultLanguageVersion(javaVersion);
        config.setIgnoreIncrementalAnalysis(true);

        try (PmdAnalysis pmd = PmdAnalysis.create(config)) {
            RuleSetLoader loader = RuleSetLoader.fromPmdConfig(config);
            pmd.addRuleSet(loader.loadFromResource("category/java/bestpractices.xml"));
            pmd.addRuleSet(loader.loadFromResource("category/java/errorprone.xml"));
            pmd.addRuleSet(loader.loadFromResource("category/java/codestyle.xml"));
            pmd.addRuleSet(loader.loadFromResource("category/java/design.xml"));

            Path tempDir = Files.createTempDirectory("pmd_tmp_");
            tempDir.toFile().deleteOnExit();
            Path tempFile = Files.createTempFile(tempDir, "method", JAVA_EXTENSION);
            Files.writeString(tempFile, "public class TempClass { " + code + " }");
            pmd.files().addFile(tempFile);

            var report = pmd.performAnalysisAndCollectReport();

            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(tempDir);

            return (int) report.getViolations().spliterator().getExactSizeIfKnown();
        } catch (Exception e) {
            LOGGER.severe("Errore nell'analisi PMD: " + e.getMessage());
            return 0;
        }
    }

private static boolean isBuggy(
        MethodDeclaration method,
        Path path,
        Date releaseDate,
        Git git,
        Map<String, TicketInfo> ticketCommits
) {
    String currentNormalized = null;
    try {
        if (method.getBegin().isEmpty() || method.getEnd().isEmpty()) {
            return false;
        }
        int methodStart = method.getBegin().map(pos -> pos.line).orElse(-1);
        int methodEnd = method.getEnd().map(pos -> pos.line).orElse(-1);

        currentNormalized = path.toString().replace("\\\\", "/");

        for (TicketInfo ticket : ticketCommits.values()) {
            for (RevCommit commit : ticket.getAssociatedCommits()) {
                if (commit.getCommitTime() * 1000L > releaseDate.getTime() || commit.getParentCount() == 0) {
                    continue;
                }
                if (!ticket.getBuggyMethods().contains(method.getNameAsString())) {
                    continue;
                }

                if (isMethodModifiedInCommit(commit, git, currentNormalized, methodStart, methodEnd)) {
                    System.out.printf(">> BUGGY METHOD: %s in file %s at release date %s (commit %s)%n",
                            method.getNameAsString(), currentNormalized, releaseDate.toString(), commit.getName());
                    return true;
                }
            }
        }
    } catch (Exception e) {
        LOGGER.warning("Errore nel determinare se il metodo è buggy: " + e.getMessage());
    }
    System.out.printf(">> CLEAN METHOD: %s in file %s%n", method.getNameAsString(), currentNormalized);
    return false;
}

private static boolean isMethodModifiedInCommit(
        RevCommit commit,
        Git git,
        String currentFilePath,
        int methodStart,
        int methodEnd
) throws Exception {
    RevCommit parent = commit.getParent(0);
    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
        df.setRepository(git.getRepository());
        df.setDetectRenames(true);
        List<DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
        for (DiffEntry diff : diffs) {
            if (isMethodModifiedInFile(df, diff, currentFilePath, methodStart, methodEnd)) {
                return true;
            }
        }
    }
    return false;
}

private static boolean isMethodModifiedInFile(DiffFormatter df, DiffEntry diff, String currentFilePath, int methodStart, int methodEnd) throws IOException {
    String modifiedPath = diff.getNewPath();
    if (modifiedPath.endsWith(JAVA_EXTENSION)) {
        String normalizedModified = modifiedPath.replace("\\\\", "/");

        if (currentFilePath.endsWith(normalizedModified)) {
            System.out.printf(">> File match: %s ends with %s%n", currentFilePath, normalizedModified);
            List<Edit> edits = df.toFileHeader(diff).toEditList();

            for (Edit edit : edits) {
                int editStart = edit.getBeginB();
                int editEnd = edit.getEndB();

                if (editEnd >= methodStart && editStart <= methodEnd) {
                    System.out.printf(">> BUGGY detected: method lines [%d-%d] overlap edit [%d-%d]%n",
                        methodStart, methodEnd, editStart, editEnd);
                    return true;
                }
            }
        }
    }
    return false;
}

    private static class MethodMetrics {
        final String methodName;
        final String releaseId;
        final int loc;
        final int paramCount;
        final int statements;
        final int cyclomatic;
        final int nesting;
        final int cognitive;
        final int smells;
        final int nameLength;
        final int fanOut;
        final long tslc;
        final boolean buggy;


        // Constructor for MethodMetrics, smella ma richiederebbe un altra classe.
        MethodMetrics(String methodName, String releaseId, int loc, int paramCount, int statements, int cyclomatic,
                      int nesting, int cognitive, int smells, int nameLength, long tslc, int fanOut, boolean buggy) {
            this.methodName = methodName;
            this.releaseId = releaseId;
            this.loc = loc;
            this.paramCount = paramCount;
            this.statements = statements;
            this.cyclomatic = cyclomatic;
            this.nesting = nesting;
            this.cognitive = cognitive;
            this.smells = smells;
            this.nameLength = nameLength;
            this.tslc = tslc;
            this.fanOut = fanOut;
            this.buggy = buggy;
        }
    }

    private static void writeMethodMetrics(
            HistoricalMetricsExtractor historicalExtractor,
            Path path,
            Date releaseDate,
            Git git,
            MethodMetrics metrics,
            PrintWriter writer
    ) {
        try {
            HistoricalMetricsExtractor.HistoricalMetrics historical =
                    historicalExtractor.extract(path, releaseDate, git);

            int modifications = historical.getModifications();
            int authors = historical.getAuthors().size();

            String commitHash = findLatestCommitHashBeforeRelease(path, releaseDate, git);
            writer.println(String.format(
                    "%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s,%s,%s",
                    metrics.methodName, metrics.releaseId, metrics.loc, metrics.paramCount, metrics.statements,
                    metrics.cyclomatic, metrics.nesting, metrics.cognitive, metrics.smells, modifications,
                    authors, metrics.nameLength, metrics.tslc, metrics.fanOut, metrics.buggy ? "YES" : "NO",
                    path.toString(), commitHash
            ));
        } catch (IOException | org.eclipse.jgit.api.errors.GitAPIException e) {
            LOGGER.warning("Errore nelle metriche storiche per " + metrics.methodName + ": " + e.getMessage());
        }
    }

    private static String findLatestCommitHashBeforeRelease(Path path, Date releaseDate, Git git) {
        try {
            String relPath = path.toAbsolutePath().toString()
                    .replace(git.getRepository().getWorkTree().getAbsolutePath(), "")
                    .replace(File.separatorChar, '/')
                    .replaceAll("^/", "");

            Iterable<RevCommit> commits = git.log().addPath(relPath).call();

            for (RevCommit commit : commits) {
                Date commitDate = commit.getAuthorIdent().getWhen();
                if (!commitDate.after(releaseDate)) {
                    return commit.getName(); // SHA-1 del commit
                }
            }
        } catch (Exception e) {
            LOGGER.warning("Errore nel recupero del commit per " + path + ": " + e.getMessage());
        }
        return "UNKNOWN";
    }
}