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
import java.util.Arrays;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import com.tercen.client.impl.TercenClient;
import com.tercen.model.impl.FileDocument;
import com.tercen.service.ServiceError;

public class ProgressBar extends JFrame {

	private static final long serialVersionUID = 1L;
	private JProgressBar progressBar;

	public ProgressBar(int max) {
		progressBar = new JProgressBar(0, max);
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

	public FileDocument uploadFile(File file, TercenClient client, FileDocument fileDoc, int blocksize)
			throws ServiceError, IOException {
		InputStream inputStream = new FileInputStream(file);
		try {
			byte[] block = new byte[blocksize];
			int i = 1, bytesRead = 0;
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
		return fileDoc;
	}
}
