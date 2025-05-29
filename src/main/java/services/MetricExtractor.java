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

public class MetricExtractor {
    private static final Logger LOGGER = Logger.getLogger(MetricExtractor.class.getName());
    private static final String JAVA_EXTENSION = ".java";

    public static void main(String[] args) throws Exception {
        extractMetrics();
    }

    private static void extractMetrics() throws MetricExtractionException {
        final String PATH_SEPARATOR_REGEX = "\\\\";
        final String PATH_SEPARATOR = "/";

        // Percorso della repo locale (ora configurabile tramite variabile d'ambiente REPO_DIR)
        String repoPath = System.getenv().getOrDefault("REPO_DIR", "/Users/colaf/Documents/ISW2/bookkeeper/bookkeeper_ISW2/");
        File repoDir = new File(repoPath);
        try (Git git = Git.open(new File(repoDir, ".git"))) {

            Map<String, String> bugTickets = JiraTicketFetcher.fetchFixedBugTickets("BOOKKEEPER");
            Map<String, List<RevCommit>> ticketCommits = BugCommitMatcher.mapTicketsToCommits(bugTickets, git);

            JavaParser parser = new JavaParser();
            HistoricalMetricsExtractor historicalExtractor = new HistoricalMetricsExtractor();

            processReleases(repoDir, git, parser, historicalExtractor, ticketCommits);
        } catch (IOException e) {
            throw new MetricExtractionException("Errore durante l'apertura della repository Git", e);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void processReleases(File repoDir, Git git, JavaParser parser, HistoricalMetricsExtractor historicalExtractor, Map<String, List<RevCommit>> ticketCommits) throws Exception {
        final String PATH_SEPARATOR_REGEX = "\\\\";
        final String PATH_SEPARATOR = "/";
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        try (
            BufferedReader versionReader = new BufferedReader(new FileReader("BOOKKEEPERVersionInfo.csv"));
            PrintWriter writer = new PrintWriter(new FileWriter("metrics_dataset.csv", false))
        ) {
            writer.println("Method,ReleaseId,LOC,ParamCount,Statements,Cyclomatic,Nesting,Cognitive,Smells,Modifications,Authors,NameLength,TSLC,FanOut,Buggy,File");

            String line = versionReader.readLine(); // skip header
            while ((line = versionReader.readLine()) != null) {
                String[] tokens = line.split(",");
                if (tokens.length < 4) continue;
                String releaseId = tokens[2].trim();
                String dateStr = tokens[3].split("T")[0];
                Date releaseDate = sdf.parse(dateStr);

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
                                 CompilationUnit cu = parser.parse(path).getResult().orElse(null);
                                 if (cu == null) return;
                                 cu.findAll(MethodDeclaration.class).forEach(method -> {
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
                                     long tslc = 0;
                                     try {
                                         Iterable<RevCommit> commits = git.log().addPath(repoDir.toPath().relativize(path).toString().replace(PATH_SEPARATOR_REGEX, PATH_SEPARATOR)).call();
                                         Date lastModification = null;
                                         for (RevCommit c : commits) {
                                             Date commitDate = c.getAuthorIdent().getWhen();
                                             if (commitDate.before(releaseDate) || commitDate.equals(releaseDate)) {
                                                 lastModification = commitDate;
                                                 break; // il primo commit nella log è il più recente
                                             }
                                         }
                                         if (lastModification != null) {
                                             long diffMillis = releaseDate.getTime() - lastModification.getTime();
                                             tslc = diffMillis / (1000 * 60 * 60 * 24);
                                         } else {
                                             tslc = -1;
                                         }
                                     } catch (Exception _) {
                                         tslc = -1;
                                     }
                                     int fanOut = method.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class).size();

                                     boolean buggy = false;
                                     try {
                                         int methodStart = method.getBegin().get().line;
                                         int methodEnd = method.getEnd().get().line;
                                         boolean matchFound = false;
                                         String currentNormalized = repoDir.toPath().relativize(path).toString().replace(PATH_SEPARATOR_REGEX, PATH_SEPARATOR);
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
                                                         String normalizedModified = modifiedPath.replace(PATH_SEPARATOR_REGEX, PATH_SEPARATOR);
                                                         if (normalizedModified.equals(currentNormalized)) {
                                                             List<org.eclipse.jgit.diff.Edit> edits = df.toFileHeader(diff).toEditList();
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


