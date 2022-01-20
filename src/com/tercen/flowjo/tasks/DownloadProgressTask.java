package com.tercen.flowjo.tasks;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.tercen.flowjo.Tercen;

public class DownloadProgressTask extends JFrame {

	private static final long serialVersionUID = 1L;
	private static final Logger logger = LogManager.getLogger();
	private JProgressBar progressBar;
	private Tercen plugin;

	public DownloadProgressTask(Tercen plugin) {
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
		setTitle("Updating Tercen plugin, progress..");
		setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		setLayout(new BorderLayout());
		add(new ProgressPane(progressBar));
		pack();
		setLocationRelativeTo(null);
		setVisible(true);
		setSize(new Dimension(500, 100));
		setIconImage(((ImageIcon) this.plugin.getIcon()).getImage());
	}

	public void setIterations(int maxItems) {
		progressBar.setMaximum(maxItems);
	}

	public void setValue(int value) {
		progressBar.setValue(value);
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
		}
	}
}
