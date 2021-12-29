package com.tercen.flowjo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.tercen.flowjo.tasks.DownloadProgressTask;

public class Updater {

	private static final Logger logger = LogManager.getLogger(Updater.class);
	private static final String PLUGIN_REPO = "tercen/flowjo_plugin";
	private static final String ARTIFACTS_URL = "https://api.github.com/repos/" + PLUGIN_REPO + "/actions/artifacts";
	private static final String TAGS_URL = "https://api.github.com/repos/" + PLUGIN_REPO + "/tags";

	/**
	 * Checks if there is a new plugin version available. If so, it will download
	 * the newer and stores the JAR file in the plugin directory.
	 * 
	 * @param plugin               the tercen plugin.
	 * @param currentPluginVersion current plugin version.
	 */
	public static void downloadLatestVersion(Tercen plugin, String currentPluginVersion, String gitToken)
			throws IOException, JSONException {
		CloseableHttpClient httpClient = HttpClientBuilder.create().build();

		JSONArray tags = new JSONArray(doRequest(httpClient, TAGS_URL, gitToken));
		JSONObject latestTag = (JSONObject) tags.get(0);
		String latestVersion = (String) latestTag.get("name");
		if (latestVersion.compareTo(currentPluginVersion) > 0) {
			logger.debug("Newer plugin version available! Getting artifacts..");

			JSONObject obj = new JSONObject(doRequest(httpClient, ARTIFACTS_URL, gitToken));
			JSONArray artifacts = (JSONArray) obj.get("artifacts");
			JSONObject latestArtifact = (JSONObject) artifacts.get(0);
			String downloadURL = (String) latestArtifact.get("archive_download_url");

			logger.debug("Downloading latest artifact..");
			String pluginDir = System.getProperty("user.dir");
			String outputPath = Paths.get(pluginDir, "out.zip").toString();

			DownloadProgressTask downloadTask = new DownloadProgressTask(plugin);
			downloadTask.setIterations(2);
			downloadTask.showDialog();

			downloadZipFile(downloadURL, outputPath, gitToken, downloadTask);

			writeJarFile(pluginDir, outputPath, downloadTask);

			boolean removed = new File(outputPath).delete();
			if (removed) {
				logger.debug("Artifact has been removed");
			}
		}
	}

	private static void writeJarFile(String pluginDir, String outputPath, DownloadProgressTask downloadTask)
			throws FileNotFoundException, IOException {
		ZipInputStream zis = new ZipInputStream(new FileInputStream(outputPath));
		ZipEntry zipEntry = zis.getNextEntry();
		if (zipEntry != null) {
			File newFile = newFile(new File(pluginDir), zipEntry);
			FileOutputStream fos = new FileOutputStream(newFile);
			logger.debug(String.format("Write JAR file to: %s", newFile.toString()));
			writeToFileOutputStream(zis, fos);
			downloadTask.setValue(2);
		}
	}

	private static void downloadZipFile(String downloadURL, String outputPath, String gitToken,
			DownloadProgressTask downloadTask) throws IOException {
		URL artifact = new URL(downloadURL);
		URLConnection connection = (URLConnection) artifact.openConnection();
		connection.setRequestProperty("Authorization", "token " + gitToken);
		connection.connect();

		writeToFileOutputStream(connection.getInputStream(), new FileOutputStream(new File(outputPath)));
		downloadTask.setValue(1);

		logger.debug(String.format("Artifact available at: %s", outputPath));
	}

	private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
		File destFile = new File(destinationDir, zipEntry.getName());
		String destDirPath = destinationDir.getCanonicalPath();
		String destFilePath = destFile.getCanonicalPath();

		if (!destFilePath.startsWith(destDirPath + File.separator)) {
			throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
		}
		return destFile;
	}

	private static String doRequest(CloseableHttpClient httpClient, String URL, String gitToken)
			throws ClientProtocolException, IOException {
		HttpGet request = new HttpGet(URL);
		request.addHeader("Authorization", "token " + gitToken);
		request.addHeader("content-type", "application/json");
		HttpResponse result = httpClient.execute(request);
		return EntityUtils.toString(result.getEntity(), "UTF-8");
	}

	private static void writeToFileOutputStream(InputStream is, FileOutputStream fos) throws IOException {
		byte[] buffer = new byte[4096];
		int n = 0;
		while (-1 != (n = is.read(buffer))) {
			fos.write(buffer, 0, n);
		}
		is.close();
		fos.close();
	}
}
