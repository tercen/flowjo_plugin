package com.tercen.flowjo;

import org.junit.Assert;
import org.junit.Test;

public class UtilsTest {

	@Test
	public void getFileName() {
		Assert.assertEquals("LD1_NS+NS_A01_exp", Utils
				.getFilename("C:\\FlowJo\\Ger\\Documents\\06-Jul-2022\\Connector\\LD1_NS+NS_A01_exp..ExtNode.csv"));
	}
}
