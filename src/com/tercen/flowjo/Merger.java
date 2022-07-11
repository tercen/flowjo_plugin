package com.tercen.flowjo;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringUtils;

import com.tercen.service.ServiceError;
import com.treestar.lib.PluginHelper;
import com.treestar.lib.core.ExternalAlgorithmResults;
import com.treestar.lib.fjml.types.FileTypes;
import com.treestar.lib.parsing.interpreter.ParseUtil;
import com.treestar.lib.xml.SElement;

public class Merger {

	/**
	 * Generate an output file that has a row for all events, so that it can be
	 * uploaded in FlowJo. The exported file in Tercen might have limited results
	 * (e.g. downsampled), so empty rows need to be added.
	 * 
	 * @throws ServiceError
	 * 
	 */
	public static File getCompleteUploadFile(SElement fcmlElem, ExternalAlgorithmResults algorithmResults,
			File pluginCSVFile, String outputFolder, double noVal) throws IOException, ServiceError {
		int numEvents = PluginHelper.getNumTotalEvents(fcmlElem);

		BufferedReader pluginCSVFileReader = new BufferedReader(new FileReader(pluginCSVFile));
		String pluginCSVLine = pluginCSVFileReader.readLine();
		// determine which column is the rowId column
		int rowIdColumnIndex = -1;
		int colCt = 0;
		StringTokenizer tokenizer = new StringTokenizer(pluginCSVLine, ",");
		String noEventLine = ""; // the line to write when there is no output
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			if (StringUtils.containsIgnoreCase(token, "rowid")) {
				rowIdColumnIndex = colCt;
			} else {
				noEventLine += noVal + ",";
			}
			colCt++;
		}
		if (rowIdColumnIndex == -1) {
			pluginCSVFileReader.close();
			throw new ServiceError("Importer: there is no column containing the rowId");
		}

		// get rid of trailing comma of no parameter value line
		if (noEventLine.endsWith(",")) {
			noEventLine = noEventLine.substring(0, noEventLine.length() - 1);
		}
		noEventLine += "\n";

		String sampleName = pluginCSVFile.getName();
		if (sampleName.endsWith(FileTypes.FCS_SUFFIX)) {
			sampleName = sampleName.substring(0, sampleName.length() - FileTypes.FCS_SUFFIX.length());
		}
		if (sampleName.endsWith(FileTypes.CSV_SUFFIX) || sampleName.endsWith(FileTypes.TXT_SUFFIX)) {
			sampleName = sampleName.substring(0, sampleName.length() - FileTypes.CSV_SUFFIX.length());
		}
		String outFileName = sampleName + ".complete" + FileTypes.CSV_SUFFIX;
		File outFile = new File(outputFolder, outFileName);
		Writer output = new BufferedWriter(new FileWriter(outFile));
		String[] headerLineParts = pluginCSVLine.split(",");
		headerLineParts = Arrays.copyOfRange(headerLineParts, 1, headerLineParts.length);
		pluginCSVLine = String.join(",", headerLineParts);
		output.write(pluginCSVLine + "\n");

		int outputLineNum = 0;
		int eventNum = 0;
		int previousEventNum = 0;
		while ((pluginCSVLine = pluginCSVFileReader.readLine()) != null) {
			tokenizer = new StringTokenizer(pluginCSVLine, ",");
			colCt = 0;
			String line = "";
			while (tokenizer.hasMoreTokens()) {
				String token = tokenizer.nextToken();
				if (colCt == rowIdColumnIndex) // get the event number as integer
				{
					eventNum = (int) ParseUtil.getDouble(token);
					break;
				}
				colCt++;
			}

			// eventNum should be > 0 and increasing
			if (eventNum <= 0) {
				break;
			}
			if (eventNum < previousEventNum) {
				output.close();
				pluginCSVFileReader.close();
				throw new ServiceError("Importer: please make sure the data is ordered by rowId (increasing)");
			}

			while (outputLineNum < eventNum - 1 && outputLineNum < numEvents) {
				outputLineNum++;
				output.write(noEventLine);
			}
			line += pluginCSVLine.substring(pluginCSVLine.indexOf(",") + 1).trim();
			output.write(line);
			output.write("\n");
			outputLineNum++;
			previousEventNum = eventNum;
		}

		while (outputLineNum < numEvents) {
			outputLineNum++;
			output.write(noEventLine);
		}
		pluginCSVFileReader.close();
		output.close();
		return outFile;
	}
}
