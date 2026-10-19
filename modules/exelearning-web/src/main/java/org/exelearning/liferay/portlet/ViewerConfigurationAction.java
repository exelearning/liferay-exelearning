/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.portlet;

import com.liferay.portal.kernel.portlet.ConfigurationAction;
import com.liferay.portal.kernel.portlet.DefaultConfigurationAction;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.WebKeys;

import javax.portlet.ActionParameters;
import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.PortletConfig;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exelearning.liferay.constants.ExeLearningPortletKeys;
import org.exelearning.liferay.resource.ExeResource;
import org.exelearning.liferay.resource.ExeResourceService;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Stores the viewer preferences after checking that the selected resource is
 * visible to the configuring user and belongs to the widget's site.
 *
 * @author eXeLearning contributors
 */
@Component(
	property = "javax.portlet.name=" + ExeLearningPortletKeys.VIEWER,
	service = ConfigurationAction.class
)
public class ViewerConfigurationAction extends DefaultConfigurationAction {

	@Override
	public void include(
			PortletConfig portletConfig, HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse)
		throws Exception {

		ThemeDisplay themeDisplay =
			(ThemeDisplay)httpServletRequest.getAttribute(
				WebKeys.THEME_DISPLAY);

		httpServletRequest.setAttribute(
			ExeResource.class.getName(),
			_exeResourceService.getResources(
				themeDisplay.getScopeGroupId(), null));

		super.include(portletConfig, httpServletRequest, httpServletResponse);
	}

	@Override
	public void processAction(
			PortletConfig portletConfig, ActionRequest actionRequest,
			ActionResponse actionResponse)
		throws Exception {

		ActionParameters actionParameters = actionRequest.getActionParameters();

		long fileEntryId = GetterUtil.getLong(
			actionParameters.getValue(
				"preferences--" +
					ViewerDisplayContext.PREFERENCE_FILE_ENTRY_ID + "--"));

		if (fileEntryId > 0) {
			ThemeDisplay themeDisplay =
				(ThemeDisplay)actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

			ExeResource exeResource = _exeResourceService.getResource(
				fileEntryId);

			if (exeResource.getGroupId() != themeDisplay.getScopeGroupId()) {
				throw new PrincipalException(
					"Resource " + fileEntryId + " belongs to another site");
			}
		}

		super.processAction(portletConfig, actionRequest, actionResponse);
	}

	@Reference
	private ExeResourceService _exeResourceService;

}