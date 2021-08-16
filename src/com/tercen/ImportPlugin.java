package com.tercen;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import javax.swing.Icon;
import javax.swing.JOptionPane;

import com.tercen.service.ServiceError;
import com.treestar.flowjo.core.Sample;
import com.treestar.lib.FJPluginHelper;
import com.treestar.lib.core.ExportFileTypes;
import com.treestar.lib.core.ExternalAlgorithmResults;
import com.treestar.lib.core.PopulationPluginInterface;
import com.treestar.lib.file.FJFileRef;
import com.treestar.lib.xml.SElement;

public class ImportPlugin implements PopulationPluginInterface {

	private static final String pluginName = "TercenImportPlugin";
	private int myCount = 0;
	private String version = "1.0";
	private String url;
	
	private static final String TEAM_NAME = "test-team";
	private static final String PROJECT_NAME = "myproject";
	private static final String LOCALHOST_URL = "http://10.0.2.2:5402/";
	private static final String DOMAIN = "tercen";
	private static final String USERNAME = "test";
	private static final String PASSWORD = "test";
	
	private static final String Failed = "Failed";
	
	@Override
	public SElement getElement() {
		SElement result = new SElement(pluginName);
		result.setInt("myCount", myCount);
		result.setString("url", url);
		return result;
	}
	
	@Override
	public Icon getIcon() {
		return null;
	}
	
	@Override
	public String getName() {
		return pluginName;
	}
	
	@Override
	public List<String> getParameters() {
		return new ArrayList<String>();
	}
	
	@Override
	public String getVersion() {
		return version;
	}
	
	@Override
	public ExternalAlgorithmResults invokeAlgorithm(SElement fcmlQueryElement, File sampleFile, File outputFolder) {
		ExternalAlgorithmResults result = new ExternalAlgorithmResults();
		
		try {
	        if (!sampleFile.exists()) {
	            JOptionPane.showMessageDialog(null, "Input file does not exist", "ImportPlugin error", JOptionPane.ERROR_MESSAGE);
	            result.setWorkspaceString(ImportPlugin.Failed);
	        } else {
	        	//upload csv file
	        	String fileName = sampleFile.getPath();
	        	LinkedHashMap uploadResult = Utils.uploadCsvFile(LOCALHOST_URL, TEAM_NAME, PROJECT_NAME, DOMAIN, USERNAME, PASSWORD, fileName);

	    		//upload fcs-zip file
	        	Sample sample = FJPluginHelper.getSample(fcmlQueryElement);
	        	FJFileRef fileRef = sample.getFileRef();
	        	fileName = fileRef.getLocalFilepath();
	        	uploadResult = Utils.uploadZipFile(LOCALHOST_URL, TEAM_NAME, PROJECT_NAME, DOMAIN, USERNAME, PASSWORD, fileName);
	        	result.setWorkspaceString(uploadResult.toString());
	        	
	        	// open browser
	        	String projectId = (String) uploadResult.get("projectId");
	        	if (projectId != null && projectId != "") {
		        	String url = LOCALHOST_URL + TEAM_NAME + "/p/" + projectId;
		        	Desktop desktop = java.awt.Desktop.getDesktop();
		        	URI uri = new URI(String.valueOf(url));
		        	desktop.browse(uri);
	        	}
	        	// TODO:
	        	// Find a way to view the data
	        	// next step is to execute a workflow given the data
	        }
		} catch (ServiceError e) {
			e.printStackTrace();
			result.setWorkspaceString(e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			result.setWorkspaceString(e.getMessage());
		} catch (URISyntaxException e) {
			e.printStackTrace();
			result.setWorkspaceString(e.getMessage());
		}
		return result;
	}
	
	@Override
	public boolean promptForOptions(SElement arg0, List<String> arg1) {
		myCount++;
		return true;
	}
	
	@Override
	public void setElement(SElement arg0) {
		myCount = arg0.getInt("myCount");
		url = arg0.getString("url");
	}
	
	@Override
	public ExportFileTypes useExportFileType() {
		return ExportFileTypes.CSV_CHANNEL;
	}
}