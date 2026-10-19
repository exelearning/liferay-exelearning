/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.portlet;

import com.liferay.portal.json.JSONObjectImpl;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.Portlet;
import com.liferay.portal.kernel.portlet.LiferayPortletConfig;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.WebKeys;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import javax.portlet.ActionParameters;
import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletContext;
import javax.portlet.PortletMode;
import javax.portlet.PortletPreferences;
import javax.portlet.PortletRequestDispatcher;
import javax.portlet.RenderParameters;
import javax.portlet.RenderRequest;
import javax.portlet.WindowState;

import org.exelearning.liferay.configuration.ExeLearningConfiguration;
import org.exelearning.liferay.constants.ExeLearningPortletKeys;
import org.exelearning.liferay.resource.ExeResource;
import org.exelearning.liferay.resource.ExeResourceService;
import org.exelearning.liferay.test.LiferayMocks;
import org.exelearning.liferay.test.TestJSON;
import org.exelearning.liferay.test.TestZips;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Exercises the portlets through the real MVC portlet lifecycle with a
 * mocked portlet container.
 *
 * @author eXeLearning contributors
 */
public class PortletsTest {

	@Before
	public void setUp() throws Exception {
		Mockito.when(
			_themeDisplay.getLocale()
		).thenReturn(
			new Locale("es", "ES")
		);

		Mockito.when(
			_themeDisplay.getScopeGroupId()
		).thenReturn(
			LiferayMocks.GROUP_ID
		);

		Mockito.when(
			_portal.getPathModule()
		).thenReturn(
			"/o"
		);

		Mockito.when(
			_renderRequest.getAttribute(WebKeys.THEME_DISPLAY)
		).thenReturn(
			_themeDisplay
		);

		Mockito.doAnswer(
			invocation -> _attributes.put(
				invocation.getArgument(0), invocation.getArgument(1))
		).when(
			_renderRequest
		).setAttribute(
			ArgumentMatchers.anyString(), ArgumentMatchers.any()
		);

		RenderParameters renderParameters = Mockito.mock(
			RenderParameters.class);

		Mockito.when(
			renderParameters.getValue("keywords")
		).thenReturn(
			"demo"
		);

		Mockito.when(
			_renderRequest.getRenderParameters()
		).thenReturn(
			renderParameters
		);

		Mockito.when(
			_renderRequest.getPreferences()
		).thenReturn(
			Mockito.mock(PortletPreferences.class)
		);

		Mockito.when(
			_renderRequest.getPortletMode()
		).thenReturn(
			PortletMode.VIEW
		);

		Mockito.when(
			_renderRequest.getWindowState()
		).thenReturn(
			WindowState.NORMAL
		);

		Mockito.when(
			_portletConfig.getPortletContext()
		).thenReturn(
			_portletContext
		);

		Mockito.when(
			_portletContext.getRequestDispatcher(ArgumentMatchers.anyString())
		).thenReturn(
			_portletRequestDispatcher
		);

		Mockito.when(
			_portletConfig.getPortletId()
		).thenReturn(
			ExeLearningPortletKeys.VIEWER
		);

		Mockito.when(
			_portletConfig.getPortletName()
		).thenReturn(
			ExeLearningPortletKeys.VIEWER
		);

		Mockito.when(
			_portletConfig.getInitParameter("view-template")
		).thenReturn(
			"/view.jsp"
		);

		Mockito.when(
			_portletConfig.getResourceBundle(ArgumentMatchers.any())
		).thenReturn(
			ResourceBundle.getBundle("content.Language", Locale.ENGLISH)
		);
	}

	@Test
	public void testAdminPortletRendersTheManagerConfiguration()
		throws Exception {

		ExeLearningAdminPortlet exeLearningAdminPortlet =
			new ExeLearningAdminPortlet();

		Language language = Mockito.mock(Language.class);

		Mockito.when(
			language.get(
				ArgumentMatchers.any(ResourceBundle.class),
				ArgumentMatchers.anyString())
		).thenAnswer(
			invocation -> "<" + invocation.getArgument(1) + ">"
		);

		TestZips.setField(
			exeLearningAdminPortlet, "_exeResourceService",
			_exeResourceService(true));
		TestZips.setField(
			exeLearningAdminPortlet, "_jsonFactory", TestJSON.factory());
		TestZips.setField(exeLearningAdminPortlet, "_language", language);
		TestZips.setField(exeLearningAdminPortlet, "_portal", _portal);

		exeLearningAdminPortlet.prepare(_renderRequest);

		Assert.assertEquals(
			"/o/exelearning/v1",
			_attributes.get(ExeLearningAdminPortlet.API_BASE));

		ExeLearningAdminDisplayContext exeLearningAdminDisplayContext =
			(ExeLearningAdminDisplayContext)_attributes.get(
				ExeLearningAdminPortlet.DISPLAY_CONTEXT);

		Assert.assertEquals(
			"demo", exeLearningAdminDisplayContext.getKeywords());
		Assert.assertTrue(exeLearningAdminDisplayContext.isEditorEnabled());

		Assert.assertEquals(
			"/o/exelearning-editor/index.html?locale=es",
			_attributes.get(ExeLearningAdminPortlet.EDITOR_URL));

		String json = (String)_attributes.get(ExeLearningAdminPortlet.I18N);

		Assert.assertFalse(json.contains("<"));

		JSONObject jsonObject = new JSONObjectImpl(json);

		Assert.assertEquals("<save>", jsonObject.getString("save"));
		Assert.assertEquals(FrontendStrings.keys.size(), jsonObject.length());

		_attributes.clear();

		TestZips.setField(
			exeLearningAdminPortlet, "_exeResourceService",
			_exeResourceService(false));

		exeLearningAdminPortlet.prepare(_renderRequest);

		Assert.assertEquals(
			"", _attributes.get(ExeLearningAdminPortlet.EDITOR_URL));
	}

	@Test
	public void testConfigurationActionOnlyAcceptsResourcesOfTheSite()
		throws Exception {

		ExeResourceService exeResourceService = Mockito.mock(
			ExeResourceService.class);

		ViewerConfigurationAction viewerConfigurationAction =
			new ViewerConfigurationAction();

		TestZips.setField(
			viewerConfigurationAction, "_exeResourceService",
			exeResourceService);

		FileEntry fileEntry = LiferayMocks.fileEntry(
			5L, LiferayMocks.expandoBridge(new HashMap<>()),
			LiferayMocks.fileVersion(
				"1.0",
				TestZips.write(
					temporaryFolder.newFile(
					).toPath(),
					TestZips.package_())));

		Mockito.when(
			exeResourceService.getResource(5L)
		).thenReturn(
			new ExeResource(
				fileEntry, Collections.emptyMap(), true, true, true,
				Collections.emptyList())
		);

		ActionRequest actionRequest = Mockito.mock(ActionRequest.class);

		ActionParameters actionParameters = Mockito.mock(
			ActionParameters.class);

		Mockito.when(
			actionParameters.getValue("preferences--fileEntryId--")
		).thenReturn(
			"5"
		);

		Mockito.when(
			actionRequest.getActionParameters()
		).thenReturn(
			actionParameters
		);

		Mockito.when(
			actionRequest.getAttribute(WebKeys.THEME_DISPLAY)
		).thenReturn(
			_themeDisplay
		);

		Mockito.when(
			_themeDisplay.getScopeGroupId()
		).thenReturn(
			999L
		);

		try {
			viewerConfigurationAction.processAction(
				_portletConfig, actionRequest,
				Mockito.mock(ActionResponse.class));

			Assert.fail();
		}
		catch (PrincipalException principalException) {
			Assert.assertTrue(
				principalException.getMessage(
				).contains(
					"another site"
				));
		}
	}

	@Test
	public void testPanelApp() throws Exception {
		ExeLearningPanelApp exeLearningPanelApp = new ExeLearningPanelApp();

		Portlet portlet = Mockito.mock(Portlet.class);

		TestZips.setField(exeLearningPanelApp, "_portlet", portlet);

		Assert.assertSame(portlet, exeLearningPanelApp.getPortlet());

		Assert.assertEquals(
			ExeLearningPortletKeys.ADMIN, exeLearningPanelApp.getPortletId());
	}

	@Test
	public void testViewerPortletPreparesItsDisplayContext() throws Exception {
		ExeLearningViewerPortlet exeLearningViewerPortlet =
			new ExeLearningViewerPortlet();

		TestZips.setField(
			exeLearningViewerPortlet, "_exeResourceService",
			Mockito.mock(ExeResourceService.class));

		exeLearningViewerPortlet.prepare(_renderRequest);

		ViewerDisplayContext viewerDisplayContext =
			(ViewerDisplayContext)_attributes.get(
				ViewerDisplayContext.class.getName());

		Assert.assertFalse(viewerDisplayContext.isConfigured());
	}

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private ExeResourceService _exeResourceService(boolean editorEnabled) {
		ExeResourceService exeResourceService = Mockito.mock(
			ExeResourceService.class);

		Map<String, Object> overrides = new HashMap<>();

		overrides.put("editorEnabled", editorEnabled);

		ExeLearningConfiguration exeLearningConfiguration =
			TestZips.configuration(overrides);

		Mockito.when(
			exeResourceService.getConfiguration()
		).thenReturn(
			exeLearningConfiguration
		);

		return exeResourceService;
	}

	private final Map<String, Object> _attributes = new HashMap<>();
	private final Portal _portal = Mockito.mock(Portal.class);
	private final LiferayPortletConfig _portletConfig = Mockito.mock(
		LiferayPortletConfig.class);
	private final PortletContext _portletContext = Mockito.mock(
		PortletContext.class);
	private final PortletRequestDispatcher _portletRequestDispatcher =
		Mockito.mock(PortletRequestDispatcher.class);
	private final RenderRequest _renderRequest = Mockito.mock(
		RenderRequest.class);
	private final ThemeDisplay _themeDisplay = Mockito.mock(ThemeDisplay.class);

}