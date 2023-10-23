package com.tercen.flowjo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Queue;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.JOptionPane;

import org.apache.http.client.ClientProtocolException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;
import org.json.JSONObject;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tercen.client.impl.TercenClient;
import com.tercen.flowjo.comparator.SampleComparator;
import com.tercen.flowjo.exception.DataFormatException;
import com.tercen.flowjo.gui.TercenGUI;
import com.tercen.flowjo.tasks.UploadProgressTask;
import com.tercen.model.impl.FileDocument;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.ProjectDocument;
import com.tercen.model.impl.Token;
import com.tercen.model.impl.User;
import com.tercen.model.impl.UserSession;
import com.tercen.model.impl.Version;
import com.tercen.service.ServiceError;
import com.treestar.flowjo.application.workspace.Workspace;
import com.treestar.flowjo.core.Sample;
import com.treestar.flowjo.core.nodes.AppNode;
import com.treestar.flowjo.core.nodes.SampleNode;
import com.treestar.flowjo.core.nodes.templating.ExternalPopNode;
import com.treestar.flowjo.engine.auth.fjcloud.CloudAuthInfo;
import com.treestar.lib.fjml.FJML;

public class Utils {

	private static final Logger logger = LogManager.getLogger();
	private static final String SESSION_FILE_NAME = "session.ser";
	private static final String SESSION_FOLDER_NAME = ".tercen";
	private static final String SEPARATOR = "\\";
	private static final int MIN_BLOCKSIZE = 1024 * 1024;
	public static final String RANDOM_LABEL = "random_label";
	public static final String FILENAME = "filename";
	private static final String SERVICE_ERROR = "ServiceError: ";
	public static final String FLOWJO_ROW_ID = "F_rowId";

	
	public static FileDocument uploadZipFile(Tercen plugin, TercenClient client, Project project,
			LinkedHashSet<String> fileNames, UploadProgressTask uploadProgressTask,
			String dataTableName) throws ServiceError, IOException, DataFormatException {

		FileDocument fileDoc = new FileDocument();
		String name = dataTableName;
		fileDoc.name = name;
		fileDoc.projectId = project.id;
		fileDoc.acl.owner = project.acl.owner;
	
		File mergedFile = createUploadFile(fileNames, plugin, uploadProgressTask);

		// remove existing file and upload new file
		removeProjectFileIfExists(client, project, name);

		int blockSize = getBlockSize(mergedFile);
		int iterations = (int) (mergedFile.length() / blockSize);
		uploadProgressTask.setIterations(iterations);
		uploadProgressTask.showDialog();
		return uploadProgressTask.uploadFile(mergedFile, client, fileDoc, blockSize);
	}
	
	private static int getBlockSize(File mergedFile) {
		int blockSize = MIN_BLOCKSIZE;
		int fileSize = (int) mergedFile.length();
		if (fileSize > 10 * (1024 * 1024)) {
			blockSize = fileSize / 10;
		}
		return blockSize;
	}

	protected static String getTercenProjectURL(String hostName, String teamName, FileDocument fileDoc) {
		return hostName + teamName + "/p/" + fileDoc.projectId;
	}

	protected static String getTercenCreateWorkflowURL(TercenClient client, String hostName, String userId,
			FileDocument fileDoc, Workspace wsp) throws UnsupportedEncodingException, ServiceError {
		// replace schema id by document id, documentId
		String url = hostName + userId + "/p/" + fileDoc.projectId + "?action=new.workflow&tags=flowjo&documentId="
				+ fileDoc.id + "&client=tercen.flowjo.plugin&workflow.name="
				+ Utils.getWorkflowName(wsp).replace(" ", "_");
		logger.debug("Tercen create workflow URL:" + url);
		return url + Utils.addToken(client, userId, false);
	}

	public static String addToken(TercenClient client, String userId, boolean onlyParam) throws ServiceError {
		String addParamStr = onlyParam ? "?" : "&";
		return String.format("%stoken=%s", addParamStr, Utils.createTemporaryToken(client, userId));
	}

	private static String getWorkflowName(Workspace wsp) {
		return getWorkspaceName(wsp) + "_" + Utils.getCurrentLocalDateTimeStampShort();
	}

	protected static List<User> getTercenUser(TercenClient client, String user) throws ServiceError {
		List<User> result = null;
		List<String> usernames = Arrays.asList(user);
		if (user.contains("@")) {
			result = client.userService.findUserByEmail(usernames, false);
		} else {
			result = new ArrayList<User>();
			result.add(client.userService.get(user));
		}
		return (result);
	}

	public static UserSession createTercenUser(TercenClient client, String userName, String email, String password)
			throws ServiceError {
		User newUser = new User();
		newUser.name = userName;
		newUser.email = email;
		client.userService.createUser(newUser, password);
		return client.userService.connect2(Tercen.DOMAIN, userName, password);
	}
	
	// merge fcs files into a zip file
	private static File createUploadFile(LinkedHashSet<String> paths, Tercen plugin, 
			UploadProgressTask uploadProgressTask) throws IOException, DataFormatException {
			int fileCount = paths.size();
			logger.debug(String.format("Create upload file from %d sample files", fileCount));
			// Create zip file
			File mergedFile = File.createTempFile("merged-", ".zip");
			String mergedFilePath = mergedFile.getPath();
			try (ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(mergedFilePath))) {
			    for (String filePath : paths) {
			    	File file = new File(filePath);
			        zipOut.putNextEntry(new ZipEntry(file.getName()));
			        Files.copy(file.toPath(), zipOut);
			    }
			}
			logger.debug("Upload file has been created");
			return (mergedFile);
	}

	// In some cases, the FCS channel name as can be seen in the User Interface is
	// not the same as the colname in the data (e.g. csv) generated by FlowJo. We
	// need to adjust this, otherwise Tercen can't match these columns.
	public static String setColumnName(String input) {
		String result = input;
		if (result != null && result.equalsIgnoreCase("Event #")) {
			result = FJML.EventNumberDP;

		}
		return result;
	}

	protected static String getFilename(String fullFileName) {
		String[] filenameParts = null;
		if (Utils.isWindows()) {
			filenameParts = fullFileName.replaceAll(Pattern.quote(Utils.SEPARATOR), "\\\\").split("\\\\");
		} else {
			filenameParts = fullFileName.split("/");
		}
		String filename = filenameParts[filenameParts.length - 1];
		return filename.replace("..ExtNode.csv", "");
	}

	protected static String getCurrentPortalUser() {
		String result = null;
		try {
			CloudAuthInfo cai = CloudAuthInfo.getInstance();
			if (cai != null) {
				result = cai.getUsername();
			}
		} catch (Exception e) {
			// default name
		}
		return result;
	}

	public static Project getProject(TercenClient client, String teamOrUser, String projectName) throws ServiceError {

		List<Object> startKey = Stream.of(teamOrUser, false, "2000").collect(Collectors.toList());
		List<Object> endKey = Stream.of(teamOrUser, false, "2100").collect(Collectors.toList());

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

	private static void removeProjectFileIfExists(TercenClient client, Project project, String tableName)
			throws ServiceError {
		List<Object> startKey = Stream.of(project.id, "2000").collect(Collectors.toList());
		List<Object> endKey = Stream.of(project.id, "2100").collect(Collectors.toList());

		List<ProjectDocument> projectDocs = client.projectDocumentService.findProjectObjectsByLastModifiedDate(startKey,
				endKey, 100, 0, false, false);
		projectDocs.stream().filter(p -> p.subKind.equals("TableSchema") && p.name.equals(tableName)).forEach(p -> {
			try {
				client.tableSchemaService.delete(p.id, p.rev);
			} catch (ServiceError e) {
				logger.error(e.getMessage());
			}
		});
	}

	public static String urlEncodeUTF8(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new UnsupportedOperationException(e);
		}
	}

	public static String getWorkspaceName(Workspace wsp) {
		String pluginFolder = wsp.getPluginFolder();
		return pluginFolder.substring(pluginFolder.lastIndexOf(File.separator) + 1);
	}

	public static String getTercenProjectName(Workspace wsp) {
		return getWorkspaceName(wsp) + "_" + Utils.getCurrentLocalDateTimeStamp();
	}

	public static String getCurrentLocalDateTimeStamp() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss"));
	}

	public static String getCurrentLocalDateTimeStampShort() {
		return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss"));
	}

	public static String getTercenDataTableName(Workspace wsp) {
		return "Data_" + getWorkspaceName(wsp) + "_" + Utils.getCurrentLocalDateTimeStampShort();
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
						String annotation = appNode.getAnnotation();
						if (!selectedOrUploaded || (selectedOrUploaded && annotation.equalsIgnoreCase("Selected")
								|| annotation.startsWith("Uploaded") || annotation.startsWith("Imported")
								|| annotation.contains("ServiceError"))) {
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
		TreeSet<Sample> samples = new TreeSet<Sample>(new SampleComparator());
		wsp.getSampleMgr().getSamples().stream().forEach(samples::add);
		for (Sample sam : samples) {
			SampleNode node = sam.getSampleNode();
			List<AppNode> tempList = Utils.getTercenNodes(node, true);
			popList.addAll(tempList);
		}
		return popList;
	}

	public static String getSampleFileName(AppNode appNode) {
		return appNode.getElement().getChild(Tercen.pluginName).getAttribute(Tercen.SAMPLE_FILE_NAME);
	}

	public static String getSampleShortFileName(Sample sample) {
		return (sample.getShortFilename().replace(".fcs", ""));
	}

	private static Path getSessionFilePath() {
		FileSystem fs = FileSystems.getDefault();
		String home = System.getProperty("user.home");
		return fs.getPath(home, SESSION_FOLDER_NAME, SESSION_FILE_NAME);
	}

	public static boolean isWindows() {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("win")) {
			return true;
		} else {
			return false;
		}
	}

	protected static void saveTercenSession(UserSession session) throws IOException {
		if (session != null) {
			Path tokenPath = Utils.getSessionFilePath();
			Path folderPath = tokenPath.getParent();
			Files.createDirectories(folderPath);
			if (Utils.isWindows()) {
				Files.setAttribute(folderPath, "dos:hidden", true);
			}
			ObjectOutputStream objStream = new ObjectOutputStream(new FileOutputStream(tokenPath.toString()));
			objStream.writeObject(session.toJson());
			objStream.close();
		}
	}

	public static UserSession getTercenSession() throws IOException, ClassNotFoundException {
		UserSession result = null;
		Path sessionPath = Utils.getSessionFilePath();
		if (new File(sessionPath.toString()).exists()) {
			ObjectInputStream objStream = new ObjectInputStream(new FileInputStream(sessionPath.toString()));
			LinkedHashMap map = (LinkedHashMap) objStream.readObject();
			result = UserSession.createFromJson(map);
			objStream.close();
		}
		return result;
	}

	public static boolean removeTercenSession() {
		boolean result = false;
		Path sessionPath = Utils.getSessionFilePath();
		File session = new File(sessionPath.toString());
		if (session.exists()) {
			result = session.delete();
		}
		return result;
	}

	public static UserSession reconnect(TercenClient client, TercenGUI gui, String userNameOrEmail, String passWord)
			throws ServiceError {
		if (passWord == null) {
			passWord = gui.getTercenPassword(client, userNameOrEmail);
		}
		if (passWord == null) {
			return (null);
		}
		return client.userService.connect2(Tercen.DOMAIN, userNameOrEmail, passWord);
	}

	private static String createTemporaryToken(TercenClient client, String userId) throws ServiceError {
		return client.userService.createToken(userId, 2 * 24 * 3600);
	}

	protected static UserSession getSamlSession(String token) {
		UserSession session = null;
		if (token != null && !token.equals("")) {
			String[] parts = token.split("\\.");
			if (parts.length == 3) {
				JSONObject payload = new JSONObject(Utils.decode(parts[1]));
				JSONObject data = (JSONObject) payload.get("data");
				session = new UserSession();
				session.token = new Token();
				session.token.token = token;
				session.user = new User();
				session.user.id = (String) data.get("u");
			}
		}
		return session;
	}

	public static boolean isNumeric(String strNum) {
		if (strNum == null) {
			return false;
		}
		try {
			long l = Long.valueOf(strNum);
		} catch (NumberFormatException nfe) {
			return false;
		}
		return true;
	}

	public static String getProjectVersion() {
		String result = "0.0.0";
		Properties properties = new Properties();
		try {
			properties.load(Utils.class.getResourceAsStream("/pom.properties"));
			result = properties.getProperty("version");
		} catch (IOException e) {
			logger.error("Pom.properties could not be loaded");
		}
		logger.debug(String.format("Plugin version: %s", result));
		return result;
	}

	public static UserSession getAndExtendTercenSession(TercenClient client, TercenGUI gui, String userName,
			String passWord, UserSession session) throws ClassNotFoundException, IOException, ServiceError {
		if (session == null) {
			session = Utils.getTercenSession();
		}
		if (session == null || !client.userService.isTokenValid(session.token.token)) {
			// if username not set, try to get it from license or otherwise from session
			if (userName == null || userName == "") {
				userName = Utils.getCurrentPortalUser();
			}
			if ((userName == null || userName == "") && session != null) {
				userName = session.user.email;
			}
			session = Utils.reconnect(client, gui, userName, passWord);
			Utils.saveTercenSession(session);
		} else {
			session = Utils.extendTercenSession(client, session);
			Utils.saveTercenSession(session);
		}
		return session;
	}

	private static UserSession extendTercenSession(TercenClient client, UserSession session) throws ServiceError {
		return client.userService.connect2(Tercen.DOMAIN, "", session.token.token);
	}

	protected static boolean isPluginVersionSupported(String pluginVersion, Version version) throws ServiceError {
		boolean result = true;
		String serverPluginVersion = String.format("%s.%s.%s", version.major, version.minor, version.patch);
		logger.debug(String.format("Server supported plugin version: %s", serverPluginVersion));
		if (serverPluginVersion.compareTo(pluginVersion) > 0) {
			result = false;
			logger.warn(String.format("Plugin version (%s) is not compatible with the server (>= %s)", pluginVersion,
					serverPluginVersion));
		}
		return result;
	}

	protected static boolean isPluginOutdated(String pluginVersion, String gitToken)
			throws JSONException, ClientProtocolException, IOException {
		return Updater.newVersionAvailable(pluginVersion, gitToken);
	}

	public static void showInfoDialog(String text, String title) {
		JOptionPane.showMessageDialog(null, text, title, -1);
	}

	public static void showWarningDialog(String text) {
		JOptionPane.showMessageDialog(null, text, "ImportPlugin warning", JOptionPane.WARNING_MESSAGE);
	}

	public static void showErrorDialog(String text) {
		JOptionPane.showMessageDialog(null, text, "ImportPlugin error", JOptionPane.ERROR_MESSAGE);
	}

	protected static String setUserFriendlyErrorMessage(String errorMsg, String hostName) {
		String result = errorMsg;
		if (result.contains("password") && result.contains("error")) {
			result = SERVICE_ERROR + "Invalid Password";
		} else if (result.contains("ConnectException") || result.contains("Service Unavailable")
				|| result.contains("SocketException") || result.contains("Socket closed")) {
			result = SERVICE_ERROR + "Could not connect to " + hostName;
		} else if (result.contains("IOException")) {
			result = SERVICE_ERROR + "Upload failed.";
		} else if (result.contains("Project.unknown")) {
			result = SERVICE_ERROR + "Failed to create project.";
		}
		return (result);
	}

	public static String[] reverseStringArray(String[] array) {
		List<String> arrayList = Arrays.asList(array);
		Collections.reverse(arrayList);
		return arrayList.toArray(new String[0]);
	}

	protected static String decode(String encodedString) {
		return new String(Base64.getUrlDecoder().decode(encodedString));
	}
}
