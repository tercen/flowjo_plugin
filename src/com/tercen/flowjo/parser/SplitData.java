package com.tercen.flowjo.parser;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.opencsv.CSVWriter;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.HeaderColumnNameMappingStrategy;

public class SplitData {

	public static List<File> splitCsvFileOnColumn(File inputFileO) throws IOException {

		HeaderColumnNameMappingStrategy msIn = new HeaderColumnNameMappingStrategy();
		msIn.setType(SplitBean.class);

		List<SplitBean> list;

		// read the data from the input CSV file into our SplitBean list:
		try (Reader reader = Files.newBufferedReader(Paths.get(inputFileO.getAbsolutePath()))) {
			CsvToBean cb = new CsvToBeanBuilder(reader).withMappingStrategy(msIn).build();
			list = cb.parse();
			int i = 1;
		}

		// set up file writers:
		File file1 = File.createTempFile("cluster", ".csv", null);
		File file2 = File.createTempFile("other", ".csv", null);
		try (CSVWriter writer1 = new CSVWriter(new FileWriter(file1.getAbsolutePath()));
				CSVWriter writer2 = new CSVWriter(new FileWriter(file2.getAbsolutePath()));) {

			// first write the headers to each file (false = no quotes):
			writer1.writeNext(SplitBean.getHeadingsOne(), false);
			writer2.writeNext(SplitBean.getHeadingsTwo(list.get(0)), false);

			// then write each row of data (false = no quotes):
			for (SplitBean item : list) {
				writer1.writeNext(item.getDataOne(), false);
				writer2.writeNext(item.getDataTwo(), false);
			}
		}
		return new ArrayList<File>(Arrays.asList(file1, file2));
	}
}
