package com.tercen.flowjo;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.concurrent.CountDownLatch;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.tercen.client.impl.TercenClient;
import com.tercen.model.impl.CSVTask;
import com.tercen.model.impl.FailedState;
import com.tercen.model.impl.FileDocument;
import com.tercen.model.impl.InitState;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.Schema;
import com.tercen.service.ServiceError;

public class UploadProgressTask extends JFrame {

	private static final long serialVersionUID = 1L;
	private static final Logger logger = LogManager.getLogger(UploadProgressTask.class);
	private static final int CSV_TASK_COUNT = 3;
	private JProgressBar progressBar;

	public UploadProgressTask() {
		progressBar = new JProgressBar();
		progressBar.setSize(new Dimension(200, 80));
		progressBar.setValue(0);
		progressBar.setStringPainted(true);
		progressBar.addChangeListener(new ChangeListener() {
			public void stateChanged(ChangeEvent evt) {
				JProgressBar comp = (JProgressBar) evt.getSource();
				int value = comp.getValue();
				int max = comp.getMaximum();
				if (value == max) {
					setVisible(false);
				}
			}
		});
		setTitle("Upload Progress");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setLayout(new BorderLayout());
		add(new TestPane(progressBar));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
		setSize(new Dimension(300, 100));
	}

	public void setIterations(int maxItems) {
		// add to maxItems for the 2nd phase: CSVTask
		progressBar.setMaximum(maxItems + CSV_TASK_COUNT);
	}

	public class TestPane extends JPanel {

		public TestPane(JProgressBar progressBar) {
			setBorder(new EmptyBorder(10, 10, 10, 10));
			setLayout(new GridBagLayout());
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.insets = new Insets(4, 4, 4, 4);
			gbc.gridx = 0;
			gbc.gridy = 0;
			add(progressBar, gbc);
		}
	}

	public Schema uploadFile(File file, TercenClient client, Project project, FileDocument fileDoc,
			ArrayList<String> channels, int blocksize) throws ServiceError, IOException {
		InputStream inputStream = new FileInputStream(file);
		int i = 1;
		try {
			byte[] block = new byte[blocksize];
			int bytesRead = 0;
			while ((bytesRead = inputStream.read(block)) != -1) {
				progressBar.setValue(i);
				logger.debug(String.format("file upload progress: %d %%", 100 * i / progressBar.getMaximum()));
				byte[] uploadBytes = Arrays.copyOfRange(block, 0, bytesRead);
				fileDoc = client.fileService.append(fileDoc, uploadBytes);
				i++;
			}
		} catch (Exception e) {
			// any error -> remove fileDoc
			client.fileService.delete(fileDoc.id, fileDoc.rev);
		} finally {
			inputStream.close();
		}

		return handleCsvTask(client, project, fileDoc, channels, i);
	}

	private Schema handleCsvTask(TercenClient client, Project project, FileDocument fileDoc, ArrayList<String> channels,
			int i) throws ServiceError, IOException {
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
		logger.debug("create task");
		task = (CSVTask) client.taskService.create(task);
		progressBar.setValue(i++);

		URI baseUri = URI.create("api/v1/evt" + "/" + "listenTaskChannel");
		LinkedHashMap<String, Object> params = new LinkedHashMap<String, Object>();
		params.put("taskId", task.id);
		params.put("start", true);
		URI clientUrl = client.tercenURI.resolve(baseUri);
		String wsScheme = clientUrl.getScheme().equals("https") ? "wss" : "ws";
		String url = clientUrl.toString().replace(clientUrl.getScheme(), wsScheme) + "?params="
				+ Utils.urlEncodeUTF8(Utils.toJson(params));

		// Start task handler in websocket thread
		TercenWebSocketListener listener = new TercenWebSocketListener();
		int connectionRetries = 3;
		for (int j = 1; j <= connectionRetries; j++) {
			if (j == 1 || listener.hasError()) {
				if (listener.hasError()) {
					logger.error(listener.getThrowable().getMessage());
				}
				logger.debug("Connecting to (" + j + "): " + url);
				CountDownLatch latch = new CountDownLatch(1);
				listener.setCountDownLatch(latch);
				client.httpClient.createWebsocket(url, listener);
				try {
					latch.await();
				} catch (InterruptedException e) {
					logger.error(e.getMessage());
				}
			}
		}

		if (listener.hasError()) {
			throw new ServiceError(listener.getThrowable().getMessage());
		}

		logger.debug("get task");
		task = (CSVTask) client.taskService.get(task.id);
		progressBar.setValue(i++);

		if (task.state instanceof FailedState) {
			throw new ServiceError(((FailedState) task.state).reason);
		}
		logger.debug("task state: " + task.state.subKind);
		logger.debug("get schema");
		Schema schema = client.tableSchemaService.get(task.schemaId);
		progressBar.setValue(i);
		logger.debug("return schema");
		return schema;
	}
}
