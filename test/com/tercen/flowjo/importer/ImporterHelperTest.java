package com.tercen.flowjo.importer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.tercen.service.ServiceError;

public class ImporterHelperTest {

	private static final String FILE_NAME = "Export-LD1_PI+PI_D01_exp_metacluster_umap.csv";
	private static final String sep = System.getProperty("file.separator");

	@Test
	public void importerTest() {
		try {
			String importFilePath = getTestResourcesDir() + sep + FILE_NAME;
			String outputFolder = getTestResourcesDir();
			File importFile = new File(importFilePath);

			ImportProperties result = ImportHelper.getImportFileProperties(importFile, 0);
			Assert.assertNotNull(result);

			Assert.assertEquals("0.0,0.0,0.0\n", result.noEventLine);
			Assert.assertEquals(0, result.rowIdColumnIndex);
			Assert.assertEquals("res..F_rowId,FlowSOM.metacluster_number,res.umap.1,res.umap.2", result.headerLine);
			Assert.assertEquals("Export-LD1_PI+PI_D01_exp_metacluster_umap", result.sampleName);

			int numEvents = 2000;
			File outputFile = ImportHelper.writeOutput(importFile, outputFolder, numEvents, result);
			Assert.assertNotNull(outputFile);
			Assert.assertEquals("Export-LD1_PI+PI_D01_exp_metacluster_umap.complete.csv", outputFile.getName());

			BufferedReader pluginCSVFileReader = new BufferedReader(new FileReader(outputFile));
			String line = pluginCSVFileReader.readLine();
			Assert.assertEquals("FlowSOM.metacluster_number,res.umap.1,res.umap.2", line);
			int counter = 0;
			while ((line = pluginCSVFileReader.readLine()) != null) {
				counter++;
				if (counter == 1) {
					Assert.assertEquals("0.0,0.0,0.0", line);
				} else if (counter == 46) {
					Assert.assertEquals("14,-7.2793496409301,-3.5571551059761", line);
				}
			}
			Assert.assertEquals(numEvents, counter);
			pluginCSVFileReader.close();

		} catch (IOException | ServiceError e) {
			Assert.fail(e.getMessage());
		}
	}

	private String getTestResourcesDir() {
		return (new File("").getAbsolutePath() + sep + "test" + sep + "resources");
	}
}
