package com.tercen.flowjo.gui;

import java.awt.Component;
import java.util.Arrays;
import java.util.List;

import javax.swing.JEditorPane;

import org.junit.Assert;
import org.junit.Test;

import com.tercen.flowjo.Tercen;
import com.tercen.flowjo.Utils;
import com.tercen.service.ServiceError;
import com.treestar.lib.gui.FJList;
import com.treestar.lib.gui.FontUtil;
import com.treestar.lib.gui.panels.FJLabel;
import com.treestar.lib.gui.text.FJTextField;

public class TercenGUITest {

	private Tercen plugin = new Tercen();
	private TercenGUI gui = new TercenGUI(plugin);

	@Test
	public void dialogTitleTest() {
		Assert.assertEquals("Tercen Plugin V" + Utils.getProjectVersion(), gui.getDialogTitle());
	}

	@Test
	public void createPaneWithLinkTest() {
		JEditorPane result = gui.createPaneWithLink(true, false);
		Assert.assertEquals(false, result.isEditable());
		Assert.assertEquals(1, result.getHyperlinkListeners().length);
	}

	@Test
	public void getFailedUserMessageTest() {
		String result = gui.getFailedUserMessage("", new ServiceError(""));
		Assert.assertEquals("", result);
	}

	@Test
	public void addHeaderStringTest() {
		FJLabel result = gui.addHeaderString("", FontUtil.dlogBold16);
		Assert.assertEquals("com.treestar.lib.gui.panels.FJLabel", result.getClass().getName());
	}

	@Test
	public void createParameterListTest() {
		List<String> params = Arrays.asList(new String[] {});
		FJList result = gui.createParameterList(params, null, false);
		Assert.assertEquals("com.treestar.lib.gui.FJList", result.getClass().getName());
	}

	@Test
	public void createLabelTextFieldComboTest() {
		int fixedLabelWidth = 130;
		int fixedFieldWidth = 250;
		int fixedLabelHeigth = 25;
		int fixedFieldHeigth = 25;

		Component[] result = gui.createLabelTextFieldCombo("labelText", "fieldValue", "fieldTooltip", false,
				FontUtil.dlog12, (int) (fixedLabelWidth * 0.5), fixedLabelHeigth, (int) (fixedFieldWidth * 1.5),
				fixedFieldHeigth);
		Assert.assertEquals("[Ljava.awt.Component;", result.getClass().getName());
		Assert.assertEquals("labelText", ((FJLabel) result[0]).getText());
		Assert.assertEquals("fieldValue", ((FJTextField) result[1]).getText());
		Assert.assertTrue(((FJTextField) result[1]).getToolTipText().contains("fieldTooltip"));
	}

	@Test
	public void createLabelLabelComboTest() {
		Component[] result = gui.createLabelLabelCombo("label1Text", "label2Text");
		Assert.assertEquals("[Ljava.awt.Component;", result.getClass().getName());
		Assert.assertEquals("label1Text", ((FJLabel) result[0]).getText());
		Assert.assertEquals("label2Text", ((FJLabel) result[1]).getText());
	}

}
