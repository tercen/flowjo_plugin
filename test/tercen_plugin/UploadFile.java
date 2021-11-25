package tercen_plugin;

import java.util.ArrayList;
import java.util.HashSet;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.tercen.client.impl.TercenClient;
import com.tercen.flowjo.Tercen;
import com.tercen.flowjo.TercenGUI;
import com.tercen.flowjo.UploadProgressTask;
import com.tercen.flowjo.Utils;
import com.tercen.model.impl.CSVFileMetadata;
import com.tercen.model.impl.FileDocument;
import com.tercen.model.impl.Project;
import com.tercen.model.impl.UserSession;
import com.tercen.service.ServiceError;

public class UploadFile {

	private static final String PROJECT = "flowjo";
	private static final String FILE = "C:\\flowjo\\maho\\AMC20170906_ILC_Buffy_phenotype\\Import_To_Tercen\\merged.csv";
	private static final String USER_NAME = "ger.inberg@tercen.com";
	private static final String PASSWORD = "test";

	private static final Logger logger = LogManager.getLogger(UploadFile.class);

	public static void main(String[] args) throws ServiceError {

		Tercen plugin = new Tercen();
		TercenClient client = new TercenClient(plugin.getHostName());
		TercenGUI gui = new TercenGUI(plugin);
		FileDocument fileDoc = new FileDocument();
		try {
			UserSession session = client.userService.connect2("tercen", USER_NAME, PASSWORD);
			Project project = Utils.getProject(client, session.user.id, PROJECT);

			String name = FILE.substring(FILE.lastIndexOf("\\") + 1);
			fileDoc.name = name;
			fileDoc.projectId = project.id;
			fileDoc.acl.owner = project.acl.owner;
			CSVFileMetadata metadata = new CSVFileMetadata();
			metadata.contentType = "text/csv";
			metadata.separator = ",";
			metadata.quote = "\"";
			metadata.contentEncoding = "iso-8859-1";
			fileDoc.metadata = metadata;

			HashSet<String> filenames = new HashSet<String>();
			filenames.add(FILE);
			ArrayList<String> channels = new ArrayList<String>();
			logger.debug("Uploading file: " + FILE);
			Utils.uploadCsvFile(plugin, client, project, filenames, channels, new UploadProgressTask(plugin));
			logger.debug(String.format("Create upload file from %d sample files", 3));

			// get task, schema..

		} catch (Exception e) {
			logger.error(e.getMessage());
			e.printStackTrace();
		}
		logger.debug("finished");
	}
}
