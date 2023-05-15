package com.tercen.flowjo;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.builder.api.AppenderComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.api.LayoutComponentBuilder;
import org.apache.logging.log4j.core.config.builder.api.RootLoggerComponentBuilder;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.json.JSONException;

import com.tercen.client.impl.TercenClient;
import com.tercen.flowjo.exception.DataFormatException;
import com.tercen.flowjo.gui.TercenGUI;
import com.tercen.flowjo.importer.ImportHelper;
import com.tercen.flowjo.parser.ClusterFileMetaData;
import com.tercen.flowjo.parser.SplitData;
import com.tercen.flowjo.tasks.UploadProgressTask;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.Schema;
import com.tercen.model.impl.User;
import com.tercen.model.impl.UserSession;
import com.tercen.model.impl.Version;
import com.tercen.service.ServiceError;
import com.treestar.flowjo.application.workspace.Workspace;
import com.treestar.flowjo.core.Sample;
import com.treestar.flowjo.core.nodes.AppNode;
import com.treestar.flowjo.engine.utility.ParameterOptionHolder;
import com.treestar.lib.FJPluginHelper;
import com.treestar.lib.PluginHelper;
import com.treestar.lib.core.ExportFileTypes;
import com.treestar.lib.core.ExternalAlgorithmResults;
import com.treestar.lib.core.PopulationPluginInterface;
import com.treestar.lib.parsing.interpreter.CSVReader;
import com.treestar.lib.xml.SElement;

import nu.studer.java.util.OrderedProperties;

public class Tercen extends ParameterOptionHolder implements PopulationPluginInterface {

	private static Logger logger = null;
	private static final String TERCEN_PROPERTIES = "tercen.properties";
	private static final String HOST = "host";
	private static final String MAX_UPLOAD_DATAPOINTS = "max.upload.datapoints";
	private static final String SEED = "seed";
	private static final String AUTO_UPDATE = "autoupdate";
	private static final String FAILED = "Failed";
	private static final String ICON_NAME = "logo.png";
	private static final String PROJECT_URL = "projectURL";
	private static final String PLUGIN_STATE = "pluginState";
	private static final String CHANNELS = "channels";
	private static final String IMPORT_FILE_PATH = "importFilePath";
	private static final String SELECTED_POPULATION = "SelectedPopulation";
	private static final String SELECTED_POPULATIONS = "SelectedPopulations";
	private static final String PATH = "path";

	protected static final String pluginName = "Connector";
	protected static final String version = Utils.getProjectVersion();
	protected static final String CSV_FILE_NAME = "csvFileName";
	protected static final String HOSTNAME_URL = "https://tercen.com/";
	protected static final String MAX_DATAPOINTS_VALUE = "-1";
	protected static final String SEED_VALUE = "42";
	protected static final String AUTO_UPDATE_VALUE = "false";
	protected static final String GIT_TOKEN_VALUE = "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";
	protected static final String DOMAIN = "tercen";

	public enum ImportPluginStateEnum {
		empty, collectingSamples, uploading, uploaded, importing, error;
	}

	protected String hostName = HOSTNAME_URL;
	protected String projectName;
	protected String userName;
	protected String passWord;
	protected String token;
	protected UserSession session;
	private Icon tercenIcon = null;
	protected ArrayList<String> channels = new ArrayList<String>();
	private String csvFileName;
	protected String projectURL;
	protected File importFile;
	protected long seed = -1;
	protected long maxDataPoints = -1;
	protected boolean autoUpdate = false;

	// properties to gather multiple samples
	public ImportPluginStateEnum pluginState = ImportPluginStateEnum.empty;
	public LinkedHashSet<String> samplePops = new LinkedHashSet<String>();
	public LinkedHashSet<String> selectedSamplePops = new LinkedHashSet<String>();
	public TercenGUI gui = new TercenGUI(this);

	static {
		initLogger();
	}

	public Tercen() {
		super(pluginName);
		logger.debug("Read upload properties file");
		readPropertiesFile();
	}

	@Override
	public String getName() {
		return pluginName;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public SElement getElement() {
		SElement result = super.getElement();
		result.setName(pluginName);
		result.setString(CHANNELS, String.join(",", channels));
		result.setString(PLUGIN_STATE, pluginState.toString());
		result.setString(CSV_FILE_NAME, csvFileName);
		result.setString(PROJECT_URL, projectURL);
		if (!this.selectedSamplePops.isEmpty()) {
			SElement pops = new SElement(SELECTED_POPULATIONS);
			for (String pop : this.selectedSamplePops) {
				SElement popElem = new SElement(SELECTED_POPULATION);
				popElem.setString(PATH, pop);
				pops.addContent(popElem);
			}
			result.addContent(pops);
		}
		if (importFile != null) {
			result.setString(IMPORT_FILE_PATH, importFile.getAbsolutePath());
		}
		return result;
	}

	private LinkedHashSet<String> getSelectedSamplePops(SElement element) {
		LinkedHashSet<String> result = new LinkedHashSet<String>();
		SElement pops = element.getChild(SELECTED_POPULATIONS);
		if (pops != null) {
			for (SElement popElem : pops.getChildren(SELECTED_POPULATION)) {
				result.add(popElem.getString(PATH));
			}
		}
		return (result);
	}

	@Override
	public void setElement(SElement element) {
		String channelString = element.getString(CHANNELS);
		if (channelString.equals("")) {
			channels = new ArrayList<String>();
		} else {
			channels = new ArrayList<String>(Arrays.asList(channelString.split(",")));
		}
		this.pluginState = ImportPluginStateEnum.valueOf(element.getString(PLUGIN_STATE));
		this.csvFileName = element.getString(CSV_FILE_NAME);
		this.projectURL = element.getString(PROJECT_URL);
		this.selectedSamplePops = getSelectedSamplePops(element);
		String importFilePath = element.getString(IMPORT_FILE_PATH);
		if (importFile == null && importFilePath != null && !importFilePath.equals("")) {
			importFile = new File(importFilePath);
		}
	}

	@Override
	public List<String> getParameters() {
		return new ArrayList<String>();
	}

	public void setWorkspaceText(List<AppNode> nodeList, String text) {
		for (AppNode appNode : nodeList) {
			if (appNode.isExternalPopNode()) {
				appNode.setAnnotation(text);
			}
		}
	}

	public ExternalAlgorithmResults setWorkspaceUploadText(ExternalAlgorithmResults result, List<AppNode> nodeList,
			String text) {
		if (!text.equals("")) {
			boolean anyAppNodeTextSet = false;
			for (AppNode appNode : nodeList) {
				if (appNode.isExternalPopNode()) {
					// check if file has been selected for upload
					String csvFileName = Utils.getCsvFileName(appNode);
					if (this.selectedSamplePops.contains(csvFileName)) {
						appNode.setAnnotation(text);
						anyAppNodeTextSet = true;
					}
				}
			}
			if (anyAppNodeTextSet) {
				result.setWorkspaceString("Double Click to access your Tercen project.");
			}
		}
		return result;
	}

	@Override
	public ExternalAlgorithmResults invokeAlgorithm(SElement fcmlQueryElement, File sampleFile, File outputFolder) {
		ExternalAlgorithmResults result = new ExternalAlgorithmResults();
		Sample sample = FJPluginHelper.getSample(fcmlQueryElement);
		Workspace wsp = sample.getWorkspace();
		List<AppNode> nodeList = Utils.getTercenNodes(sample);

		String workspaceText = "";
		UploadProgressTask uploadProgressTask = null;

		try {
			if (pluginState == ImportPluginStateEnum.error) {
				result.setErrorMessage("Previous error prevents uploading");
				return result;
			}

			// reset state
			if (pluginState == ImportPluginStateEnum.uploaded) {
				pluginState = ImportPluginStateEnum.collectingSamples;
			}

			String fileName = sampleFile.getPath();
			if (pluginState == ImportPluginStateEnum.empty) {
				csvFileName = fileName;
				pluginState = ImportPluginStateEnum.collectingSamples;
			} else if (pluginState == ImportPluginStateEnum.collectingSamples) {
				csvFileName = fileName;
			} else if (pluginState == ImportPluginStateEnum.uploading) {
				TercenClient client = new TercenClient(hostName);

				// Check plugin supported version
				Version pluginServerVersion = client.userService.getServerVersion("flowjoPlugin");
				if (!Utils.isPluginVersionSupported(version, pluginServerVersion)) {
					Utils.showErrorDialog(String.format(
							"Oops! Your Plugin version (%s) is no longer compatible with the Tercen server. We cannot upload your data right now.\n"
									+ "Install the new version from FlowJo Exchange and re-start FlowJo to take advantage of new features.",
							version));
					return result;
				}

				// Autoupdate
				if (autoUpdate && Utils.isPluginOutdated(version, GIT_TOKEN_VALUE)) {
					String pluginDir = Updater.getPluginDirectory();
					if (Updater.isPluginDirectoryWritable(pluginDir)) {
						Updater.downloadLatestVersion(this, version, GIT_TOKEN_VALUE);
						Utils.showWarningDialog(
								"Your plugin has been updated, please remove all connectors and restart FlowJo now.");
						return result;
					} else {
						// notify user & log
						Utils.showErrorDialog(
								"Oops, looks like we don't have permission to update the Tercen plugin automatically. Please download the new version from the FlowJo Exchange.");
						logger.error("Plugindir is not writable: " + pluginDir);
					}
				}

				if (!sampleFile.exists()) {
					Utils.showErrorDialog("Input file does not exist");
					workspaceText = Tercen.FAILED;
				} else {
					Schema uploadResult = null;

					// Check if SAML authentication is enabled
					Version authMethods = client.userService.getServerVersion("auth.method");
					if (authMethods.features.contains("saml")) {
						session = Utils.getTercenSession();
						if (session == null) {
							session = Utils.getSamlSession(gui.getSAMLToken(client));
							if (session == null) {
								Utils.showErrorDialog("Token is invalid.");
								return result;
							}
						}
						userName = session.user.id;
					} else {
						userName = Utils.getCurrentPortalUser();
						if (userName == null || userName.equals("")) {
							logger.info("FlowJo email address is not set, asking user for it.");
							userName = gui.getEmailAddress();
							if (userName == null || userName.equals("") || !userName.contains("@")) {
								Utils.showErrorDialog("FlowJo email address needs to be set.");
								logger.error("FlowJo email address is not set, can't upload");
								return result;
							}
						}
					}

					List<User> users = Utils.getTercenUser(client, userName);
					if (users.size() == 0) {
						logger.debug(String.format("User %s not found in Tercen, create user\".", userName));
						// create popup to ask user for credentials
						Map<String, Object> userResult = gui.createUser(client, userName);
						if (session != null) {
							passWord = (String) userResult.get("pwd");
							token = (String) userResult.get("token");
							Utils.saveTercenSession(session);
						} else {
							pluginState = ImportPluginStateEnum.collectingSamples;
							return result;
						}
					} else {
						// get and check token for existing user
						session = Utils.getAndExtendTercenSession(client, gui, userName, passWord, session);
						if (session == null) {
							return result;
						}
						client.httpClient.setAuthorization(session.token.token);
					}

					// Get or create project if it doesn't exist
					projectName = Utils.getTercenProjectName(wsp);
					Project project = Utils.getProject(client, session.user.id, projectName);

					// upload csv file
					if (selectedSamplePops.size() > 0) {
						if (channels.size() > 0) {
							uploadProgressTask = new UploadProgressTask(this);
							uploadResult = Utils.uploadCsvFile(this, client, project, selectedSamplePops, channels,
									uploadProgressTask, Utils.getTercenDataTableName(wsp));

							// open browser
							if (uploadResult != null) {
								String url = Utils.getTercenCreateWorkflowURL(client, hostName, session.user.id,
										uploadResult, wsp);
								Desktop desktop = java.awt.Desktop.getDesktop();
								URI uri = new URI(String.valueOf(url));
								desktop.browse(uri);
								projectURL = Utils.getTercenProjectURL(hostName, session.user.id, uploadResult);
								workspaceText = String.format("Uploaded to %s.", hostName);
								pluginState = ImportPluginStateEnum.uploaded;
							} else {
								Utils.showWarningDialog("No files have been uploaded, browser window will not open.");
								return result;
							}
						} else {
							Utils.showWarningDialog(
									"Oops! there are no FCS channels selected. Make sure to pick at least one.");
							return result;
						}
					} else {
						Utils.showWarningDialog(
								"There is no data to upload, please make sure you have selected at minimum one sample.");
						return result;
					}
				}
			} else if (pluginState == ImportPluginStateEnum.importing)

			{
				if (importFile != null) {
					File fullImportFile = ImportHelper.getCompleteUploadFile(fcmlQueryElement, result, importFile,
							outputFolder.getAbsolutePath(), 0);
					if (fullImportFile != null) {
						List<File> importFiles = SplitData.splitCsvFileOnColumn(fullImportFile);
						File clusterFile = importFiles.get(0);
						File otherFile = importFiles.get(1);

						// clusters
						if (clusterFile != null) {
							List<ClusterFileMetaData> clusterMetadata = extractNameAndCountForParameter(clusterFile);
							for (ClusterFileMetaData metadata : clusterMetadata) {
								PluginHelper.createClusterParameter(result, metadata.colname, clusterFile);
							}
							addGatingML(result, clusterMetadata);
						}
						// other results (float values)
						if (otherFile != null) {
							PluginHelper.createClusterParameter(result, pluginName, otherFile);
						}
						workspaceText = String.format("Imported data from %s.", importFile);
					}
				} else {
					Utils.showWarningDialog("You did not select a file to import");
				}
				pluginState = ImportPluginStateEnum.uploaded;
			}

			switch (pluginState) {
			case empty:
				setWorkspaceText(nodeList, "Empty");
				break;
			case collectingSamples:
				setWorkspaceText(nodeList, "Selected");
				break;
			case uploading:
				setWorkspaceText(nodeList, "Uploading");
				break;
			case uploaded:
				nodeList = Utils.getAllSelectedTercenNodes(wsp);
				result = setWorkspaceUploadText(result, nodeList, workspaceText);
				break;
			default:
				break;
			}
		} catch (

		ServiceError e) {
			if (uploadProgressTask != null) {
				uploadProgressTask.setVisible(false);
			}
			String errorMsg = e.toString();
			if (errorMsg.contains("user.token.bad") || errorMsg.contains("user.token.expired")
					|| errorMsg.contains("token.not.valid")) {
				Utils.removeTercenSession();
			}
			logger.error(errorMsg);
			errorMsg = Utils.setUserFriendlyErrorMessage(errorMsg, this.hostName);
			setWorkspaceText(nodeList, errorMsg);
			pluginState = ImportPluginStateEnum.error;
		} catch (IOException e) {
			logger.error(e.getMessage());
			setWorkspaceText(nodeList, e.getMessage());
			pluginState = ImportPluginStateEnum.error;
		} catch (URISyntaxException e) {
			logger.error(e.getMessage());
			setWorkspaceText(nodeList, e.getMessage());
			pluginState = ImportPluginStateEnum.error;
		} catch (ClassNotFoundException e) {
			logger.error(e.getMessage());
			setWorkspaceText(nodeList, e.getMessage());
			pluginState = ImportPluginStateEnum.error;
		} catch (JSONException e) {
			logger.error(e.getMessage());
			setWorkspaceText(nodeList, e.getMessage());
			pluginState = ImportPluginStateEnum.error;
		} catch (DataFormatException e) {
			logger.error(e.getMessage());
			setWorkspaceText(nodeList, e.getMessage());
			pluginState = ImportPluginStateEnum.error;
			Utils.showErrorDialog(e.getMessage());
		} catch (RuntimeException e) {
			logger.error(e.getMessage());
			setWorkspaceText(nodeList, e.getMessage());
			pluginState = ImportPluginStateEnum.error;
		}
		return result;
	}

	private void readPropertiesFile() {
		Properties prop = new Properties();
		String propertyFilePath = "./" + TERCEN_PROPERTIES;
		logger.debug("propertyFilePath: " + propertyFilePath);
		try {
			prop.load(new BufferedReader(new InputStreamReader(new FileInputStream(propertyFilePath))));
			hostName = prop.getProperty(HOST);
			String maxDataPointsStr = prop.getProperty(MAX_UPLOAD_DATAPOINTS);
			if (Utils.isNumeric(maxDataPointsStr)) {
				maxDataPoints = Long.valueOf(maxDataPointsStr);
			}
			String seedStr = prop.getProperty(SEED);
			if (Utils.isNumeric(maxDataPointsStr)) {
				seed = Long.valueOf(seedStr);
			}
			autoUpdate = Boolean.valueOf(prop.getProperty(AUTO_UPDATE));
		} catch (IOException e) {
			logger.error(e.getMessage());
			if (e.getClass().getName().equalsIgnoreCase("java.io.FileNotFoundException")) {
				// generate file if it can't be found
				saveTercenProperties(propertyFilePath);
				hostName = HOSTNAME_URL;
				maxDataPoints = Long.valueOf(MAX_DATAPOINTS_VALUE);
				seed = Long.valueOf(SEED_VALUE);
				autoUpdate = Boolean.valueOf(AUTO_UPDATE_VALUE);
			}
		}
	}

	private void saveTercenProperties(String path) {
		OrderedProperties props = new OrderedProperties();
		props.setProperty(HOST, HOSTNAME_URL);
		props.setProperty(MAX_UPLOAD_DATAPOINTS, MAX_DATAPOINTS_VALUE);
		props.setProperty(SEED, SEED_VALUE);
		props.setProperty(AUTO_UPDATE, AUTO_UPDATE_VALUE);
		try {
			FileOutputStream outputStream = new FileOutputStream(path);
			props.store(outputStream, "Property file for Tercen. Put it in the FlowJo plugin directory.");
			outputStream.close();
			logger.debug(String.format("Tercen property file has been generated at: %s", path));
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}

	@Override
	public boolean promptForOptions(SElement arg0, List<String> arg1) {
		if (this.pluginState == ImportPluginStateEnum.collectingSamples
				|| this.pluginState == ImportPluginStateEnum.uploaded
				|| this.pluginState == ImportPluginStateEnum.error) {
			Sample sample = FJPluginHelper.getSample(arg0);
			Workspace wsp = sample.getWorkspace();
			List<AppNode> nodeList = Utils.getAllSelectedTercenNodes(wsp);
			this.samplePops.clear();
			String sampleFileName = Utils.getSampleFileName(sample);
			for (AppNode node : nodeList) {
				SElement nodeElement = node.getElement();
				String nodeCSVFilename = Utils.getCsvFileName(node);
				this.samplePops.add(nodeCSVFilename);
				// update fields if current sample has been uploaded from a different sample
				if (!nodeCSVFilename.contains(sampleFileName)) {
					SElement connEl = nodeElement.getChild(pluginName);
					ImportPluginStateEnum nodeState = ImportPluginStateEnum.valueOf(connEl.getString(PLUGIN_STATE));
					LinkedHashSet<String> nodeSamplePops = getSelectedSamplePops(connEl);
					List<String> filteredPops = nodeSamplePops.stream().filter(c -> c.contains(sampleFileName))
							.collect(Collectors.toList());
					if (filteredPops.size() >= 1 && (nodeState == ImportPluginStateEnum.uploaded
							|| nodeState == ImportPluginStateEnum.error)) {
						this.pluginState = nodeState;
						this.projectURL = connEl.getString(PROJECT_URL);
					}
				}
			}
		}
		return gui.promptForOptions(arg0, arg1);
	}

	@Override
	public ExportFileTypes useExportFileType() {
		return ExportFileTypes.CSV_CHANNEL;
	}

	@Override
	public Icon getIcon() {
		if (tercenIcon == null) {
			URL url = this.getClass().getClassLoader().getResource(ICON_NAME);
			if (url != null)
				tercenIcon = new ImageIcon(url);
		}
		return tercenIcon;
	}

	private static void initLogger() {
		String filename = "tercen-plugin.log";
		String pattern = "%d{yyyy-MM-dd HH:mm:ss} %-5p %c{1}:%L - %m%n";

		ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
		builder.setStatusLevel(Level.DEBUG);
		builder.setConfigurationName("DefaultFileLogger");
		RootLoggerComponentBuilder rootLogger = builder.newRootLogger(Level.DEBUG);

		// set the pattern layout and pattern
		LayoutComponentBuilder layoutBuilder = builder.newLayout("PatternLayout").addAttribute("pattern", pattern);

		// create a file appender
		AppenderComponentBuilder appenderBuilder = builder.newAppender("LogToFile", "File")
				.addAttribute("fileName", filename).add(layoutBuilder);
		builder.add(appenderBuilder);
		rootLogger.add(builder.newAppenderRef("LogToFile"));

		// create a console appender
		AppenderComponentBuilder consoleAppenderBuilder = builder.newAppender("Console", "CONSOLE")
				.addAttribute("target", ConsoleAppender.Target.SYSTEM_OUT);
		consoleAppenderBuilder.add(builder.newLayout("PatternLayout").addAttribute("pattern", pattern));
		builder.add(consoleAppenderBuilder);
		rootLogger.add(builder.newAppenderRef("Console"));

		builder.add(rootLogger);
		Configurator.reconfigure(builder.build());
		logger = LogManager.getLogger();
	}

	public static void addGatingML(ExternalAlgorithmResults results, List<ClusterFileMetaData> clusterMetadata) {
		SElement gatingml = new SElement("gating:Gating-ML");
		for (ClusterFileMetaData metadata : clusterMetadata) {
			for (int i = 0; i < metadata.nclusters; i++) {
				SElement gate = new SElement("gating:RectangleGate");
				gate.setString("gating:id", metadata.colname + "." + i);
				gatingml.addContent(gate);
				SElement dimElem = new SElement("gating:dimension");
				dimElem.setDouble("gating:min", ((double) i - 0.3));
				dimElem.setDouble("gating:max", ((double) i + 0.3));
				gate.addContent(dimElem);
				SElement fcsDimElem1 = new SElement("data-type:fcs-dimension");
				fcsDimElem1.setString("data-type:name", metadata.colname);
				dimElem.addContent(fcsDimElem1);
			}
		}
		results.setGatingML(gatingml.toString());
	}

	private List<ClusterFileMetaData> extractNameAndCountForParameter(File clusterFile) throws IOException {
		List<ClusterFileMetaData> result = new ArrayList<ClusterFileMetaData>();
		CSVReader reader = new CSVReader(new FileReader(clusterFile));
		List<String[]> entries = reader.readAll();
		String[] header = entries.get(0);

		for (int i = 0; i < header.length; i++) {
			List<Long> clusters = new ArrayList<Long>();
			for (int j = 1; j < entries.size(); j++) {
				long val = Math.round(Double.parseDouble(entries.get(j)[i]));
				if (!clusters.contains(val)) {
					clusters.add(val);
				}
			}
			ClusterFileMetaData metadata = new ClusterFileMetaData();
			metadata.nclusters = clusters.size();
			metadata.colname = header[i];
			result.add(metadata);
		}
		return result;
	}

	public String getHostName() {
		return hostName;
	}

	public String getUserName() {
		return userName;
	}

	public String getPassWord() {
		return passWord;
	}

	public UserSession getSession() {
		return session;
	}

	public String getProjectURL() {
		return projectURL;
	}

	public void setSession(UserSession session) {
		this.session = session;
	}

	public void setImportFile(File importFile) {
		this.importFile = importFile;
	}

	public void setChannels(ArrayList<String> channels) {
		this.channels = channels;
	}
}