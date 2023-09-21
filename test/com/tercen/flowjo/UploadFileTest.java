package com.tercen.flowjo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.tercen.client.impl.TercenClient;
import com.tercen.flowjo.tasks.UploadProgressTask;
import com.tercen.model.impl.FileDocument;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.UserSession;

public class UploadFileTest {

	private static final String PROJECT = "flowjo";
	private static final String FILE = "001_A488_channel_names.fcs";
	private static final String HOST_NAME = "http://10.0.2.2:5402/";
	private static final String USER_NAME = "admin";
	private static final String PASSWORD = "admin";

	@Test
	public void uploadWithoutChannels() {
		String sep = System.getProperty("file.separator");
		String fileLocation = new File("").getAbsolutePath() + sep + "test" + sep + "resources" + sep + FILE;
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
			FileDocument uploadResult = Utils.uploadZipFile(plugin, client, project, filenames, channels,
					new UploadProgressTask(plugin), dataTableName);

			// check result
			List<String> colnames = Arrays.asList(new String[] { "filename", "random_label", "F_rowId" });
			checkUploadResult(uploadResult, 100, colnames, "Relation");

		} catch (Exception e) {
			e.printStackTrace();
			Assert.fail(e.getMessage());
		}
		System.out.println("finished");
	}

	@Test
	public void uploadWithChannels() {
		String sep = System.getProperty("file.separator");
		String fileLocation = new File("").getAbsolutePath() + sep + "test" + sep + "resources" + sep + FILE;
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
			channels.add("FSC-A");
			channels.add("FSC-H");
			System.out.println("Uploading file: " + FILE + ", 2 channels");
			FileDocument uploadResult = Utils.uploadZipFile(plugin, client, project, filenames, channels,
					new UploadProgressTask(plugin), dataTableName);

			// check result (since data is gathered, hard to check some properties)
			List<String> colnames = Arrays.asList(new String[] {});
			checkUploadResult(uploadResult, 0, colnames, "CompositeRelation");

		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}
		System.out.println("finished");
	}
	
	private void checkUploadResult(FileDocument uploadResult, int nrows, List<String> colnames, String relation) {
		Assert.assertEquals(FILE, uploadResult.name);
		Assert.assertEquals(USER_NAME, uploadResult.createdBy);
		Assert.assertEquals(false, uploadResult.isDeleted);
		Assert.assertEquals("FileDocument", uploadResult.subKind);
	}

}
