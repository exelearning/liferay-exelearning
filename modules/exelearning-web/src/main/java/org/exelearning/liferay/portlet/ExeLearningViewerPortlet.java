/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.portlet;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.WebKeys;

import java.io.IOException;

import javax.portlet.Portlet;
import javax.portlet.PortletException;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.exelearning.liferay.constants.ExeLearningPortletKeys;
import org.exelearning.liferay.resource.ExeResourceService;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Instanceable widget that embeds one published eXeLearning resource. Pages
 * keep a stable reference to the resource (its file entry ID), so publishing a
 * new revision updates every page that shows it.
 *
 * @author eXeLearning contributors
 */
@Component(
	property = {
		"com.liferay.portlet.css-class-wrapper=exelearning-viewer-portlet",
		"com.liferay.portlet.display-category=category.cms",
		"com.liferay.portlet.header-portlet-css=/js/exelearning.css",
		"com.liferay.portlet.header-portlet-javascript=/js/exelearning.js",
		"com.liferay.portlet.icon=/icons/exelearning.svg",
		"com.liferay.portlet.instanceable=true",
		"javax.portlet.display-name=eXeLearning",
		"javax.portlet.init-param.config-template=/viewer/configuration.jsp",
		"javax.portlet.init-param.template-path=/",
		"javax.portlet.init-param.view-template=/viewer/view.jsp",
		"javax.portlet.name=" + ExeLearningPortletKeys.VIEWER,
		"javax.portlet.resource-bundle=content.Language",
		"javax.portlet.security-role-ref=guest,power-user,user",
		"javax.portlet.version=3.0"
	},
	service = Portlet.class
)
public class ExeLearningViewerPortlet extends MVCPortlet {

	@Override
	public void render(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		prepare(renderRequest);

		super.render(renderRequest, renderResponse);
	}

	protected void prepare(RenderRequest renderRequest)
		throws PortletException {

		ThemeDisplay themeDisplay = (ThemeDisplay)renderRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		try {
			renderRequest.setAttribute(
				ViewerDisplayContext.class.getName(),
				new ViewerDisplayContext(
					renderRequest.getPreferences(), _exeResourceService,
					themeDisplay.getScopeGroupId(),
					System.currentTimeMillis() / 1000));
		}
		catch (PortalException portalException) {
			throw new PortletException(portalException);
		}
	}

	@Reference
	private ExeResourceService _exeResourceService;

}