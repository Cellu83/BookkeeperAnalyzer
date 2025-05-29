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
    private static final String PROJECT_NAME = "BOOKKEEPER";
    private static final String API_URL = "https://issues.apache.org/jira/rest/api/2/project/";
    private static final String OUTPUT_FILENAME = PROJECT_NAME + "VersionInfo.csv";

    private static final Map<LocalDateTime, String> releaseNames = new HashMap<>();
    private static final Map<LocalDateTime, String> releaseID = new HashMap<>();
    private static final List<LocalDateTime> releases = new ArrayList<>();

    private ReleaseInfoFetcher() {
        // Prevent instantiation
    }

    public static void main(String[] args) {
        try {
            JSONObject json = readJsonFromUrl(API_URL + PROJECT_NAME);
            JSONArray versions = json.getJSONArray("versions");

            for (int i = 0; i < versions.length(); i++) {
                JSONObject version = versions.getJSONObject(i);
                if (version.has("releaseDate")) {
                    String name = version.optString("name", "");
                    String id = version.optString("id", "");
                    addRelease(version.getString("releaseDate"), name, id);
                }
            }

            releases.sort(LocalDateTime::compareTo);

            int cutoff = (int) Math.ceil(releases.size() * 0.33);
            List<LocalDateTime> datasetReleases = new ArrayList<>(releases.subList(0, cutoff));

            writeVersionInfoToCSV(datasetReleases);

        } catch (IOException | JSONException e) {
            LOGGER.severe("Error during processing: " + e.getMessage());
        }
    }

    private static void addRelease(String strDate, String name, String id) {
        LocalDateTime dateTime = LocalDate.parse(strDate).atStartOfDay();
        if (!releases.contains(dateTime)) {
            releases.add(dateTime);
        }
        releaseNames.put(dateTime, name);
        releaseID.put(dateTime, id);
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

    private static void writeVersionInfoToCSV(List<LocalDateTime> datasetReleases) {
        try (FileWriter fileWriter = new FileWriter(OUTPUT_FILENAME)) {
            fileWriter.append("Index,Version ID,Version Name,Date\n");
            for (int i = 0; i < datasetReleases.size(); i++) {
                LocalDateTime dateTime = datasetReleases.get(i);
                fileWriter.append(String.format("%d,%s,%s,%s%n",
                        i + 1,
                        releaseID.get(dateTime),
                        releaseNames.get(dateTime),
                        dateTime.toString()));
            }
        } catch (IOException e) {
            LOGGER.severe("Error writing CSV: " + e.getMessage());
        }
    }
}