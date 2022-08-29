package com.tercen.flowjo.gui;

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
		// TODO
	}

	@Test
	public void createLabelTextFieldComboComboTest() {
		// TODO
	}

	@Test
	public void createLabelLabelComboTest() {
		// TODO
	}

}
