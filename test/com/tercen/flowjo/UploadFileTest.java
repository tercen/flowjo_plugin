package com.tercen.flowjo;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;

import org.junit.Assert;
import org.junit.Test;

import com.tercen.client.impl.TercenClient;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.Schema;
import com.tercen.model.impl.UserSession;

public class UploadFileTest {

	private static final String PROJECT = "flowjo";
	private static final String HOST_NAME = "http://localhost:5400/";
	private static final String FILE = "SI5_eukaryotic_lineages.csv";
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
			System.out.println("Uploading file: " + FILE);
			Schema uploadResult = Utils.uploadCsvFile(plugin, client, project, filenames, channels,
					new UploadProgressTask(plugin), dataTableName);

			// check result
			Assert.assertEquals(uploadResult.name, FILE);
			Assert.assertEquals(uploadResult.columns.size(), 7); // input + population, random_label

		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}
		System.out.println("finished");
	}
}
