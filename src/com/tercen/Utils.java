package com.tercen;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;

import com.tercen.client.impl.TercenClient;
import com.tercen.model.impl.CSVFileMetadata;
import com.tercen.model.impl.FileDocument;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.ProjectDocument;
import com.tercen.service.ServiceError;

public class Utils {
	
	private static final String Separator = "\\";

	private static String getFilename(String fullFileName) {
		String[] filenameParts = fullFileName.replaceAll(Pattern.quote(Utils.Separator), "\\\\").split("\\\\");
		return filenameParts[filenameParts.length - 1];
	}
	
	private static Project getProject(TercenClient client, String teamOrUser, String projectName)
			throws ServiceError {

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
	
	private static void removeProjectFileIfExists(TercenClient client, Project project, String filename) throws ServiceError {
		List<ProjectDocument> projectDocs = client.projectDocumentService.findProjectObjectsByLastModifiedDate(null, null, 100, 0, true, false);
		if (projectDocs.size() > 0) {
			projectDocs = projectDocs
				  .stream()
				  .filter(p -> p.projectId.equals(project.id))
				  .filter(p -> p.name.equals(filename))
				  .collect(Collectors.toList());
			
			projectDocs.forEach(p ->  {
				try {
					client.fileService.delete(p.id, p.rev);
				} catch (ServiceError e) {
					e.printStackTrace();
				}
			});
		}
	}
	
	public static LinkedHashMap uploadZipFile(String url, String teamName, String projectName, String domain, String username, String password, String fullFileName) throws ServiceError, IOException {
		// Write data to tercen
		TercenClient client = new TercenClient(url);
		client.userService.connect2(domain, username, password);
		Project	project = getProject(client, teamName, projectName);
			
		FileDocument fileDoc = new FileDocument();
		String filename = getFilename(fullFileName);
		String outFilename =  filename.replace(".fcs", ".zip");
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
	
	public static LinkedHashMap uploadCsvFile(String url, String teamName, String projectName, String domain, String username, String password, String fullFileName) throws ServiceError, IOException {
		// Write data to tercen
		TercenClient client = new TercenClient(url);
		client.userService.connect2(domain, username, password);
		Project	project = getProject(client, teamName, projectName);
			
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
}
