package com.tercen.flowjo.tasks;

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
import java.util.List;
import java.util.concurrent.CountDownLatch;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tercen.client.impl.TercenClient;
import com.tercen.flowjo.Tercen;
import com.tercen.flowjo.TercenWebSocketListener;
import com.tercen.flowjo.Utils;
import com.tercen.model.impl.CSVTask;
import com.tercen.model.impl.ColumnSchema;
import com.tercen.model.impl.ColumnSchemaMetaData;
import com.tercen.model.impl.FailedState;
import com.tercen.model.impl.FileDocument;
import com.tercen.model.impl.InitState;
import com.tercen.model.impl.Pair;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.Schema;
import com.tercen.model.impl.TableSchema;
import com.tercen.service.ServiceError;

public class UploadProgressTask extends JFrame {

	private static final long serialVersionUID = 1L;
	private static final Logger logger = LogManager.getLogger();
	private static final int CSV_TASK_COUNT = 3;
	private JProgressBar progressBar;
	private String downSampleMessage;
	private Tercen plugin;

	public UploadProgressTask(Tercen plugin) {
		this.plugin = plugin;
		progressBar = new JProgressBar();
		progressBar.setSize(new Dimension(600, 80));
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
	}

	public void showDialog() {
		setTitle("Upload Progress");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setLayout(new BorderLayout());
		add(new ProgressPane(progressBar));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
		if (downSampleMessage != null && !downSampleMessage.equals("")) {
			setSize(new Dimension(700, 150));
		} else {
			setSize(new Dimension(300, 100));
		}
		setIconImage(((ImageIcon) this.plugin.getIcon()).getImage());
	}

	public void setIterations(int maxItems) {
		if (maxItems == 0) {
			maxItems = 1;
		}
		progressBar.setMaximum(maxItems);
	}

	public void setMessage(String message) {
		downSampleMessage = message;
	}

	public class ProgressPane extends JPanel {

		public ProgressPane(JProgressBar progressBar) {
			setBorder(new EmptyBorder(10, 10, 10, 10));
			setLayout(new GridBagLayout());
			GridBagConstraints gbc = new GridBagConstraints();
			gbc.fill = GridBagConstraints.HORIZONTAL;
			gbc.weightx = 0.5;
			gbc.insets = new Insets(4, 4, 4, 4);
			gbc.gridx = 0;
			gbc.gridy = 0;
			add(progressBar, gbc);
			gbc.gridy = 1;
			gbc.insets = new Insets(14, 4, 4, 4);
			if (downSampleMessage != null && !downSampleMessage.equals("")) {
				add(new JLabel("<html><i>" + downSampleMessage + "</i></html>"), gbc);
			}
		}
	}

	public FileDocument uploadFile(File file, TercenClient client, FileDocument fileDoc, int blocksize) throws ServiceError, IOException {
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
			throw new ServiceError(e.getMessage());
		} finally {
			inputStream.close();
		}

		return fileDoc;
	}
}
