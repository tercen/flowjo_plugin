package com.tercen.flowjo;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import javax.swing.JOptionPane;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tercen.client.impl.TercenClient;
import com.tercen.flowjo.tasks.UploadProgressTask;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.Schema;
import com.tercen.model.impl.UserSession;
import com.tercen.service.ServiceError;
import com.treestar.flowjo.application.workspace.Workspace;
import com.treestar.flowjo.core.nodes.AppNode;

public class Uploader implements Runnable {

	private static final Logger logger = LogManager.getLogger();
	private Tercen plugin;
	private TercenClient client;
	private Project project;
	private LinkedHashSet<String> fileNames;
	private ArrayList<String> channels;
	private UploadProgressTask uploadProgressTask;
	private Workspace wsp;
	private String hostName;
	private UserSession session;
	private String projectURL;
	private String statusText;

	public Uploader(Tercen plugin, TercenClient client, Project project, LinkedHashSet<String> fileNames,
			ArrayList<String> channels, Workspace wsp, String hostName, UserSession session) {
		this.plugin = plugin;
		this.client = client;
		this.project = project;
		this.fileNames = fileNames;
		this.channels = channels;
		this.wsp = wsp;
		this.hostName = hostName;
		this.session = session;
	}

	@Override
	public void run() {
		uploadProgressTask = new UploadProgressTask(plugin);
		try {
			Schema uploadResult = Utils.uploadCsvFile(plugin, client, project, fileNames, channels, uploadProgressTask,
					Utils.getTercenDataTableName(wsp));

			// open browser
			if (uploadResult != null) {
				String url = Utils.getTercenCreateWorkflowURL(client, hostName, session.user.id, uploadResult, wsp);
				Desktop desktop = java.awt.Desktop.getDesktop();
				URI uri = new URI(String.valueOf(url));
				desktop.browse(uri);
				projectURL = Utils.getTercenProjectURL(hostName, session.user.id, uploadResult);
				statusText = String.format("Uploaded to %s.", hostName);
			} else {
				JOptionPane.showMessageDialog(null, "No files have been uploaded, browser window will not open.",
						"ImportPlugin warning", JOptionPane.WARNING_MESSAGE);
			}
		} catch (ServiceError e) {
			logger.error(e.getMessage());
		} catch (IOException e) {
			logger.error(e.getMessage());
		} catch (URISyntaxException e) {
			logger.error(e.getMessage());
		}

		// write status files
		List<AppNode> nodes = Utils.getAllUploadingTercenNodes(wsp);
		for (AppNode appNode : nodes) {
			if (appNode.isExternalPopNode()) {
				Utils.writeStatusFile(Utils.getCsvFileName(appNode), statusText);
				appNode.setDirty();
				appNode.update(false);
			}
		}

	}

}
