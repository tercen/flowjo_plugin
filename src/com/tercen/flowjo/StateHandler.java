package com.tercen.flowjo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import com.tercen.flowjo.Tercen.ImportPluginStateEnum;
import com.treestar.flowjo.core.nodes.AppNode;

public class StateHandler {

	public static void writeStateFile(List<AppNode> nodeList, ImportPluginStateEnum pluginState, String csvFileName)
			throws IOException {
		String text = pluginState.toString();
		if (!text.equals("")) {
			if (nodeList.size() == 1) {
				StateHandler.writeToFile(csvFileName, text);
			} else {
				for (AppNode appNode : nodeList) {
					if (appNode.isExternalPopNode()) {
						String sampleName = Utils.getCsvFileName(appNode);
						StateHandler.writeToFile(sampleName, text);
					}
				}
			}
		}
	}

	public static void writeToFile(String sampleName, String text) throws IOException {
		if (sampleName != "") {
			File stateFile = new File(sampleName + "_state.txt");
			FileWriter stateFileWriter = new FileWriter(stateFile);
			stateFileWriter.write(text);
			stateFileWriter.close();
		}
	}

	public static ImportPluginStateEnum getState(ImportPluginStateEnum currentState, String csvFileName,
			File outputFolder) {
		ImportPluginStateEnum result = currentState;
		if (currentState != ImportPluginStateEnum.empty) {
			result = ImportPluginStateEnum.collectingSamples;
			File stateFile = new File(outputFolder, csvFileName + "_state.txt");
			BufferedReader br;
			try {
				br = new BufferedReader(new FileReader(stateFile));
				result = ImportPluginStateEnum.valueOf(br.readLine());
			} catch (FileNotFoundException e) {
				// state file not found
			} catch (IOException e) {
				// empty file
			}
		}
		return result;
	}
}
