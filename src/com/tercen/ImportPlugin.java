package com.tercen;

import java.awt.Component;
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
import com.treestar.lib.gui.HBox;
import com.treestar.lib.gui.panels.FJLabel;
import com.treestar.lib.gui.swing.FJCheckBox;
import com.treestar.lib.xml.SElement;

public class ImportPlugin implements PopulationPluginInterface {

	private static final String pluginName = "TercenImportPlugin";
	private String version = "1.0";
	private boolean upload;
	private boolean uploadFCS;
	private boolean uploadCSV;
	private boolean openBrowser;
	
	private static final String TEAM_NAME = "test-team";
	private static final String PROJECT_NAME = "myproject";
	private static final String HOSTNAME_URL = "http://10.0.2.2:5402/";
	private static final String DOMAIN = "tercen";
	private static final String USERNAME = "test";
	private static final String PASSWORD = "test";
	
	private static final int fixedToolTipWidth = 300;
	private static final String Failed = "Failed";
	private static final String confirmText = String.format("Are you sure to upload files to %s?", HOSTNAME_URL);
	private static final String sampleLabel = "Upload Sample FCS file";
    private static final String sampleTooltip = "Should the FCS file be uploaded?";
    private boolean fSample = true;
	private static final String csvLabel = "Upload CSV file";
    private static final String csvTooltip = "Should the CSV file be uploaded?";
    private boolean fCsv = true;
	private static final String browserLabel = "Open browser window to Tercen";
    private static final String browserTooltip = "Should a browser window be opened to see the uploaded files?";
    private boolean fBrowser = true;
	
	@Override
	public SElement getElement() {
		SElement result = new SElement(pluginName);
		result.setBool("upload", upload);
		result.setBool("uploadFCS", uploadFCS);
		result.setBool("uploadCSV", uploadCSV);
		result.setBool("openBrowser", openBrowser);
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
			if (upload) {
		        if (!sampleFile.exists()) {
		            JOptionPane.showMessageDialog(null, "Input file does not exist", "ImportPlugin error", JOptionPane.ERROR_MESSAGE);
		            result.setWorkspaceString(ImportPlugin.Failed);
		        } else {
		        	LinkedHashMap uploadResult = null;
		        	//upload csv file
		        	if (uploadCSV) {
		        		String fileName = sampleFile.getPath();
		        		uploadResult = Utils.uploadCsvFile(HOSTNAME_URL, TEAM_NAME, PROJECT_NAME, DOMAIN, USERNAME, PASSWORD, fileName);
		        	}
	
		    		//upload fcs-zip file
		        	if (uploadFCS) {
			        	Sample sample = FJPluginHelper.getSample(fcmlQueryElement);
			        	FJFileRef fileRef = sample.getFileRef();
			        	String fileName = fileRef.getLocalFilepath();
			        	uploadResult = Utils.uploadZipFile(HOSTNAME_URL, TEAM_NAME, PROJECT_NAME, DOMAIN, USERNAME, PASSWORD, fileName);
			        	result.setWorkspaceString(uploadResult.toString());
		        	}
		        	
		        	// open browser
		        	if (openBrowser) {
		        		if (uploadResult != null) {
				        	String projectId = (String) uploadResult.get("projectId");
				        	if (projectId != null && projectId != "") {
					        	String url = Utils.getTercenProjectURL(HOSTNAME_URL, TEAM_NAME, projectId);
					        	Desktop desktop = java.awt.Desktop.getDesktop();
					        	URI uri = new URI(String.valueOf(url));
					        	desktop.browse(uri);
				        	}
		        		} else {
		        			JOptionPane.showMessageDialog(null, "No files have been uploaded, browser window will not open.", "ImportPlugin warning", JOptionPane.WARNING_MESSAGE);
		        		}
		        	}
		        	
		        	// set status for user
		        	if (uploadCSV && uploadFCS) {
		        		result.setWorkspaceString("FCS and CSV file have been uploaded to Tercen.");
		        	} else if (uploadCSV && !uploadFCS) {
		        		result.setWorkspaceString("CSV file has been uploaded to Tercen.");
		        	} else if (!uploadCSV && uploadFCS) {
		        		result.setWorkspaceString("FCS file has been uploaded to Tercen.");
		        	}  else {
						result.setWorkspaceString("Files have not been uploaded to Tercen.");
					}
		        	// TODO:
		        	// Execute a workflow in Tercen given the uploaded data
		        }
			} else {
				result.setWorkspaceString("Files have not been uploaded to Tercen.");
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
	
	private FJCheckBox createCheckbox(String label, String tooltip, boolean selected) {
        FJCheckBox checkbox = new FJCheckBox(label);
        checkbox.setToolTipText("<html><p width=\"" + fixedToolTipWidth + "\">" + tooltip + "</p></html>");
        checkbox.setSelected(selected);
        return checkbox;
	}
	
	@Override
	public boolean promptForOptions(SElement arg0, List<String> arg1) {
		// show confirm dialog
		List<Object> componentList = new ArrayList<Object>();
        
		FJLabel fjLabel1 = new FJLabel(confirmText);
        componentList.add(fjLabel1);
        
        // checkboxes
        FJCheckBox sampleFileCheckbox = createCheckbox(sampleLabel, sampleTooltip, fSample);
        FJCheckBox csvFileCheckbox = createCheckbox(csvLabel, csvTooltip, fCsv);
        FJCheckBox browserFileCheckbox = createCheckbox(browserLabel, browserTooltip, fBrowser);
        componentList.add(new HBox(new Component[]{ sampleFileCheckbox }));
        componentList.add(new HBox(new Component[]{ csvFileCheckbox }));
        componentList.add(new HBox(new Component[]{ browserFileCheckbox }));
        // TODO add option to select team name, project name etc?
        
		int option = JOptionPane.showConfirmDialog((Component) null, componentList.toArray(), getName() + " " + getVersion(), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
		if (option == JOptionPane.OK_OPTION)
		{
			upload = true;
			uploadFCS =  sampleFileCheckbox.isSelected();
			uploadCSV =  csvFileCheckbox.isSelected();
			openBrowser =  browserFileCheckbox.isSelected();
		}
		else {
			upload = false;
		}
		return true;
	}
	
	@Override
	public void setElement(SElement arg0) {
		upload = arg0.getBool("upload");
		uploadFCS = arg0.getBool("uploadFCS");
		uploadCSV = arg0.getBool("uploadCSV");
		openBrowser = arg0.getBool("openBrowser");
	}
	
	@Override
	public ExportFileTypes useExportFileType() {
		return ExportFileTypes.CSV_CHANNEL;
	}
}