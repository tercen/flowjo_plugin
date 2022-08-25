package com.tercen.flowjo;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.junit.Assert;
import org.junit.Test;

import com.tercen.client.impl.TercenClient;
import com.tercen.flowjo.tasks.UploadProgressTask;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.Schema;
import com.tercen.model.impl.UserSession;

public class UploadFileTest {

	private static final String PROJECT = "flowjo";
	private static final String HOST_NAME = "http://0.0.0.0:5400/";
	private static final String FILE = "LD1_PI+NS_B01_exp..ExtNode.csv";
	private static final String USER_NAME = "admin";
	private static final String PASSWORD = "admin";

	@Test
	public void upload() {

		String fileLocation = new File("").getAbsolutePath() + "\\test\\resources\\" + FILE;
		System.out.println(fileLocation);
		System.out.println(new File(fileLocation).exists());

		Tercen plugin = new Tercen();
		TercenClient client = new TercenClient(HOST_NAME);
		try {
			UserSession session = client.userService.connect2("tercen", USER_NAME, PASSWORD);
			Project project = Utils.getProject(client, session.user.id, PROJECT);
			String dataTableName = FILE.substring(FILE.lastIndexOf("\\") + 1);

			LinkedHashSet<String> filenames = new LinkedHashSet<String>();
			filenames.add(fileLocation);
			ArrayList<String> channels = new ArrayList<String>();
			System.out.println("Uploading file: " + FILE + ", no channels");
			Schema uploadResult = Utils.uploadCsvFile(plugin, client, project, filenames, channels,
					new UploadProgressTask(plugin), dataTableName);

			// check result
			Assert.assertEquals(FILE, uploadResult.name);
			Assert.assertEquals(3, uploadResult.columns.size()); // filename, random_label, F_rowId

			// add channels
			channels.add("FSC-A");
			channels.add("FSC-H");
			System.out.println("Uploading file: " + FILE + ", 2 channels");
			uploadResult = Utils.uploadCsvFile(plugin, client, project, filenames, channels,
					new UploadProgressTask(plugin), dataTableName);

			// check result
			Assert.assertEquals(FILE, uploadResult.name);

		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}
		System.out.println("finished");
	}
}
