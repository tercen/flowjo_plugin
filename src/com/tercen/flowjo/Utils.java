package com.tercen.flowjo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.regex.Pattern;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tercen.client.impl.TercenClient;
import com.tercen.model.base.Vocabulary;
import com.tercen.model.impl.CSVFileMetadata;
import com.tercen.model.impl.FileDocument;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.ProjectDocument;
import com.tercen.model.impl.Schema;
import com.tercen.model.impl.User;
import com.tercen.model.impl.UserSession;
import com.tercen.service.ServiceError;
import com.treestar.flowjo.application.workspace.Workspace;
import com.treestar.flowjo.core.Sample;
import com.treestar.flowjo.core.SampleList;
import com.treestar.flowjo.core.nodes.AppNode;
import com.treestar.flowjo.core.nodes.SampleNode;
import com.treestar.flowjo.core.nodes.templating.ExternalPopNode;
import com.treestar.flowjo.engine.auth.fjcloud.CloudAuthInfo;

public class Utils {

	private static final String SESSION_FILE_NAME = "session.ser";
	private static final String SESSION_FOLDER_NAME = ".tercen";
	private static final String SEPARATOR = "\\";
	private static final int MIN_BLOCKSIZE = 1024 * 1024;

	public static Schema uploadCsvFile(TercenClient client, Project project, HashSet<String> fileNames,
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
	protected static String getTercenProjectURL(String hostName, String teamName, Schema schema)
			throws UnsupportedEncodingException {
		return hostName + teamName + "/p/" + schema.projectId + "?action=new.workflow&tag=flowjo&schemaId=" + schema.id
				+ "&client=tercen.flowjo.plugin&workflow.name=" + Utils.getWorkflowName();
	}

	private static String getWorkflowName() {
		return "FlowJo-Tercen-Plug-in-workflow";
	}

	protected static List<User> getTercenUser(TercenClient client, String user) throws ServiceError {
		List<String> usernames = new ArrayList<String>();
		usernames.add(user);
		return client.userService.findUserByEmail(usernames, false);
	}

	protected static UserSession createTercenUser(TercenClient client, String userName, String email, String password)
			throws ServiceError {
		LinkedHashMap userProperties = new LinkedHashMap();
		userProperties.put(Vocabulary.KIND, Vocabulary.User_CLASS);
		userProperties.put(Vocabulary.isDeleted_DP, false);
		userProperties.put(Vocabulary.isPublic_DP, false);
		userProperties.put(Vocabulary.isValidated_DP, false);
		userProperties.put(Vocabulary.invitationCounts_DP, 0);
		userProperties.put(Vocabulary.maxInvitation_DP, 0);
		userProperties.put(Vocabulary.name_DP, userName);
		userProperties.put(Vocabulary.email_DP, email);
		User newUser = User.createFromJson(userProperties);
		User user = client.userService.createUser(newUser, password);
		return client.userService.connect2(Tercen.DOMAIN, userName, password);
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

	public static Project getProject(TercenClient client, String teamOrUser, String projectName) throws ServiceError {

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

	public static String getWorkspaceName(Workspace wsp) {
		String pluginFolder = wsp.getPluginFolder();
		return pluginFolder.substring(pluginFolder.lastIndexOf("\\") + 1);
	}

	public static String getTercenProjectName(Workspace wsp) {
		return getWorkspaceName(wsp) + "_" + Utils.getCurrentLocalDateTimeStamp();
	}

	public static String getCurrentLocalDateTimeStamp() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss"));
	}

	public static String toJson(Map map) throws JsonProcessingException {
		return new ObjectMapper().writeValueAsString(map);
	}

	public static List<AppNode> getTercenNodes(AppNode theNode, boolean selectedOrUploaded) {
		List<AppNode> popList = new ArrayList<AppNode>();
		Queue<AppNode> q = new LinkedList<>();
		q.add(theNode);
		while (!q.isEmpty()) {
			int l = q.size();
			// If this node has children
			while (l > 0) {
				AppNode appNode = q.peek();
				q.remove();
				if (!appNode.isSampleNode() && appNode.getParent() != null) {
					if (appNode instanceof ExternalPopNode && appNode.getName().contains(Tercen.pluginName)) {
						if (!selectedOrUploaded
								|| (selectedOrUploaded && (appNode.getAnnotation().equalsIgnoreCase("Selected")
										|| appNode.getAnnotation().startsWith("Uploaded")))) {
							popList.add(appNode);
						}
					}
				}
				// Put all children of the node in a list:
				for (int i = 0; i < appNode.getChildren().size(); i++)
					q.add(appNode.getChild(i));
				l--;
			}
		}
		return popList;
	}

	public static List<AppNode> getTercenNodes(Sample sample) {
		List<AppNode> popList = new ArrayList<AppNode>();
		if (sample != null) {
			SampleNode node = sample.getSampleNode();
			popList = Utils.getTercenNodes(node, false);
		}
		return popList;
	}

	public static List<AppNode> getAllSelectedTercenNodes(Workspace wsp) {
		List<AppNode> popList = new ArrayList<AppNode>();
		SampleList sampleList = wsp.getSampleMgr().getSamples();
		for (Sample sam : sampleList) {
			SampleNode node = sam.getSampleNode();
			List<AppNode> tempList = Utils.getTercenNodes(node, true);
			popList.addAll(tempList);
		}
		return popList;
	}

	public static String getCsvFileName(AppNode appNode) {
		return appNode.getElement().getChild(Tercen.pluginName).getAttribute(Tercen.CSV_FILE_NAME);
	}

	private static Path getSessionFilePath() {
		FileSystem fs = FileSystems.getDefault();
		String home = System.getProperty("user.home");
		return fs.getPath(home, SESSION_FOLDER_NAME, SESSION_FILE_NAME);
	}

	public static void saveTercenSession(UserSession session) throws IOException {
		Path tokenPath = Utils.getSessionFilePath();
		Path folderPath = tokenPath.getParent();
		Files.createDirectories(folderPath);
		Files.setAttribute(folderPath, "dos:hidden", true);
		new ObjectOutputStream(new FileOutputStream(tokenPath.toString())).writeObject(session.toJson());
	}

	public static UserSession getTercenSession() throws IOException, ClassNotFoundException {
		UserSession result = null;
		Path tokenPath = Utils.getSessionFilePath();
		if (new File(tokenPath.toString()).exists()) {
			LinkedHashMap map = (LinkedHashMap) new ObjectInputStream(new FileInputStream(tokenPath.toString()))
					.readObject();
			result = UserSession.createFromJson(map);
		}
		return result;
	}

	public static UserSession reconnect(TercenClient client, TercenGUI gui, String userName, String passWord)
			throws ServiceError {
		if (passWord == null) {
			passWord = gui.getTercenPassword();
		}
		return client.userService.connect2(Tercen.DOMAIN, userName, passWord);
	}
}
