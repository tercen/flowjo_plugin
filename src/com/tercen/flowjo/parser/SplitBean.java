package com.tercen.flowjo.parser;

import org.apache.commons.collections4.MultiValuedMap;

import com.opencsv.bean.CsvBindAndJoinByName;
import com.opencsv.bean.CsvBindByName;

public class SplitBean {

	@CsvBindByName(column = "FlowSOM.metacluster_id.int")
	private String cluster;

	@CsvBindAndJoinByName(column = ".*", elementType = Double.class)
	private MultiValuedMap<String, Double> theRest;

	public static String[] getHeadingsOne() {
		String[] s = { "FlowSOM.metacluster_id" };
		return s;
	}

	public static String[] getHeadingsTwo(SplitBean bean) {
		return bean.theRest.keySet().stream().map(str -> str.substring(0, str.lastIndexOf("."))).toArray(String[]::new);
	}

	public String[] getDataOne() {
		String[] i = { String.valueOf(cluster) };
		return i;
	}

	public String[] getDataTwo() {
		String[] columns = theRest.keySet().toArray(new String[theRest.size()]);
		String[] result = new String[columns.length];
		for (int i = 0; i < columns.length; i++) {
			String column = columns[i];
			result[i] = String.valueOf(theRest.get(column).iterator().next());

		}
		return result;
	}

}
