/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.portlet.action;

import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.servlet.SessionMessages;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.upload.UploadPortletRequest;
import com.liferay.portal.kernel.util.Constants;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.WebKeys;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import java.util.HashMap;
import java.util.Map;

import javax.portlet.ActionParameters;
import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.MutableRenderParameters;

import org.exelearning.liferay.elpx.ElpxException;
import org.exelearning.liferay.resource.ExeResource;
import org.exelearning.liferay.resource.ExeResourceService;
import org.exelearning.liferay.test.TestZips;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * @author eXeLearning contributors
 */
public class EditResourceMVCActionCommandTest {

	@After
	public void tearDown() {
		_sessionErrors.close();
		_sessionMessages.close();
	}

	@Before
	public void setUp() throws Exception {
		_sessionErrors = Mockito.mockStatic(SessionErrors.class);
		_sessionMessages = Mockito.mockStatic(SessionMessages.class);

		TestZips.setField(_command, "_exeResourceService", _exeResourceService);
		TestZips.setField(_command, "_portal", _portal);

		new PortalUtil(
		).setPortal(
			_portal
		);

		Mockito.when(
			_portal.escapeRedirect(ArgumentMatchers.anyString())
		).thenAnswer(
			invocation -> invocation.getArgument(0)
		);

		Mockito.when(
			_portal.getPortletId(ArgumentMatchers.any(ActionRequest.class))
		).thenReturn(
			"portlet"
		);

		Mockito.when(
			_portal.getUploadPortletRequest(_actionRequest)
		).thenReturn(
			_uploadPortletRequest
		);

		Mockito.when(
			_actionParameters.getValue(ArgumentMatchers.anyString())
		).thenAnswer(
			invocation -> _parameters.get(invocation.<String>getArgument(0))
		);

		Mockito.when(
			_actionRequest.getActionParameters()
		).thenReturn(
			_actionParameters
		);

		ThemeDisplay themeDisplay = Mockito.mock(ThemeDisplay.class);

		Mockito.when(
			themeDisplay.getScopeGroupId()
		).thenReturn(
			20L
		);

		Mockito.when(
			_actionRequest.getAttribute(WebKeys.THEME_DISPLAY)
		).thenReturn(
			themeDisplay
		);

		Mockito.when(
			_actionResponse.getRenderParameters()
		).thenReturn(
			_mutableRenderParameters
		);

		_parameters.put("backURL", "/list");
		_parameters.put("fileEntryId", "5");
		_parameters.put("redirect", "/back");
	}

	@Test
	public void testAddUploadsThePackage() throws Exception {
		_parameters.put(Constants.CMD, Constants.ADD);

		InputStream inputStream = new ByteArrayInputStream(new byte[] {1});

		Mockito.when(
			_uploadPortletRequest.getFileName("file")
		).thenReturn(
			"demo.elpx"
		);

		Mockito.when(
			_uploadPortletRequest.getFileAsStream("file")
		).thenReturn(
			inputStream
		);

		ExeResource exeResource = Mockito.mock(ExeResource.class);

		Mockito.when(
			exeResource.getFileEntryId()
		).thenReturn(
			77L
		);

		Mockito.when(
			_exeResourceService.addResource(20L, "demo.elpx", inputStream)
		).thenReturn(
			exeResource
		);

		_command.doProcessAction(_actionRequest, _actionResponse);

		Mockito.verify(
			_mutableRenderParameters
		).setValue(
			"mvcPath", "/admin/view_resource.jsp"
		);

		Mockito.verify(
			_mutableRenderParameters
		).setValue(
			"fileEntryId", "77"
		);

		Mockito.verify(
			_actionResponse, Mockito.never()
		).sendRedirect(
			ArgumentMatchers.anyString()
		);
	}

	@Test
	public void testInvalidPackagesReturnToTheForm() throws Exception {
		_parameters.put(Constants.CMD, Constants.UPDATE);
		_parameters.put("version", "1.0");

		Mockito.when(
			_uploadPortletRequest.getFileName("file")
		).thenReturn(
			"demo.elpx"
		);

		Mockito.when(
			_uploadPortletRequest.getFileAsStream("file")
		).thenReturn(
			new ByteArrayInputStream(new byte[] {1})
		);

		ElpxException elpxException = new ElpxException(
			"the-package-contains-an-unsafe-path-x", "../x");

		Mockito.when(
			_exeResourceService.updatePackage(
				ArgumentMatchers.eq(5L), ArgumentMatchers.eq("1.0"),
				ArgumentMatchers.eq("demo.elpx"),
				ArgumentMatchers.any(InputStream.class))
		).thenThrow(
			elpxException
		);

		_command.doProcessAction(_actionRequest, _actionResponse);

		_sessionErrors.verify(
			() -> SessionErrors.add(
				_actionRequest, ElpxException.class, elpxException));

		Mockito.verify(
			_mutableRenderParameters
		).setValue(
			"mvcPath", "/admin/edit_resource.jsp"
		);

		Mockito.verify(
			_actionResponse, Mockito.never()
		).sendRedirect(
			ArgumentMatchers.anyString()
		);
	}

	@Test
	public void testMissingFilesAreReported() throws Exception {
		_parameters.put(Constants.CMD, Constants.ADD);

		_command.doProcessAction(_actionRequest, _actionResponse);

		_sessionErrors.verify(
			() -> SessionErrors.add(
				ArgumentMatchers.eq(_actionRequest),
				ArgumentMatchers.eq(ElpxException.class),
				ArgumentMatchers.any(ElpxException.class)));

		Mockito.verifyNoInteractions(_exeResourceService);
	}

	@Test
	public void testPublishUnpublishAndDelete() throws Exception {
		_parameters.put(
			Constants.CMD, EditResourceMVCActionCommand.CMD_PUBLISH);
		_parameters.put("version", "1.2");

		_command.doProcessAction(_actionRequest, _actionResponse);

		Mockito.verify(
			_exeResourceService
		).publish(
			5L, "1.2"
		);

		_parameters.put(
			Constants.CMD, EditResourceMVCActionCommand.CMD_UNPUBLISH);

		_command.doProcessAction(_actionRequest, _actionResponse);

		Mockito.verify(
			_exeResourceService
		).unpublish(
			5L
		);

		_parameters.put(Constants.CMD, Constants.DELETE);
		_parameters.put("redirect", null);

		_command.doProcessAction(_actionRequest, _actionResponse);

		Mockito.verify(
			_exeResourceService
		).deleteResource(
			5L
		);

		Mockito.verify(
			_actionResponse, Mockito.times(2)
		).sendRedirect(
			"/back"
		);
	}

	@Test
	public void testRejectedActionsStayOnThePage() throws Exception {
		_parameters.put(Constants.CMD, Constants.DELETE);

		PrincipalException principalException = new PrincipalException();

		Mockito.doThrow(
			principalException
		).when(
			_exeResourceService
		).deleteResource(
			5L
		);

		_command.doProcessAction(_actionRequest, _actionResponse);

		_sessionErrors.verify(
			() -> SessionErrors.add(
				_actionRequest, PrincipalException.class, principalException));

		Mockito.verifyNoInteractions(_mutableRenderParameters);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testUnknownCommandsFail() throws Exception {
		_parameters.put(Constants.CMD, "explode");

		_command.doProcessAction(_actionRequest, _actionResponse);
	}

	private final ActionParameters _actionParameters = Mockito.mock(
		ActionParameters.class);
	private final ActionRequest _actionRequest = Mockito.mock(
		ActionRequest.class);
	private final ActionResponse _actionResponse = Mockito.mock(
		ActionResponse.class);
	private final EditResourceMVCActionCommand _command =
		new EditResourceMVCActionCommand();
	private final ExeResourceService _exeResourceService = Mockito.mock(
		ExeResourceService.class);
	private final MutableRenderParameters _mutableRenderParameters =
		Mockito.mock(MutableRenderParameters.class);
	private final Map<String, String> _parameters = new HashMap<>();
	private final Portal _portal = Mockito.mock(Portal.class);
	private MockedStatic<SessionErrors> _sessionErrors;
	private MockedStatic<SessionMessages> _sessionMessages;
	private final UploadPortletRequest _uploadPortletRequest = Mockito.mock(
		UploadPortletRequest.class);

}