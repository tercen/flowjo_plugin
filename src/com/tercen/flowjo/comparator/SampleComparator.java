package com.tercen.flowjo.comparator;

import java.util.Comparator;

import com.treestar.flowjo.core.Sample;

public class SampleComparator implements Comparator<Sample> {

	@Override
	public int compare(Sample s1, Sample s2) {
		int result;
		if (s1 == null || s2 == null) {
			result = 0;
		} else if (s1.getName() == null || s2.getName() == null) {
			result = 0;
		} else {
			result = s1.getName().compareTo(s2.getName());
		}
		return result;
	}

}
