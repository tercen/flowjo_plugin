package com.tercen.flowjo;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import com.tercen.client.impl.TercenClient;
import com.tercen.model.base.Vocabulary;
import com.tercen.model.impl.CSVFileMetadata;
import com.tercen.model.impl.FileDocument;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.ProjectDocument;
import com.tercen.model.impl.Schema;
import com.tercen.model.impl.User;
import com.tercen.service.ServiceError;
import com.treestar.flowjo.application.workspace.Workspace;
import com.treestar.flowjo.engine.auth.fjcloud.CloudAuthInfo;

public class Utils {

	private static final String SEPARATOR = "\\";
	private static final int MIN_BLOCKSIZE = 1024 * 1024;

	protected static Schema uploadCsvFile(TercenClient client, Project project, HashSet<String> fileNames,
			ArrayList<String> channels, UploadProgressTask uploadProgressTask) throws ServiceError, IOException {

		FileDocument fileDoc = new FileDocument();
		String name = getFilename((String) fileNames.toArray()[0]);
		fileDoc.name = name;
		fileDoc.projectId = project.id;
		fileDoc.acl.owner = project.acl.owner;
		CSVFileMetadata metadata = new CSVFileMetadata();
		metadata.contentType = "text/csv";
		metadata.separator = ",";
		metadata.quote = "\"";
		metadata.contentEncoding = "iso-8859-1";
		fileDoc.metadata = metadata;

		File mergedFile = getMergedFile(fileNames);

		// remove existing file and upload new file
		removeProjectFileIfExists(client, project, name);

		int blockSize = getBlockSize(mergedFile);
		int iterations = (int) (mergedFile.length() / blockSize);
		uploadProgressTask.setIterations(iterations);
		uploadProgressTask.setVisible(true);
		return uploadProgressTask.uploadFile(mergedFile, client, project, fileDoc, channels, blockSize);
	}

	private static int getBlockSize(File mergedFile) {
		int blockSize = MIN_BLOCKSIZE;
		int fileSize = (int) mergedFile.length();
		if (fileSize > 10 * (1024 * 1024)) {
			blockSize = fileSize / 10;
		}
		return blockSize;
	}

	// get Tercen URL that creates a new workflow given the uploaded data
	protected static String getTercenProjectURL(String hostName, String teamName, Schema schema) {
		return hostName + teamName + "/p/" + schema.projectId + "?action=new.workflow&tag=flowjo&schemaId=" + schema.id;
	}

	protected static List<User> getTercenUser(TercenClient client, String user) throws ServiceError {
		List<String> usernames = new ArrayList<String>();
		usernames.add(user);
		return client.userService.findUserByEmail(usernames, false);
	}

	protected static User createTercenUser(TercenClient client, String email) throws ServiceError {
		LinkedHashMap userProperties = new LinkedHashMap();
		userProperties.put(Vocabulary.email_DP, email);
		User newUser = new User(userProperties);
		return client.userService.createUser(newUser, "");
	}

	protected static Project getProject(TercenClient client, String teamName, String projectName, String username,
			String password) throws ServiceError {
		client.userService.connect2(Tercen.DOMAIN, username, password);
		return getProject(client, teamName, projectName);
	}

	// merge csv files into one. The filename column is added after reading the
	// data. This might need to be optimized.
	private static File getMergedFile(HashSet<String> paths) throws IOException {
		List<String> mergedLines = new ArrayList<>();
		for (String p : paths) {
			List<String> lines = Files.readAllLines(Paths.get(p), Charset.forName("UTF-8"));
			if (!lines.isEmpty()) {
				if (mergedLines.isEmpty()) {
					mergedLines.add(lines.get(0).concat(", filename")); // add header only once
				}
				List<String> content = lines.subList(1, lines.size());
				content.replaceAll(s -> s + String.format(", %s", getFilename(p)));
				mergedLines.addAll(content);
			}
		}
		// add column filename
		File mergedFile = File.createTempFile("merged-", ".csv");
		Files.write(mergedFile.toPath(), mergedLines, Charset.forName("UTF-8"));
		return mergedFile;
	}

	private static String getFilename(String fullFileName) {
		String[] filenameParts = fullFileName.replaceAll(Pattern.quote(Utils.SEPARATOR), "\\\\").split("\\\\");
		String filename = filenameParts[filenameParts.length - 1];
		return filename.replace("..ExtNode.csv", "");
	}

	protected static String getCurrentPortalUser() {
		String result = null;
		try {
			CloudAuthInfo cai = CloudAuthInfo.getInstance();
			if (cai != null && cai.isLoggedIn()) {
				result = cai.getUsername();
			}
		} catch (Exception e) {
			// default name
		}
		return result;
	}

	private static Project getProject(TercenClient client, String teamOrUser, String projectName) throws ServiceError {

		List<Object> startKey = List.of(teamOrUser, false, "2000");
		List<Object> endKey = List.of(teamOrUser, false, "2100");

		List<Project> projects = client.projectService.findByTeamAndIsPublicAndLastModifiedDate(startKey, endKey, 1000,
				0, false, true);

		Optional<Project> result = projects.stream().filter(p -> p.name.equals(projectName)).findAny();

		if (result.isPresent()) {
			return result.get();
		}

		Project new_project = new Project();
		new_project.name = projectName;
		new_project.acl.owner = teamOrUser;

		return client.projectService.create(new_project);
	}

	private static void removeProjectFileIfExists(TercenClient client, Project project, String filename)
			throws ServiceError {
		List<Object> startKey = List.of(project.id, "2000");
		List<Object> endKey = List.of(project.id, "2100");

		List<ProjectDocument> projectDocs = client.projectDocumentService.findProjectObjectsByLastModifiedDate(startKey,
				endKey, 100, 0, false, false);
		projectDocs.stream().filter(p -> p.subKind.equals("TableSchema") && p.name.equals(filename)).forEach(p -> {
			try {
				client.tableSchemaService.delete(p.id, p.rev);
			} catch (ServiceError e) {
				e.printStackTrace();
			}
		});
	}

	protected static String urlEncodeUTF8(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new UnsupportedOperationException(e);
		}
	}

	public static String getTercenProjectName(Workspace wsp) {
		String pluginFolder = wsp.getPluginFolder();
		String workspaceName = pluginFolder.substring(pluginFolder.lastIndexOf("\\") + 1);
		return workspaceName + "_" + Utils.getCurrentLocalDateTimeStamp();
	}

	public static String getCurrentLocalDateTimeStamp() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd_MM_yyyy_HH_mm_ss"));
	}
}
