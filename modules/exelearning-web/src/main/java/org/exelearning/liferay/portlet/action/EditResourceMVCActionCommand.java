/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.portlet.action;

import com.liferay.document.library.kernel.exception.NoSuchFileEntryException;
import com.liferay.portal.kernel.portlet.bridges.mvc.BaseMVCActionCommand;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCActionCommand;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.servlet.SessionErrors;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.upload.UploadPortletRequest;
import com.liferay.portal.kernel.util.Constants;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.util.WebKeys;

import java.io.InputStream;

import javax.portlet.ActionParameters;
import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.MutableRenderParameters;

import org.exelearning.liferay.constants.ExeLearningPortletKeys;
import org.exelearning.liferay.elpx.ElpxException;
import org.exelearning.liferay.resource.ExeResource;
import org.exelearning.liferay.resource.ExeResourceService;
import org.exelearning.liferay.resource.ResourceConflictException;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Handles the administration actions on eXeLearning resources: add, update
 * (new revision), publish, unpublish and delete. Authorization is enforced by
 * {@link ExeResourceService}; expected failures are reported to the form with
 * {@link SessionErrors}.
 *
 * @author eXeLearning contributors
 */
@Component(
	property = {
		"javax.portlet.name=" + ExeLearningPortletKeys.ADMIN,
		"mvc.command.name=/exelearning/edit_resource"
	},
	service = MVCActionCommand.class
)
public class EditResourceMVCActionCommand extends BaseMVCActionCommand {

	public static final String CMD_PUBLISH = "publish";

	public static final String CMD_UNPUBLISH = "unpublish";

	@Override
	protected void doProcessAction(
			ActionRequest actionRequest, ActionResponse actionResponse)
		throws Exception {

		ActionParameters actionParameters = actionRequest.getActionParameters();

		String cmd = GetterUtil.getString(
			actionParameters.getValue(Constants.CMD));
		long fileEntryId = GetterUtil.getLong(
			actionParameters.getValue("fileEntryId"));
		String version = actionParameters.getValue("version");

		try {
			if (cmd.equals(Constants.ADD) || cmd.equals(Constants.UPDATE)) {
				ExeResource exeResource = _upload(
					actionRequest, cmd, fileEntryId, version);

				// Show the stored resource with its preview, like Documents and
				// Media does after an upload. The form sends its back link as
				// "backURL" because MVCPortlet follows a "redirect" parameter.

				MutableRenderParameters mutableRenderParameters =
					actionResponse.getRenderParameters();

				mutableRenderParameters.setValue(
					"fileEntryId",
					String.valueOf(exeResource.getFileEntryId()));
				mutableRenderParameters.setValue(
					"mvcPath", "/admin/view_resource.jsp");
				mutableRenderParameters.setValue(
					"redirect", actionParameters.getValue("backURL"));

				return;
			}
			else if (cmd.equals(CMD_PUBLISH)) {
				_exeResourceService.publish(fileEntryId, version);
			}
			else if (cmd.equals(CMD_UNPUBLISH)) {
				_exeResourceService.unpublish(fileEntryId);
			}
			else if (cmd.equals(Constants.DELETE)) {
				_exeResourceService.deleteResource(fileEntryId);
			}
			else {
				throw new IllegalArgumentException("Unknown command " + cmd);
			}
		}
		catch (ElpxException | NoSuchFileEntryException | PrincipalException |
			   ResourceConflictException exception) {

			SessionErrors.add(actionRequest, exception.getClass(), exception);

			hideDefaultErrorMessage(actionRequest);

			if (cmd.equals(Constants.ADD) || cmd.equals(Constants.UPDATE)) {
				MutableRenderParameters mutableRenderParameters =
					actionResponse.getRenderParameters();

				mutableRenderParameters.setValue(
					"mvcPath", "/admin/edit_resource.jsp");
				mutableRenderParameters.setValue(
					"fileEntryId", String.valueOf(fileEntryId));
				mutableRenderParameters.setValue(
					"redirect", actionParameters.getValue("backURL"));
			}

			return;
		}

		String redirect = _portal.escapeRedirect(
			actionParameters.getValue("redirect"));

		if (Validator.isNotNull(redirect)) {
			actionResponse.sendRedirect(redirect);
		}
	}

	private ExeResource _upload(
			ActionRequest actionRequest, String cmd, long fileEntryId,
			String version)
		throws Exception {

		UploadPortletRequest uploadPortletRequest =
			_portal.getUploadPortletRequest(actionRequest);

		String fileName = uploadPortletRequest.getFileName("file");

		try (InputStream inputStream = uploadPortletRequest.getFileAsStream(
				"file")) {

			if ((inputStream == null) || Validator.isNull(fileName)) {
				throw new ElpxException("please-select-an-elpx-file");
			}

			if (cmd.equals(Constants.ADD)) {
				ThemeDisplay themeDisplay =
					(ThemeDisplay)actionRequest.getAttribute(
						WebKeys.THEME_DISPLAY);

				return _exeResourceService.addResource(
					themeDisplay.getScopeGroupId(), fileName, inputStream);
			}

			return _exeResourceService.updatePackage(
				fileEntryId, version, fileName, inputStream);
		}
	}

	@Reference
	private ExeResourceService _exeResourceService;

	@Reference
	private Portal _portal;

}