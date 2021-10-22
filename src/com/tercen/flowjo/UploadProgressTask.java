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
import java.util.ArrayList;
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

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
	private static final int CSV_TASK_COUNT = 4;
	private JProgressBar progressBar;

	public UploadProgressTask(int maxItems) {
		// add to maxItems for the 2nd phase: CSVTask
		progressBar = new JProgressBar(0, maxItems + CSV_TASK_COUNT);
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
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLayout(new BorderLayout());
		add(new TestPane(progressBar));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
		setSize(new Dimension(300, 100));
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
				progressBar.setValue(i++);
				byte[] uploadBytes = Arrays.copyOfRange(block, 0, bytesRead);
				fileDoc = client.fileService.append(fileDoc, uploadBytes);
			}
		} catch (Exception e) {
			// any error -> remove fileDoc
			client.fileService.delete(fileDoc.id, fileDoc.rev);
		} finally {
			inputStream.close();
		}

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
		task = (CSVTask) client.taskService.create(task);
		progressBar.setValue(i++);
		client.taskService.runTask(task.id);
		progressBar.setValue(i++);
		task = (CSVTask) client.taskService.waitDone(task.id);
		progressBar.setValue(i++);
		if (task.state instanceof FailedState) {
			throw new ServiceError(task.state.toString());
		}
		Schema schema = client.tableSchemaService.get(task.schemaId);
		progressBar.setValue(i);
		return schema;
	}
}
