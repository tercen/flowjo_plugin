package com.tercen;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.FileUtils;

import com.tercen.client.impl.TercenClient;
import com.tercen.model.impl.CSVFileMetadata;
import com.tercen.model.impl.FileDocument;
import com.tercen.model.impl.Project;
import com.tercen.service.ServiceError;

public class Utils {

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
	
	public static String uploadFile(String url, String teamName, String projectName, String domain, String username, String password, String fileName) throws ServiceError, IOException {
		// Write data to tercen
		TercenClient client = new TercenClient(url);
		client.userService.connect2(domain, username, password);
		Project	project = getProject(client, teamName, projectName);
			
		FileDocument fileDoc = new FileDocument();
		String[] filenameParts = fileName.split("/");
		fileDoc.name = filenameParts[filenameParts.length - 1];
		fileDoc.projectId = project.id;
		fileDoc.acl.owner = project.acl.owner;
		fileDoc.metadata = new CSVFileMetadata();
		fileDoc.metadata.contentType = "text/csv";
		fileDoc.metadata.separator = ",";
		fileDoc.metadata.quote = "\"";
		fileDoc.metadata.contentEncoding = "iso-8859-1";
		File file = new File(fileName);
		byte[] bytes = FileUtils.readFileToByteArray(file);
		FileDocument fileResult = client.fileService.upload(fileDoc, bytes);
		
		return fileResult.toJson().toString();			
	}
	
}
