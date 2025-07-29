/*
package services;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.logging.Logger;





/**
 * Classe per recuperare le informazioni sulle versioni rilasciate da un progetto JIRA.
 * Le informazioni vengono salvate in un file CSV.



public class ReleaseInfoFetcher {

    private static final String API_URL = "https://issues.apache.org/jira/rest/api/2/project/";
    private static final String ENV_PROJECT_NAME = "PROJECT_NAME";
    private static final String DEFAULT_PROJECT = "BOOKKEEPER";
    private static final String CSV_HEADER = "Index,Version ID,Version Name,Date\n";
    private static final String CSV_LINE_FORMAT = "%d,%s,%s,%s%n";
    private static final double DATASET_PERCENTAGE = 0.33;
    private static final Logger LOGGER = Logger.getLogger(ReleaseInfoFetcher.class.getName());

    private static final Map<LocalDateTime, String> releaseNames = new HashMap<>();
    private static final Map<LocalDateTime, String> releaseIds = new HashMap<>();
    private static final List<LocalDateTime> releaseDates = new ArrayList<>();

    private ReleaseInfoFetcher() {
        // Costruttore privato per evitare l'istanziazione
    }

    public static void main(String[] args) {
        try {
            String projectName = getProjectName();
            String outputFileName = projectName.toUpperCase() + "VersionInfo.csv";

            fetchReleaseData(projectName);

            if (releaseDates.isEmpty()) {
                // LOGGER.warning("Nessuna versione trovata con date di rilascio valide");
                return;
            }

            releaseDates.sort(LocalDateTime::compareTo);

            int cutoff = calculateCutoff();
            List<LocalDateTime> datasetReleases = new ArrayList<>(releaseDates.subList(0, cutoff));

            writeVersionInfoToCSV(datasetReleases, outputFileName);
            // LOGGER.info("Informazioni sulle versioni salvate in " + outputFileName);

        } catch (IOException | JSONException e) {
            // LOGGER.log(Level.SEVERE, "Errore durante il recupero o la scrittura delle informazioni sulla versione", e);
        }
    }

    private static String getProjectName() {
        return System.getenv().getOrDefault(ENV_PROJECT_NAME, DEFAULT_PROJECT);
    }

        //Contatto con API di JIRA : ottiene lista versioni
    private static void fetchReleaseData(String projectName) throws IOException, JSONException {
        JSONObject json = readJsonFromUrl(API_URL + projectName);
        JSONArray versions = json.optJSONArray("versions");
        if (versions == null) {
            // LOGGER.warning("Nessuna informazione sulle versioni trovata per " + projectName);
            return;
        }

        processVersions(versions);
    }
    //scorre la lista versioni e verifica releaseDate
    private static void processVersions(JSONArray versions) {
        for (int i = 0; i < versions.length(); i++) {
            JSONObject version = versions.optJSONObject(i);
            String releaseDate = version != null ? version.optString("releaseDate", null) : null;

            if (version != null && releaseDate != null && !releaseDate.isEmpty()) {
                String name = version.optString("name", "");
                String id = version.optString("id", "");
                storeReleaseInfo(releaseDate, name, id);
            }
        }
    }
    //memorizza le info delle versioni con releaseDate
    private static void storeReleaseInfo(String strDate, String name, String id) {
        try {
            LocalDateTime dateTime = LocalDate.parse(strDate).atStartOfDay();
            if (!releaseDates.contains(dateTime)) {
                releaseDates.add(dateTime);
            }
            releaseNames.put(dateTime, name);
            releaseIds.put(dateTime, id);
        } catch (Exception e) {
            // LOGGER.log(Level.WARNING, "Impossibile analizzare la data di rilascio: " + strDate, e);
        }
    }

    private static JSONObject readJsonFromUrl(String urlString) throws IOException, JSONException {
        URI uri = URI.create(urlString);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Richiesta HTTP fallita con codice di risposta: " + responseCode);
        }

        try (InputStream is = connection.getInputStream();
             BufferedReader rd = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

            StringBuilder sb = new StringBuilder();
            int cp;
            while ((cp = rd.read()) != -1) {
                sb.append((char) cp);
            }
            return new JSONObject(sb.toString());
        } finally {
            connection.disconnect();
        }
    }
    //taglia il 66% delle versioni per il dataset
    private static int calculateCutoff() {
        int cutoff = (int) Math.ceil(releaseDates.size() * DATASET_PERCENTAGE);
        // Assicurati che cutoff sia almeno 1 per evitare array vuoti
        return Math.max(1, cutoff);
    }
    //Scrive il csv
    private static void writeVersionInfoToCSV(List<LocalDateTime> datasetReleases, String outputFileName)
            throws IOException {
        try (FileWriter fileWriter = new FileWriter(outputFileName)) {
            fileWriter.append(CSV_HEADER);
            for (int i = 0; i < datasetReleases.size(); i++) {
                fileWriter.append(formatReleaseAsCSVLine(i, datasetReleases.get(i)));
            }
        } catch (IOException e) {
            // LOGGER.log(Level.SEVERE, "Errore durante la scrittura nel file CSV: " + outputFileName, e);
            throw e;
        }
    }
    //scrive la riga del csv
    private static String formatReleaseAsCSVLine(int index, LocalDateTime dateTime) {
        return String.format(CSV_LINE_FORMAT,
                index + 1,
                releaseIds.getOrDefault(dateTime, ""),
                releaseNames.getOrDefault(dateTime, ""),
                dateTime);
    }
}

*/