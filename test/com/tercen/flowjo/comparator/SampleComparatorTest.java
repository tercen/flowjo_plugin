package com.tercen.flowjo.comparator;

import org.junit.Test;

import com.treestar.flowjo.core.Sample;

public class SampleComparatorTest {

	SampleComparator comparator = new SampleComparator();

	@Test(expected = NullPointerException.class)
	public void testNull() {
		Sample s1 = new Sample();
		Sample s2 = new Sample();
		comparator.compare(s1, s2);
	}

	@Test
	public void test() {
		// TODO how to set sample name?
		Sample s1 = new Sample();
		s1.setSampleID("test");
		Sample s2 = new Sample();
		s2.setSampleID("test");
		// comparator.compare(s1, s2);
	}

}
