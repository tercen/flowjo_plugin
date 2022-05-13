package com.tercen.flowjo.parser;

import org.apache.commons.collections4.MultiValuedMap;

import com.opencsv.bean.CsvBindAndJoinByName;

public class SplitBean {

	private static final String INT_COLUMN = ".int";

	@CsvBindAndJoinByName(column = ".*", elementType = Double.class)
	private MultiValuedMap<String, Double> allColumns;

	public static String[] getHeadingsOne(SplitBean bean) {
		String[] result = null;
		if (bean != null && bean.allColumns != null) {
			String[] clusterCols = bean.allColumns.keySet().stream().filter(str -> str.contains(INT_COLUMN))
					.map(str -> str.substring(0, str.lastIndexOf("."))).toArray(String[]::new);
			if (clusterCols.length > 0) {
				result = clusterCols;
			}
		}
		return result;
	}

	public static String[] getHeadingsTwo(SplitBean bean) {
		String[] result = null;
		if (bean != null && bean.allColumns != null) {
			String[] nonClusterCols = bean.allColumns.keySet().stream().filter(str -> !str.contains(INT_COLUMN))
					.map(str -> str.substring(0, str.lastIndexOf("."))).toArray(String[]::new);
			if (nonClusterCols.length > 0) {
				result = nonClusterCols;
			}
		}
		return (result);
	}

	public String[] getDataOne() {
		String[] columns = allColumns.keySet().stream().filter(str -> str.contains(INT_COLUMN)).toArray(String[]::new);
		String[] result = new String[columns.length];
		for (int i = 0; i < columns.length; i++) {
			String column = columns[i];
			result[i] = String.valueOf(allColumns.get(column).iterator().next());

		}
		return result;
	}

	public String[] getDataTwo() {
		String[] columns = allColumns.keySet().stream().filter(str -> !str.contains(INT_COLUMN)).toArray(String[]::new);
		String[] result = new String[columns.length];
		for (int i = 0; i < columns.length; i++) {
			String column = columns[i];
			result[i] = String.valueOf(allColumns.get(column).iterator().next());

		}
		return result;
	}
}
