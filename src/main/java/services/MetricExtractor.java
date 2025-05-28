package services;

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
    private static final String JAVA_EXTENSION = ".java";

    public static void main(String[] args) throws Exception {
        extractMetrics();
    }

    private static void extractMetrics() throws MetricExtractionException {
        // Percorso della repo locale (modifica se necessario)
        File repoDir = new File("/Users/colaf/Documents/ISW2/bookkeeper/bookkeeper_ISW2/");
        try (Git git = Git.open(new File(repoDir, ".git"))) {

            Map<String, String> bugTickets = JiraTicketFetcher.fetchFixedBugTickets("BOOKKEEPER");
            Map<String, List<RevCommit>> ticketCommits = BugCommitMatcher.mapTicketsToCommits(bugTickets, git);
            Map<String, List<Long>> buggyFileHistory = BuggyFileTracker.collectBuggyFileHistory(ticketCommits, git);

            JavaParser parser = new JavaParser();
            HistoricalMetricsExtractor historicalExtractor = new HistoricalMetricsExtractor();

            File outputFile = new File("metrics_dataset.csv");
            boolean fileExists = outputFile.exists();
            PrintWriter writer = new PrintWriter(new FileWriter(outputFile, false));

            writer.println("Method,ReleaseId,LOC,ParamCount,Statements,Cyclomatic,Nesting,Cognitive,Smells,Modifications,Authors,NameLength,TSLC,FanOut,Buggy,File");

            BufferedReader br = null;
            try {
                br = new BufferedReader(new FileReader("BOOKKEEPERVersionInfo.csv"));
                String line = br.readLine(); // skip header
                while ((line = br.readLine()) != null) {
                    String[] tokens = line.split(",");
                    if (tokens.length < 4) continue;
                    String releaseId = tokens[2].trim();
                    String dateStr = tokens[3].split("T")[0];
                    Date releaseDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);

                    git.checkout().setName("master").call();
                    RevCommit bestCommit = null;
                    for (RevCommit commit : git.log().call()) {
                        Date commitDate = commit.getAuthorIdent().getWhen();
                        if (commitDate.before(releaseDate) || commitDate.equals(releaseDate)) {
                            if (bestCommit == null || commitDate.after(bestCommit.getAuthorIdent().getWhen())) {
                                bestCommit = commit;
                            }
                        }
                    }

                    if (bestCommit == null) {
                        System.out.println("No commit found for release " + releaseId);
                        continue;
                    }

                    git.checkout().setName(bestCommit.getName()).call();
                    System.out.println("Checked out release " + releaseId + " at commit " + bestCommit.getName());

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
                                             String normalizedPath = repoDir.toPath().relativize(path).toString().replace("\\", "/");
                                             Iterable<RevCommit> commits = git.log().addPath(normalizedPath).call();
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
                                         } catch (Exception e) {
                                             tslc = -1;
                                         }
                                         int fanOut = method.findAll(com.github.javaparser.ast.expr.MethodCallExpr.class).size();

                                         String normalizedPath = repoDir.toPath().relativize(path).toString().replace("\\", "/");
                                         boolean buggy = false;
                                         try {
                                             int methodStart = method.getBegin().get().line;
                                             int methodEnd = method.getEnd().get().line;
                                             for (RevCommit commit : ticketCommits.values().stream().flatMap(List::stream).toList()) {
                                                 if (commit.getCommitTime() * 1000L > releaseDate.getTime()) continue;

                                                 if (commit.getParentCount() == 0) continue;
                                                 RevCommit parent = commit.getParent(0);

                                                 try (org.eclipse.jgit.diff.DiffFormatter df = new org.eclipse.jgit.diff.DiffFormatter(org.eclipse.jgit.util.io.DisabledOutputStream.INSTANCE)) {
                                                     df.setRepository(git.getRepository());
                                                     df.setDetectRenames(true);
                                                     java.util.List<org.eclipse.jgit.diff.DiffEntry> diffs = df.scan(parent.getTree(), commit.getTree());

                                                     for (org.eclipse.jgit.diff.DiffEntry diff : diffs) {
                                                         String modifiedPath = diff.getNewPath();
                                                         if (!modifiedPath.endsWith(JAVA_EXTENSION)) continue;

                                                         String normalizedModified = modifiedPath.replace("\\", "/");
                                                         String currentNormalized = repoDir.toPath().relativize(path).toString().replace("\\", "/");

                                                         if (normalizedModified.equals(currentNormalized)) {
                                                             List<org.eclipse.jgit.diff.Edit> edits = df.toFileHeader(diff).toEditList();
                                                             for (org.eclipse.jgit.diff.Edit edit : edits) {
                                                                 int editStart = edit.getBeginB();
                                                                 int editEnd = edit.getEndB();
                                                                 if (editEnd >= methodStart && editStart <= methodEnd) {
                                                                     buggy = true;
                                                                     break;
                                                                 }
                                                             }
                                                             if (buggy) break;
                                                         }
                                                     }
                                                 }
                                                 if (buggy) break;
                                             }
                                         } catch (Exception e) {
                                             buggy = false;
                                         }

                                         try {
                                             HistoricalMetricsExtractor.HistoricalMetrics historical = historicalExtractor.extract(path, releaseDate, git);                                     int modifications = historical.modifications;
                                             int authors = historical.authors.size();
                                            writer.println(
                                                methodName + "," + releaseId + "," + loc + "," + paramCount + "," + statements + "," +
                                                cyclomatic + "," + nesting + "," + cognitive + "," + smells + "," + modifications + "," +
                                                authors + "," + nameLength + "," + tslc + "," + fanOut + "," + (buggy ? 1 : 0) + "," + path.toString()
                                            );
                                         } catch (IOException | org.eclipse.jgit.api.errors.GitAPIException e) {
                                             System.err.println("Errore nelle metriche storiche per " + methodName + ": " + e.getMessage());
                                         }
                                     });
                                 } catch (IOException e) {
                                     System.err.println("Errore nel parsing: " + path);
                                 }
                             });
                    }
                    writer.flush();
                }
                writer.close();
                br.close();
            } catch (Exception e) {
                throw new MetricExtractionException("Errore durante l'estrazione delle metriche", e);
            }
        } catch (IOException e) {
            throw new MetricExtractionException("Errore durante l'apertura della repository Git", e);
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

            int count = 0;
            for (var violation : report.getViolations()) {
                count++;
            }
            return count;
        } catch (Exception e) {
            System.err.println("Errore nell'analisi PMD: " + e.getMessage());
            return 0;
        }
    }
}
