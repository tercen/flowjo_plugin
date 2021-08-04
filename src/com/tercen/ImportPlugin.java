package com.tercen;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;

import com.tercen.service.ServiceError;
import com.treestar.lib.core.ExportFileTypes;
import com.treestar.lib.core.ExternalAlgorithmResults;
import com.treestar.lib.core.PopulationPluginInterface;
import com.treestar.lib.xml.SElement;

public class ImportPlugin implements PopulationPluginInterface {

	private static final String pluginName = "TercenImportPlugin";
	private int myCount = 0;
	private String version = "1.0";
	private String url;
	
	private static final String TEAM_NAME = "test-team";
	private static final String PROJECT_NAME = "myproject";
	private static final String LOCALHOST_URL = "http://10.0.2.2:5402/";
	
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
	public ExternalAlgorithmResults invokeAlgorithm(SElement arg0, File arg1, File arg2) {
		ExternalAlgorithmResults result = new ExternalAlgorithmResults();
		
		//upload file to tercen
		try {
			// arg1 should point to csvfile of FCS file
			String fileName = arg1.getPath();
			String uploadResult = Utils.uploadFile(LOCALHOST_URL, TEAM_NAME, PROJECT_NAME, fileName);
			result.setWorkspaceString(uploadResult);
		} catch (ServiceError e) {
			e.printStackTrace();
			result.setWorkspaceString(e.getMessage());
		} catch (IOException e) {
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