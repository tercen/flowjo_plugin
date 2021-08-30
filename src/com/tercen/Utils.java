package com.tercen;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;

import com.tercen.client.impl.TercenClient;
import com.tercen.model.impl.CSVFileMetadata;
import com.tercen.model.impl.CSVTask;
import com.tercen.model.impl.FailedState;
import com.tercen.model.impl.FileDocument;
import com.tercen.model.impl.InitState;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.ProjectDocument;
import com.tercen.model.impl.Schema;
import com.tercen.service.ServiceError;
import com.treestar.flowjo.engine.auth.fjcloud.CloudAuthInfo;

public class Utils {

	private static final String Separator = "\\";

	protected static LinkedHashMap uploadZipFile(TercenClient client, Project project, String fullFileName)
			throws ServiceError, IOException {

		FileDocument fileDoc = new FileDocument();
		String filename = getFilename(fullFileName);
		String ext = filename.substring(filename.lastIndexOf("."));
		String outFilename = filename.replace(ext, ".zip");
		fileDoc.name = outFilename;
		fileDoc.projectId = project.id;
		fileDoc.acl.owner = project.acl.owner;
		fileDoc.metadata.contentType = "application/zip";
		fileDoc.metadata.contentEncoding = "zip,iso-8859-1";
		File file = new File(fullFileName);

		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ZipOutputStream zos = new ZipOutputStream(bos);
		ZipEntry entry = new ZipEntry(file.getName());
		byte[] bytes = FileUtils.readFileToByteArray(file);
		zos.putNextEntry(entry);
		zos.write(bytes, 0, bytes.length);
		zos.closeEntry();
		zos.close();

		byte[] zipBytes = bos.toByteArray();
		bos.close();
		// remove existing file and upload new file
		removeProjectFileIfExists(client, project, outFilename);
		FileDocument fileResult = client.fileService.upload(fileDoc, zipBytes);

		return fileResult.toJson();
	}

	protected static Schema uploadCsvFile(TercenClient client, Project project, HashSet<String> fileNames,
			ArrayList<String> channels) throws ServiceError, IOException {

		FileDocument fileDoc = new FileDocument();
		String name = getFilename((String) fileNames.toArray()[0]);
		fileDoc.name = name;
		fileDoc.projectId = project.id;
		fileDoc.acl.owner = project.acl.owner;
		fileDoc.metadata = new CSVFileMetadata();
		fileDoc.metadata.contentType = "text/csv";
		fileDoc.metadata.separator = ",";
		fileDoc.metadata.quote = "\"";
		fileDoc.metadata.contentEncoding = "iso-8859-1";

		File mergedFile = getMergedFile(fileNames);
		byte[] bytes = FileUtils.readFileToByteArray(mergedFile);

		// remove existing file and upload new file
		removeProjectFileIfExists(client, project, name);
		fileDoc = client.fileService.upload(fileDoc, bytes);

		// create task; this will create a dataset from the file on Tercen
		CSVTask task = new CSVTask();
		task.state = new InitState();
		task.fileDocumentId = fileDoc.id;
		task.owner = project.acl.owner;
		task.projectId = project.id;
		task.params.separator = ",";
		task.params.encoding = "iso-8859-1";
		task.params.quote = "\"";
		task.gatherNames = channels;
		task.valueName = "value";
		task.variableName = "channel";
		task = (CSVTask) client.taskService.create(task);
		client.taskService.runTask(task.id);
		task = (CSVTask) client.taskService.waitDone(task.id);
		if (task.state instanceof FailedState) {
			throw new ServiceError(task.state.toString());
		}
		return client.tableSchemaService.get(task.schemaId);
	}

	// get Tercen URL that creates a new workflow given the uploaded data
	protected static String getTercenProjectURL(String hostName, String teamName, Schema schema) {
		return hostName + teamName + "/p/" + schema.projectId + "?action=new.workflow&schemaId=" + schema.id;
	}

	protected static List<Object> getClientAndProject(String url, String teamName, String projectName, String domain,
			String username, String password) throws ServiceError {
		TercenClient client = new TercenClient(url);
		client.userService.connect2(domain, username, password);
		Project project = getProject(client, teamName, projectName);
		return Arrays.asList(client, project);
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
		String[] filenameParts = fullFileName.replaceAll(Pattern.quote(Utils.Separator), "\\\\").split("\\\\");
		String filename = filenameParts[filenameParts.length - 1];
		return filename.replace("..ExtNode.csv", "");
	}

	private static String getCurrentPortalUser() {
		String result = "unknown";
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
}
