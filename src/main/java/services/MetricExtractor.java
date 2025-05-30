package services;
import java.util.logging.Logger;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PmdAnalysis;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.LanguageVersion;
import net.sourceforge.pmd.lang.rule.RuleSetLoader;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;


import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.stream.Stream;
import java.util.Map;
import java.util.List;


class MetricExtractionException extends Exception {
    public MetricExtractionException(String message, Throwable cause) {

        super(message, cause);
    }
}

/**
 * Classe responsabile per l'estrazione di metriche statiche e storiche dai metodi Java
 * all'interno di una repository Git, in base alle release specificate e ai commit legati a bug fix.
 */
public class MetricExtractor {
    private static final Logger LOGGER = Logger.getLogger(MetricExtractor.class.getName());
    private static final String JAVA_EXTENSION = ".java";

    public static void main(String[] args) throws Exception {
        extractMetrics();
    }

    @SuppressWarnings("DuplicatedCode")
    private static void extractMetrics() throws MetricExtractionException {
        File repoDir = getRepoDirectory();
        String projectName = System.getenv().getOrDefault("PROJECT_NAME", "zookeeper");
        try (Git git = Git.open(new File(repoDir, ".git"))) {
            Map<String, String> bugTickets = JiraTicketFetcher.fetchFixedBugTickets(projectName.toUpperCase());
            Map<String, List<RevCommit>> ticketCommits = BugCommitMatcher.mapTicketsToCommits(bugTickets, git);

            JavaParser parser = new JavaParser();
            HistoricalMetricsExtractor historicalExtractor = new HistoricalMetricsExtractor();

            processReleases(projectName, repoDir, git, parser, historicalExtractor, ticketCommits);
        } catch (IOException e) {
            throw new MetricExtractionException("Errore durante l'apertura della repository Git", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("DuplicatedCode")
    private static void processReleases(String projectName, File repoDir, Git git, JavaParser parser, HistoricalMetricsExtractor historicalExtractor, Map<String, List<RevCommit>> ticketCommits) throws Exception {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        try (
            BufferedReader versionReader = new BufferedReader(new FileReader(projectName.toUpperCase() + "VersionInfo.csv"));
            PrintWriter writer = new PrintWriter(new FileWriter("metrics_" + projectName.toLowerCase() + ".csv", false))
        ) {
            writer.println("Method,ReleaseId,LOC,ParamCount,Statements,Cyclomatic,Nesting,Cognitive,Smells,Modifications,Authors,NameLength,TSLC,FanOut,Buggy,File");

            String line = versionReader.readLine(); // skip header
            while ((line = versionReader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length < 4) continue;
                String releaseId = tokens[2].trim();
                String dateStr = tokens[3].split("T")[0];
                Date releaseDate = sdf.parse(dateStr);
                LOGGER.info("📌 Inizio analisi release " + releaseId + " del " + dateStr);
                LOGGER.info("📊 Ticket associati a commit in questa esecuzione: " + ticketCommits.size());
                ticketCommits.forEach((ticket, commits) -> {
                    List<RevCommit> validCommits = commits.stream()
                        .filter(c -> c.getCommitTime() * 1000L <= releaseDate.getTime())
                        .toList();
                    if (!validCommits.isEmpty()) {
                        LOGGER.info("🧾 Ticket " + ticket + ": " + validCommits.size() + " commit validi per la release " + releaseId);
                    }
                });

                git.checkout().setName("master").call();
                RevCommit bestCommit = null;
                for (RevCommit commit : git.log().call()) {
                    Date commitDate = commit.getAuthorIdent().getWhen();
                    if ((commitDate.before(releaseDate) || commitDate.equals(releaseDate)) &&
                        (bestCommit == null || commitDate.after(bestCommit.getAuthorIdent().getWhen()))) {
                        bestCommit = commit;
                    }
                }

                if (bestCommit == null) {
                    LOGGER.warning(() -> String.format("No commit found for release %s", releaseId));
                    continue;
                }

                git.checkout().setName(bestCommit.getName()).call();
                RevCommit finalBestCommit = bestCommit;
                LOGGER.info(() -> String.format("Checked out release %s at commit %s", releaseId, finalBestCommit.getName()));

                try (Stream<Path> paths = Files.walk(repoDir.toPath())) {
                    paths.filter(Files::isRegularFile)
                         .filter(p -> p.toString().endsWith(JAVA_EXTENSION))
                         .filter(p -> !p.toString().contains("/target/"))
                         .filter(p -> !p.toString().contains("/test/"))
                         .filter(p -> !p.toString().contains("/build/"))
                         .forEach(path -> {
                             try {
                                 CompilationUnit compilationUnit = parser.parse(path).getResult().orElse(null);
                                 if (compilationUnit == null) return;
                                 compilationUnit.findAll(MethodDeclaration.class).forEach(method -> {
                                     String methodName = method.getNameAsString();
                                     int paramCount = method.getParameters().size();
                                     int loc = method.toString().split("\n").length;
                                     // Statements count: approximate as number of semicolons in method body
                                     int statements = method.getBody().isPresent() ? method.getBody().get().toString().split(";").length - 1 : 0;
                                     int cyclomatic = countCyclomaticComplexity(method);
                                     int nesting = countMaxNestingDepth(method);
                                     int cognitive = cyclomatic + nesting;
                                     int smells = countPMDSmells(method.toString());
                                     int nameLength = methodName.length();
                                     long tslc = calculateTSLC(path, releaseDate, git);
                                     int fanOut = method.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class).size();

                                     boolean buggy = false;
                                     try {
                                         int methodStart = method.getBegin().get().line;
                                         int methodEnd = method.getEnd().get().line;
                                         boolean matchFound = false;
                                         String currentNormalized = repoDir.toPath().relativize(path).toString().replace("\\\\", "/");
                                         // Log numero di commit associati a ticket
                                         LOGGER.info("🔄 Numero di commit associati a ticket: " + ticketCommits.values().stream().flatMap(List::stream).count());
                                         for (RevCommit commit : ticketCommits.values().stream().flatMap(List::stream).toList()) {
                                             if (commit.getCommitTime() * 1000L > releaseDate.getTime() || commit.getParentCount() == 0) continue;
                                             RevCommit parent = commit.getParent(0);
                                             try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE)) {
                                                 df.setRepository(git.getRepository());
                                                 df.setDetectRenames(true);
                                                 List<org.eclipse.jgit.diff.DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());
                                                 for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                                                     String modifiedPath = diff.getNewPath();
                                                     if (modifiedPath.endsWith(JAVA_EXTENSION)) {
                                                         String normalizedModified = modifiedPath.replace("\\\\", "/");
                                                         LOGGER.info("🔍 Confronto path: modified=" + normalizedModified + ", current=" + currentNormalized);
                                                         if (normalizedModified.equals(currentNormalized)) {
                                                             LOGGER.info("✅ Match path con file modificato: " + normalizedModified);
                                                             List<org.eclipse.jgit.diff.Edit> edits = df.toFileHeader(diff).toEditList();
                                                             for (org.eclipse.jgit.diff.Edit edit : edits) {
                                                                 LOGGER.info("✏️ Edit lines: " + edit.getBeginB() + " - " + edit.getEndB());
                                                             }
                                                             LOGGER.info("📌 Metodo: " + methodName + " (linee: " + methodStart + " - " + methodEnd + ")");
                                                             if (edits.stream().anyMatch(edit -> {
                                                                 int editStart = edit.getBeginB();
                                                                 int editEnd = edit.getEndB();
                                                                 return editEnd >= methodStart && editStart <= methodEnd;
                                                             })) {
                                                                 buggy = true;
                                                                 matchFound = true;
                                                                 break;
                                                             }
                                                         }
                                                     }
                                                 }
                                             }
                                             if (matchFound) break;
                                         }
                                         if (buggy) {
                                             LOGGER.info("🐞 Metodo buggy rilevato: " + methodName + " nel file " + path);
                                         } else {
                                             LOGGER.info("✅ Metodo non buggy: " + methodName + " nel file " + path);
                                         }
                                     } catch (Exception e) {
                                         buggy = false;
                                     }

                                     writeHistoricalMetrics(
                                         historicalExtractor, path, releaseDate, git, methodName, releaseId,
                                         loc, paramCount, statements, cyclomatic, nesting, cognitive, smells,
                                         nameLength, tslc, fanOut, buggy, writer
                                     );
                                 });
                             } catch (Exception _) {
                                 LOGGER.warning("Errore nel parsing: " + path);
                             }
                         });
                }
            }
        } catch (Exception e) {
            throw new MetricExtractionException("Errore durante l'estrazione delle metriche", e);
        }
    }

    private static File getRepoDirectory() {
        String projectName = System.getenv().getOrDefault("PROJECT_NAME", "zookeeper");
        String basePath = System.getenv().getOrDefault("REPO_BASE", "/Users/colaf/Documents/ISW2/");
        String repoPath = basePath + projectName + "/" + projectName + "/";
        return new File(repoPath);
    }

    private static long calculateTSLC(Path path, Date releaseDate, Git git) {
        try {
            Iterable<RevCommit> commits = git.log()
                .addPath(path.toString().replace("\\\\", "/"))
                .call();
            for (RevCommit c : commits) {
                Date commitDate = c.getAuthorIdent().getWhen();
                if (!commitDate.after(releaseDate)) {
                    long diffMillis = releaseDate.getTime() - commitDate.getTime();
                    return diffMillis / (1000 * 60 * 60 * 24);
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

    private static int countNesting(com.github.javaparser.ast.Node node, int depth) {
        int max = depth;
        for (com.github.javaparser.ast.Node child : node.getChildNodes()) {
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

            LOGGER.info("📄 Codice analizzato da PMD:\n" + code);

            Path tempDir = Files.createTempDirectory("pmd_tmp_");
            tempDir.toFile().deleteOnExit();
            Path tempFile = Files.createTempFile(tempDir, "method", JAVA_EXTENSION);
            Files.writeString(tempFile, "public class TempClass { " + code + " }");
            pmd.files().addFile(tempFile);

            var report = pmd.performAnalysisAndCollectReport();
            // Log PMD violations count
            LOGGER.info("🔍 PMD trovato " + report.getViolations().spliterator().getExactSizeIfKnown() + " smells");
            Files.deleteIfExists(tempFile);
            Files.deleteIfExists(tempDir);

            return (int) report.getViolations().spliterator().getExactSizeIfKnown();
        } catch (Exception e) {
            LOGGER.severe("Errore nell'analisi PMD: " + e.getMessage());
            return 0;
        }
    }
    private static void writeHistoricalMetrics(
            HistoricalMetricsExtractor historicalExtractor,
            Path path,
            Date releaseDate,
            Git git,
            String methodName,
            String releaseId,
            int loc,
            int paramCount,
            int statements,
            int cyclomatic,
            int nesting,
            int cognitive,
            int smells,
            int nameLength,
            long tslc,
            int fanOut,
            boolean buggy,
            PrintWriter writer
    ) {
        try {
            HistoricalMetricsExtractor.HistoricalMetrics historical = historicalExtractor.extract(path, releaseDate, git);
            int modifications = historical.getModifications();
            int authors = historical.getAuthors().size();
            writer.println(String.format(
                    "%s,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%s",
                    methodName, releaseId, loc, paramCount, statements,
                    cyclomatic, nesting, cognitive, smells, modifications,
                    authors, nameLength, tslc, fanOut, buggy ? 1 : 0, path.toString()
            ));
        } catch (IOException | org.eclipse.jgit.api.errors.GitAPIException _) {
            LOGGER.warning("Errore nelle metriche storiche per " + methodName);
        }
    }
}



    // --- PATCH: Estendi la finestra di confronto commit/ticket a ±2 giorni ---
    // Se hai una classe TicketCommitLinker con associateByHeuristics, modifica la condizione temporale così:
    // long diff = Math.abs(commitDate.getTime() - ticketDate.getTime());
    // if (diff <= TimeUnit.DAYS.toMillis(2)) { ... }