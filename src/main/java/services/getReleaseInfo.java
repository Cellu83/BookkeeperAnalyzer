package services;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.logging.Logger;


public class getReleaseInfo {

	   private static final Logger LOGGER = Logger.getLogger(getReleaseInfo.class.getName());

	   public static HashMap<LocalDateTime, String> releaseNames;
	   public static HashMap<LocalDateTime, String> releaseID;
	   public static ArrayList<LocalDateTime> releases;
	   public static Integer numVersions;

	public static void main(String[] args) throws IOException, JSONException {

		   String projName ="BOOKKEEPER";
		 //Fills the arraylist with releases dates and orders them
		   //Ignores releases with missing dates
		   releases = new ArrayList<LocalDateTime>();
		         Integer i;
		         String url = "https://issues.apache.org/jira/rest/api/2/project/" + projName;
		         JSONObject json = readJsonFromUrl(url);
		         JSONArray versions = json.getJSONArray("versions");
		         releaseNames = new HashMap<LocalDateTime, String>();
		         releaseID = new HashMap<LocalDateTime, String> ();
		         for (i = 0; i < versions.length(); i++ ) {
		            String name = "";
		            String id = "";
		            if(versions.getJSONObject(i).has("releaseDate")) {
		               if (versions.getJSONObject(i).has("name"))
		                  name = versions.getJSONObject(i).get("name").toString();
		               if (versions.getJSONObject(i).has("id"))
		                  id = versions.getJSONObject(i).get("id").toString();
		               addRelease(versions.getJSONObject(i).get("releaseDate").toString(),
		                          name,id);
		            }
		         }
		         // order releases by date
		         Collections.sort(releases, new Comparator<LocalDateTime>(){
		            //@Override
		            public int compare(LocalDateTime o1, LocalDateTime o2) {
		                return o1.compareTo(o2);
		            }
		         });
		         // Keep only the first 33% of releases
		         int cutoff = (int) Math.ceil(releases.size() * 0.33);
		         ArrayList<LocalDateTime> datasetReleases = new ArrayList<>(releases.subList(0, cutoff));

		         FileWriter fileWriter = null;
			 try {
		            fileWriter = null;
		            String outname = projName + "VersionInfo.csv";
						    //Name of CSV for output
						    fileWriter = new FileWriter(outname);
		            fileWriter.append("Index,Version ID,Version Name,Date");
		            fileWriter.append("\n");
		            numVersions = datasetReleases.size();
		            for ( i = 0; i < datasetReleases.size(); i++) {
		               Integer index = i + 1;
		               fileWriter.append(index.toString());
		               fileWriter.append(",");
		               fileWriter.append(releaseID.get(datasetReleases.get(i)));
		               fileWriter.append(",");
		               fileWriter.append(releaseNames.get(datasetReleases.get(i)));
		               fileWriter.append(",");
		               fileWriter.append(datasetReleases.get(i).toString());
		               fileWriter.append("\n");
		            }

		         } catch (Exception e) {
		            LOGGER.severe("Error in csv writer");
		            e.printStackTrace();
		         } finally {
		            try {
		               fileWriter.flush();
		               fileWriter.close();
		            } catch (IOException e) {
		               LOGGER.severe("Error while flushing/closing fileWriter !!!");
		               e.printStackTrace();
		            }
		         }
		         return;
		   }


	   public static void addRelease(String strDate, String name, String id) {
		      LocalDate date = LocalDate.parse(strDate);
		      LocalDateTime dateTime = date.atStartOfDay();
		      if (!releases.contains(dateTime))
		         releases.add(dateTime);
		      releaseNames.put(dateTime, name);
		      releaseID.put(dateTime, id);
		      return;
		   }


	   public static JSONObject readJsonFromUrl(String url) throws IOException, JSONException {
	      InputStream is = new URL(url).openStream();
	      try {
	         BufferedReader rd = new BufferedReader(new InputStreamReader(is, Charset.forName("UTF-8")));
	         String jsonText = readAll(rd);
	         JSONObject json = new JSONObject(jsonText);
	         return json;
	       } finally {
	         is.close();
	       }
	   }

	   private static String readAll(Reader rd) throws IOException {
		      StringBuilder sb = new StringBuilder();
		      int cp;
		      while ((cp = rd.read()) != -1) {
		         sb.append((char) cp);
		      }
		      return sb.toString();
		   }


}