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

public class ReleaseInfoFetcher {

    private static final Logger LOGGER = Logger.getLogger(ReleaseInfoFetcher.class.getName());
    private static final String API_URL = "https://issues.apache.org/jira/rest/api/2/project/";

    private static final Map<LocalDateTime, String> releaseNames = new HashMap<>();
    private static final Map<LocalDateTime, String> releaseIds = new HashMap<>();
    private static final List<LocalDateTime> releaseDates = new ArrayList<>();

    private ReleaseInfoFetcher() {
        // Prevent instantiation
    }

    public static void main(String[] args) {
        try {
            String projectName = getProjectName();
            String outputFileName = projectName.toUpperCase() + "VersionInfo.csv";

            JSONObject json = readJsonFromUrl(API_URL + projectName);
            JSONArray versions = json.optJSONArray("versions");
            if (versions != null) {
                for (int i = 0; i < versions.length(); i++) {
                    JSONObject version = versions.optJSONObject(i);
                    if (version != null) {
                        String releaseDate = version.optString("releaseDate", null);
                        if (releaseDate != null && !releaseDate.isEmpty()) {
                            String name = version.optString("name", "");
                            String id = version.optString("id", "");
                            storeReleaseInfo(releaseDate, name, id);
                        }
                    }
                }
            }

            releaseDates.sort(LocalDateTime::compareTo);

            int cutoff = (int) Math.ceil(releaseDates.size() * 0.33);
            List<LocalDateTime> datasetReleases = new ArrayList<>(releaseDates.subList(0, cutoff));

            writeVersionInfoToCSV(datasetReleases, outputFileName);

        } catch (IOException | JSONException e) {
            LOGGER.severe("Error during processing: " + e.getMessage());
        }
    }

    private static String getProjectName() {
        return System.getenv().getOrDefault("PROJECT_NAME", "ZOOKEEPER");
    }

    private static void storeReleaseInfo(String strDate, String name, String id) {
        try {
            LocalDateTime dateTime = LocalDate.parse(strDate).atStartOfDay();
            if (!releaseDates.contains(dateTime)) {
                releaseDates.add(dateTime);
            }
            releaseNames.put(dateTime, name);
            releaseIds.put(dateTime, id);
        } catch (Exception ignored) {
            LOGGER.warning("Invalid release date format: " + strDate);
        }
    }

    private static JSONObject readJsonFromUrl(String urlString) throws IOException, JSONException {
        URI uri = URI.create(urlString);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/json");

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

    private static void writeVersionInfoToCSV(List<LocalDateTime> datasetReleases, String outputFileName) {
        try (FileWriter fileWriter = new FileWriter(outputFileName)) {
            fileWriter.append("Index,Version ID,Version Name,Date\n");
            for (int i = 0; i < datasetReleases.size(); i++) {
                fileWriter.append(formatReleaseAsCSVLine(i, datasetReleases.get(i)));
            }
        } catch (IOException e) {
            LOGGER.severe("Error writing CSV: " + e.getMessage());
        }
    }

    private static String formatReleaseAsCSVLine(int index, LocalDateTime dateTime) {
        return String.format("%d,%s,%s,%s%n",
            index + 1,
            releaseIds.get(dateTime),
            releaseNames.get(dateTime),
            dateTime);
    }
}