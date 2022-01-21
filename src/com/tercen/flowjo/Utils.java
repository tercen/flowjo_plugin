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
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Queue;
import java.util.Random;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.http.client.ClientProtocolException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tercen.client.impl.TercenClient;
import com.tercen.flowjo.comparator.SampleComparator;
import com.tercen.flowjo.tasks.UploadProgressTask;
import com.tercen.model.base.Vocabulary;
import com.tercen.model.impl.CSVFileMetadata;
import com.tercen.model.impl.FileDocument;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.ProjectDocument;
import com.tercen.model.impl.Schema;
import com.tercen.model.impl.User;
import com.tercen.model.impl.UserSession;
import com.tercen.model.impl.Version;
import com.tercen.service.ServiceError;
import com.treestar.flowjo.application.workspace.Workspace;
import com.treestar.flowjo.core.Sample;
import com.treestar.flowjo.core.nodes.AppNode;
import com.treestar.flowjo.core.nodes.SampleNode;
import com.treestar.flowjo.core.nodes.templating.ExternalPopNode;
import com.treestar.flowjo.engine.EngineManager;
import com.treestar.flowjo.engine.auth.fjcloud.CloudAuthInfo;

public class Utils {

	private static final Logger logger = LogManager.getLogger();
	private static final String SESSION_FILE_NAME = "session.ser";
	private static final String SESSION_FOLDER_NAME = ".tercen";
	private static final String SEPARATOR = "\\";
	private static final int MIN_BLOCKSIZE = 1024 * 1024;

	public static Schema uploadCsvFile(Tercen plugin, TercenClient client, Project project,
			LinkedHashSet<String> fileNames, ArrayList<String> channels, UploadProgressTask uploadProgressTask,
			String dataTableName) throws ServiceError, IOException {

		FileDocument fileDoc = new FileDocument();
		String name = dataTableName;
		fileDoc.name = name;
		fileDoc.projectId = project.id;
		fileDoc.acl.owner = project.acl.owner;
		CSVFileMetadata metadata = new CSVFileMetadata();
		metadata.contentType = "text/csv";
		metadata.separator = ",";
		metadata.quote = "\"";
		metadata.contentEncoding = "iso-8859-1";
		fileDoc.metadata = metadata;

		File mergedFile = getMergedAndDownSampledFile(fileNames, channels, plugin, uploadProgressTask);

		// remove existing file and upload new file
		removeProjectFileIfExists(client, project, name);

		int blockSize = getBlockSize(mergedFile);
		int iterations = (int) (mergedFile.length() / blockSize);
		uploadProgressTask.setIterations(iterations);
		uploadProgressTask.showDialog();
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

	protected static String getTercenProjectURL(String hostName, String teamName, Schema schema) {
		return hostName + teamName + "/p/" + schema.projectId;
	}

	protected static String getTercenCreateWorkflowURL(TercenClient client, String hostName, String userId,
			Schema schema, Workspace wsp) throws UnsupportedEncodingException, ServiceError {
		String url = hostName + userId + "/p/" + schema.projectId + "?action=new.workflow&tags=flowjo&schemaId="
				+ schema.id + "&client=tercen.flowjo.plugin&workflow.name="
				+ Utils.getWorkflowName(wsp).replace(" ", "_");
		logger.debug("Tercen create workflow URL:" + url);
		return url + Utils.addToken(client, userId, false);
	}

	protected static String addToken(TercenClient client, String userId, boolean onlyParam) throws ServiceError {
		String addParamStr = onlyParam ? "?" : "&";
		return String.format("%stoken=%s", addParamStr, Utils.createTemporaryToken(client, userId));
	}

	private static String getWorkflowName(Workspace wsp) {
		return getWorkspaceName(wsp) + "_" + Utils.getCurrentLocalDateTimeStampShort();
	}

	protected static List<User> getTercenUser(TercenClient client, String user) throws ServiceError {
		List<String> usernames = Arrays.asList(user);
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
	private static File getMergedAndDownSampledFile(LinkedHashSet<String> paths, ArrayList<String> channels,
			Tercen plugin, UploadProgressTask uploadProgressTask) throws IOException {
		List<String> mergedLines = new ArrayList<>();
		logger.debug(String.format("Create upload file from %d sample files", paths.size()));
		for (String p : paths) {
			List<String> lines = Files.readAllLines(Paths.get(p), Charset.forName("UTF-8"));
			if (!lines.isEmpty()) {
				// add header only once
				if (mergedLines.isEmpty()) {
					List<String> headerList = Arrays.asList(lines.get(0).split(","));
					String header = headerList.stream().map(s -> s.replace("\"", ""))
							.map(s -> setColumnValue(channels, s)).collect(Collectors.joining(","));
					mergedLines.add(header.concat(", filename"));
				}
				List<String> content = lines.subList(1, lines.size());
				content.replaceAll(s -> s + String.format(", %s", getFilename(p)));
				mergedLines.addAll(content);
			}
		}
		mergedLines = Utils.downsample(mergedLines, plugin.maxDataPoints, plugin.seed, plugin.gui, uploadProgressTask,
				channels.size());

		File mergedFile = File.createTempFile("merged-", ".csv");
		Files.write(mergedFile.toPath(), mergedLines, Charset.forName("UTF-8"));
		logger.debug(String.format("Upload file has %d rows", mergedLines.size()));
		return mergedFile;
	}

	// In some cases FlowJo is generated a csv file with shortened column names.
	// This methods sets the full column name, needed by Tercen.
	private static String setColumnValue(List<String> channels, String input) {
		String result = input;
		List<String> filteredChannels = channels.stream().filter(c -> c.startsWith(input)).collect(Collectors.toList());
		if (filteredChannels.size() == 1) {
			result = filteredChannels.get(0);
		}
		return result;
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
			if (cai != null) {
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

	private static void removeProjectFileIfExists(TercenClient client, Project project, String tableName)
			throws ServiceError {
		List<Object> startKey = List.of(project.id, "2000");
		List<Object> endKey = List.of(project.id, "2100");

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
		return pluginFolder.substring(pluginFolder.lastIndexOf("\\") + 1);
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

	public static List<AppNode> getTercenNodes(AppNode theNode, boolean allNodes, String[] matches) {
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
						if (allNodes) {
							popList.add(appNode);
						} else if (!allNodes && Arrays.stream(matches).anyMatch(annotation::contains)) {
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
			popList = Utils.getTercenNodes(node, true, new String[] {});
		}
		return popList;
	}

	public static List<AppNode> getAllSelectedTercenNodes(Workspace wsp) {
		List<AppNode> popList = new ArrayList<AppNode>();
		TreeSet<Sample> samples = new TreeSet<Sample>(new SampleComparator());
		wsp.getSampleMgr().getSamples().stream().forEach(samples::add);
		for (Sample sam : samples) {
			SampleNode node = sam.getSampleNode();
			List<AppNode> tempList = Utils.getTercenNodes(node, false,
					new String[] { "Selected", "Uploaded", "ServiceError" });
			popList.addAll(tempList);
		}
		return popList;
	}

	public static List<AppNode> getAllUploadingTercenNodes(Workspace wsp) {
		List<AppNode> popList = new ArrayList<AppNode>();
		TreeSet<Sample> samples = new TreeSet<Sample>(new SampleComparator());
		wsp.getSampleMgr().getSamples().stream().forEach(samples::add);
		for (Sample sam : samples) {
			SampleNode node = sam.getSampleNode();
			List<AppNode> tempList = Utils.getTercenNodes(node, false, new String[] { "Uploading" });
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

	public static boolean isWindows() {
		if (EngineManager.isWindows()) {
			return true;
		} else {
			return false;
		}
	}

	protected static void saveTercenSession(UserSession session) throws IOException {
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
			passWord = gui.getTercenPassword(userNameOrEmail);
		}
		return client.userService.connect2(Tercen.DOMAIN, userNameOrEmail, passWord);
	}

	private static String createTemporaryToken(TercenClient client, String userId) throws ServiceError {
		return client.userService.createToken(userId, 5);
	}

	private static List<String> downsample(List<String> lines, long maxDataPoints, long seed, TercenGUI gui,
			UploadProgressTask uploadProgressTask, int channelSize) {
		List<String> result = new ArrayList<>();
		if (lines != null && lines.size() >= 1) {
			result.add(lines.get(0).concat(", random_label")); // header
			int ncols = result.get(0).split(",").length;

			List<String> content = lines.subList(1, lines.size());
			int nrows = content.size();
			List<String> contentResult = content;
			Random random = seed == -1 ? new Random() : new Random(seed);
			if (maxDataPoints != -1 && (nrows * ncols) > maxDataPoints) {
				int maxRows = Math.round(maxDataPoints / ncols);
				logger.debug(String.format("Downsample data from %d to %d rows", nrows, maxRows));
				uploadProgressTask.setMessage(String.format(
						"Downsampling from %d to %d events across all files with %d channels, %d datapoints.", nrows,
						maxRows, channelSize, maxDataPoints));
				double fraction = (double) 100 * maxRows / (double) nrows;
				contentResult.replaceAll(s -> s + "," + 100 * random.nextDouble());
				contentResult = contentResult.stream().filter(s -> {
					int i = s.lastIndexOf(",");
					double d = Double.valueOf(s.substring(i + 1));
					boolean value = (d < fraction) ? true : false;
					return value;
				}).collect(Collectors.toList());
			} else {
				logger.debug("Add random_label column");
				contentResult.replaceAll(s -> s + "," + 100 * random.nextDouble());
			}
			result.addAll(contentResult);
		}
		return result;
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

	protected static String getProjectVersion() {
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

	public static UserSession getAndExtendTercenSession(TercenClient client, TercenGUI gui, String passWord)
			throws ClassNotFoundException, IOException, ServiceError {
		UserSession session = Utils.getTercenSession();
		if (session == null || !client.userService.isTokenValid(session.token.token)) {
			session = Utils.reconnect(client, gui, session.user.id, passWord);
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

	private static String getStatusFilename(String sampleFileName) {
		return sampleFileName.replace(".csv", "-status.txt");
	}

	protected static String getStatusFromFile(File sampleFile) {
		String result = null;
		try {
			Path filePath = Paths.get(getStatusFilename(sampleFile.toString()));
			if (new File(filePath.toString()).exists()) {
				result = Files.readAllLines(filePath).get(0);
			}
		} catch (IOException e) {
			logger.error("Could not read status file"); // handle
		}
		logger.debug(String.format("getStatusFromFile: %s", result));
		return result;
	}

	protected static void writeStatusFile(String sampleFileName, String status) {
		List<String> content = new ArrayList<String>(Arrays.asList(status));
		String filename = getStatusFilename(sampleFileName);
		File statusFile = new File(filename);
		try {
			Files.write(statusFile.toPath(), content, Charset.forName("UTF-8"));
		} catch (IOException e) {
			logger.error("Could not write status file");
		}
		logger.debug(String.format("Status file written %s", filename));
	}

	protected static void removeStatusFile(File sampleFile) {
		String filename = getStatusFilename(sampleFile.toString());
		try {
			Files.delete(new File(filename).toPath());
		} catch (IOException e) {
			logger.error("Could not remove status file:" + sampleFile.toString());
		}
	}
}
