package tercen_plugin;

import java.util.ArrayList;
import java.util.HashSet;

import com.tercen.client.impl.TercenClient;
import com.tercen.flowjo.UploadProgressTask;
import com.tercen.flowjo.Utils;
import com.tercen.model.impl.CSVFileMetadata;
import com.tercen.model.impl.FileDocument;
import com.tercen.model.impl.Project;
import com.tercen.service.ServiceError;

public class UploadFile {

	private static final String HOST = "https://tercen.com/";
	private static final String TEAM = "GerTeam";
	private static final String PROJECT = "flowjo";
	private static final String USER = "";
	private static final String PWD = "";
	private static final String FILE = "C:\\flowjo\\maho\\AMC20170906_ILC_Buffy_phenotype\\Import_To_Tercen\\merged.csv";

	public static void main(String[] args) throws ServiceError {

		TercenClient client = new TercenClient(HOST);
		FileDocument fileDoc = new FileDocument();
		try {
			Project project = Utils.getProject(client, TEAM, PROJECT, USER, PWD);

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
			System.out.println("Uploading file: " + FILE);
			Utils.uploadCsvFile(client, project, filenames, channels, new UploadProgressTask());

			// get task, schema..

		} catch (Exception e) {
			// any error -> remove fileDoc
			client.fileService.delete(fileDoc.id, fileDoc.rev);
		}
		System.out.println("finished");
	}
}
