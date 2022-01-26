package com.tercen.flowjo;

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
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

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JOptionPane;

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
import com.treestar.lib.core.ExportFileTypes;
import com.treestar.lib.core.ExternalAlgorithmResults;
import com.treestar.lib.core.PopulationPluginInterface;
import com.treestar.lib.xml.SElement;

import nu.studer.java.util.OrderedProperties;

public class Tercen extends ParameterOptionHolder implements PopulationPluginInterface {

	private static Logger logger = null;
	protected static final String pluginName = "Connector";
	protected static final String version = Utils.getProjectVersion();
	protected static final String CSV_FILE_NAME = "csvFileName";

	protected enum ImportPluginStateEnum {
		empty, collectingSamples, uploading, uploaded, error;
	}

	private static final String TERCEN_PROPERTIES = "tercen.properties";
	private static final String HOST = "host";
	private static final String MAX_UPLOAD_DATAPOINTS = "max.upload.datapoints";
	private static final String SEED = "seed";

	// default settings
	protected static final String HOSTNAME_URL = "https://stage.tercen.com/";
	protected static final String MAX_DATAPOINTS_VALUE = "300000000";
	protected static final String SEED_VALUE = "42";
	protected static final String AUTO_UPDATE_VALUE = "false";
	protected static final String GIT_TOKEN_VALUE = "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";
	protected static final String DOMAIN = "tercen";
	protected static final String ICON_NAME = "logo.png";

	private static final String Failed = "Failed";
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
	protected long seed = -1;
	protected long maxDataPoints = -1;

	// properties to gather multiple samples
	protected ImportPluginStateEnum pluginState = ImportPluginStateEnum.empty;
	protected LinkedHashSet<String> samplePops = new LinkedHashSet<String>();
	protected LinkedHashSet<String> selectedSamplePops = new LinkedHashSet<String>();
	protected TercenGUI gui = new TercenGUI(this);

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
		result.setString("channels", String.join(",", channels));
		result.setString("pluginState", pluginState.toString());
		result.setString(CSV_FILE_NAME, csvFileName);
		result.setString("projectURL", projectURL);
		if (!this.selectedSamplePops.isEmpty()) {
			SElement pops = new SElement("SelectedPopulations");
			for (String pop : this.selectedSamplePops) {
				SElement popElem = new SElement("SelectedPopulation");
				popElem.setString("path", pop);
				pops.addContent(popElem);
			}
			result.addContent(pops);
		}
		return result;
	}

	@Override
	public void setElement(SElement element) {
		String channelString = element.getString("channels");
		if (channelString.equals("")) {
			channels = new ArrayList<String>();
		} else {
			channels = new ArrayList<String>(Arrays.asList(channelString.split(",")));
		}
		this.pluginState = ImportPluginStateEnum.valueOf(element.getString("pluginState"));
		this.csvFileName = element.getString(CSV_FILE_NAME);
		this.projectURL = element.getString("projectURL");
		SElement pops = element.getChild("SelectedPopulations");
		if (pops != null) {
			this.selectedSamplePops.clear();
			for (SElement popElem : pops.getChildren("SelectedPopulation")) {
				this.selectedSamplePops.add(popElem.getString("path"));
			}
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

				// Check and update plugin if needed
				Version pluginServerVersion = client.userService.getServerVersion("flowjoPlugin");
				if (!Utils.isPluginVersionSupported(version, pluginServerVersion)
						|| Utils.isPluginOutdated(version, GIT_TOKEN_VALUE)) {
					Updater.downloadLatestVersion(this, version, GIT_TOKEN_VALUE);
					JOptionPane.showMessageDialog(null, "Your plugin has been updated, please restart FlowJo now.",
							"Tercen Plugin V" + getVersion(), JOptionPane.WARNING_MESSAGE);
					return result;
				}

				if (!sampleFile.exists()) {
					JOptionPane.showMessageDialog(null, "Input file does not exist", "ImportPlugin error",
							JOptionPane.ERROR_MESSAGE);
					workspaceText = Tercen.Failed;
				} else {
					Schema uploadResult = null;

					userName = Utils.getCurrentPortalUser();
					if (userName == null || userName.equals("")) {
						JOptionPane.showMessageDialog(null, "FlowJo username needs to be set.", "ImportPlugin error",
								JOptionPane.ERROR_MESSAGE);
						workspaceText = "Selected";
						logger.error("FlowJo username is not set, can't upload");
					} else {
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
							session = Utils.getAndExtendTercenSession(client, gui, passWord);
							client.httpClient.setAuthorization(session.token.token);
						}

						// Get or create project if it doesn't exist
						projectName = Utils.getTercenProjectName(wsp);
						Project project = Utils.getProject(client, session.user.id, projectName);

						// upload csv file
						if (selectedSamplePops.size() > 0) {
							uploadProgressTask = new UploadProgressTask(this);
							uploadResult = Utils.uploadCsvFile(this, client, project, selectedSamplePops, channels,
									uploadProgressTask, Utils.getTercenDataTableName(wsp));
						}

						// open browser
						if (uploadResult != null) {
							String url = Utils.getTercenCreateWorkflowURL(client, hostName, session.user.id,
									uploadResult, wsp);
							Desktop desktop = java.awt.Desktop.getDesktop();
							URI uri = new URI(String.valueOf(url));
							desktop.browse(uri);
							projectURL = Utils.getTercenProjectURL(hostName, session.user.id, uploadResult);
							workspaceText = String.format("Uploaded to %s.", hostName);
						} else {
							JOptionPane.showMessageDialog(null,
									"No files have been uploaded, browser window will not open.",
									"ImportPlugin warning", JOptionPane.WARNING_MESSAGE);
							workspaceText = "Selected";
						}
					}
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

		} catch (ServiceError e) {
			if (uploadProgressTask != null) {
				uploadProgressTask.setVisible(false);
			}
			String errorMsg = e.toString();
			if (errorMsg.contains("user.token.bad") || errorMsg.contains("user.token.expired")
					|| errorMsg.contains("token.not.valid")) {
				Utils.removeTercenSession();
			}
			logger.error(errorMsg);
			setWorkspaceText(nodeList, e.toString());
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
		}
		return result;
	}

	private void readPropertiesFile() {
		Properties prop = new Properties();
		File jarfile = new File(Tercen.class.getProtectionDomain().getCodeSource().getLocation().getPath());
		String propertyFilePath = jarfile.getParent() + File.separator + TERCEN_PROPERTIES;
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
		} catch (IOException e) {
			logger.error(e.getMessage());
			if (e.getClass().getName().equalsIgnoreCase("java.io.FileNotFoundException")) {
				// generate file if it can't be found
				saveTercenProperties(propertyFilePath);
				hostName = HOSTNAME_URL;
				maxDataPoints = Long.valueOf(MAX_DATAPOINTS_VALUE);
				seed = Long.valueOf(SEED_VALUE);
			}
		}
	}

	private void saveTercenProperties(String path) {
		OrderedProperties props = new OrderedProperties();
		props.setProperty(HOST, HOSTNAME_URL);
		props.setProperty(MAX_UPLOAD_DATAPOINTS, MAX_DATAPOINTS_VALUE);
		props.setProperty(SEED, SEED_VALUE);
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
			samplePops.clear();
			for (AppNode node : nodeList) {
				samplePops.add(Utils.getCsvFileName(node));
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

	public String getHostName() {
		return hostName;
	}

	public String getUserName() {
		return userName;
	}

	public String getPassWord() {
		return passWord;
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

}
