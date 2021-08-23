package com.tercen;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;

import com.tercen.client.impl.TercenClient;
import com.tercen.model.impl.CSVFileMetadata;
import com.tercen.model.impl.FileDocument;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.ProjectDocument;
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

	protected static LinkedHashMap uploadCsvFile(TercenClient client, Project project, String fullFileName)
			throws ServiceError, IOException {

		FileDocument fileDoc = new FileDocument();
		String filename = getFilename(fullFileName);
		fileDoc.name = filename;
		fileDoc.projectId = project.id;
		fileDoc.acl.owner = project.acl.owner;
		fileDoc.metadata = new CSVFileMetadata();
		fileDoc.metadata.contentType = "text/csv";
		fileDoc.metadata.separator = ",";
		fileDoc.metadata.quote = "\"";
		fileDoc.metadata.contentEncoding = "iso-8859-1";
		File file = new File(fullFileName);
		byte[] bytes = FileUtils.readFileToByteArray(file);

		// remove existing file and upload new file
		removeProjectFileIfExists(client, project, filename);
		FileDocument fileResult = client.fileService.upload(fileDoc, bytes);

		return fileResult.toJson();
	}

	protected static String getTercenProjectURL(String hostName, String teamName, String projectId) {
		return hostName + teamName + "/p/" + projectId;
	}

	protected static List<Object> getClientAndProject(String url, String teamName, String projectName, String domain,
			String username, String password) throws ServiceError {
		TercenClient client = new TercenClient(url);
		client.userService.connect2(domain, username, password);
		Project project = getProject(client, teamName, projectName);
		return Arrays.asList(client, project);
	}

	private static String getFilename(String fullFileName) {
		String[] filenameParts = fullFileName.replaceAll(Pattern.quote(Utils.Separator), "\\\\").split("\\\\");
		return filenameParts[filenameParts.length - 1];
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
		projectDocs.stream().filter(p -> p.subKind.equals("FileDocument") && p.name.equals(filename)).forEach(p -> {
			try {
				client.fileService.delete(p.id, p.rev);
			} catch (ServiceError e) {
				e.printStackTrace();
			}
		});
	}
}
