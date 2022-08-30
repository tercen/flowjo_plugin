package com.tercen.flowjo.parser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class SplitDataTest {

	private static final String FILE_NAME = "Export-LD1_PI+NS_B01_exp_metacluster_umap.csv";

	@Test
	public void splitDataTest() {
		String sep = System.getProperty("file.separator");
		File file = new File(new File("").getAbsolutePath() + sep + "test" + sep + "resources" + sep + FILE_NAME);
		List<File> result;
		try {
			result = SplitData.splitCsvFileOnColumn(file);
			Assert.assertEquals(2, result.size());

			File file1 = result.get(0);
			File file2 = result.get(1);
			String header1 = readFirstLine(file1);
			String header2 = readFirstLine(file2);

			Assert.assertTrue(file1.getName().contains("cluster"));
			Assert.assertTrue(file2.getName().contains("other"));
			Assert.assertEquals("FlowSOM.metacluster_number", header1);
			Assert.assertEquals("res..rowId,res.umap.1,res.umap.2", header2);

		} catch (IOException e) {
			Assert.fail(e.getMessage());
		}
	}

	private String readFirstLine(File file) throws IOException {
		BufferedReader buffer = new BufferedReader(new FileReader(file));
		return (buffer.readLine());
	}
}
