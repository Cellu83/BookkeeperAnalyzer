package services;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.revwalk.RevCommit;

import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

public class BugCommitMatcher {

    private static final Logger LOGGER = Logger.getLogger(BugCommitMatcher.class.getName());

    private BugCommitMatcher() {
        // Utility class
    }

    /**
     * Mappa ciascun ticket ID a una lista di commit che contengono l'ID nel messaggio del commit.
     *
     * @param bugTickets mappa {ticketId → resolutionDate}
     * @param git istanza Git già inizializzata sul repo
     * @return mappa {ticketId → lista di commit che lo menzionano}
     */
    public static Map<String, List<RevCommit>> mapTicketsToCommits(Map<String, String> bugTickets, Git git) throws IOException {
        Map<String, List<RevCommit>> result = new HashMap<>();
        Set<String> matchedTickets = new HashSet<>();
        LOGGER.info("📌 Inizio associazione commit-ticket");
        try {
            Iterable<RevCommit> commits = git.log().call();
            LOGGER.info("📋 Ticket da cercare: " + bugTickets.keySet());
            for (RevCommit commit : commits) {
                LOGGER.info("🔍 Scanning commit: " + commit.getName() + " - " + commit.getFullMessage());
                String message = commit.getFullMessage().toLowerCase();
                for (String ticket : bugTickets.keySet()) {
                    LOGGER.info("🧪 Confronto ticket: \"" + ticket + "\" con messaggio commit...");
                    String normalizedTicket = ticket.toLowerCase();
                    String altFormat = normalizedTicket.replace("-", "_");

                    boolean matched = false;

                    if (message.contains(normalizedTicket) || message.contains(altFormat)) {
                        LOGGER.info("📦 Commit associato al ticket: " + ticket + " -> " + commit.getName());
                        LOGGER.info("   Autore: " + commit.getAuthorIdent().getName());
                        LOGGER.info("   Data: " + commit.getAuthorIdent().getWhen());
                        LOGGER.info("   Messaggio: " + commit.getFullMessage());
                        LOGGER.info("🪲 Match diretto: ticket " + ticket + " nel commit: " + commit.getName());
                        result.computeIfAbsent(ticket, k -> new ArrayList<>()).add(commit);
                        matchedTickets.add(ticket);
                        matched = true;
                    }

                    if (!matched && !matchedTickets.contains(ticket)) {
                        try {
                            String commitAuthor = commit.getAuthorIdent().getName().toLowerCase();
                            Date commitDate = commit.getAuthorIdent().getWhen();
                            String ticketResolutionDate = bugTickets.get(ticket);

                            if (ticketResolutionDate != null) {
                                Date resolutionDate = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(ticketResolutionDate);
                                long diffMillis = Math.abs(commitDate.getTime() - resolutionDate.getTime());
                                long diffDays = diffMillis / (1000 * 60 * 60 * 24);

                                if (diffDays <= 2) {
                                    LOGGER.info("📦 [Euristico] Commit associato al ticket: " + ticket + " -> " + commit.getName());
                                    LOGGER.info("   Autore: " + commit.getAuthorIdent().getName());
                                    LOGGER.info("   Data: " + commit.getAuthorIdent().getWhen());
                                    LOGGER.info("   Messaggio: " + commit.getFullMessage());
                                    LOGGER.info("🪲 Match euristico: ticket " + ticket + " ~ commit " + commit.getName() + " (±" + diffDays + " giorni)");
                                    result.computeIfAbsent(ticket, k -> new ArrayList<>()).add(commit);
                                    matchedTickets.add(ticket);
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.warning("⚠️ Errore nel parsing data per il ticket " + ticket + ": " + e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warning("⚠️ Errore durante la scansione dei commit: " + e.getMessage());
            LOGGER.info("📌 Fine associazione commit-ticket: " + result.size() + " ticket associati a commit");
            throw new IOException("Errore durante la scansione dei commit: " + e.getMessage(), e);
        }
        LOGGER.info("📌 Fine associazione commit-ticket: " + result.size() + " ticket associati a commit");
        Set<String> nonMatched = new HashSet<>(bugTickets.keySet());
        nonMatched.removeAll(matchedTickets);
        LOGGER.info("📋 Ticket NON associati a commit: " + nonMatched.size());
        for (String ticket : nonMatched) {
            LOGGER.info("❌ Nessun commit trovato per ticket: " + ticket);
        }
        return result;
    }
}
