/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.portlet;

import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.ResourceBundleUtil;
import com.liferay.portal.kernel.util.WebKeys;

import java.io.IOException;

import java.util.Locale;

import javax.portlet.Portlet;
import javax.portlet.PortletException;
import javax.portlet.RenderParameters;
import javax.portlet.RenderRequest;
import javax.portlet.RenderResponse;

import org.exelearning.liferay.configuration.ExeLearningConfiguration;
import org.exelearning.liferay.constants.ExeLearningPortletKeys;
import org.exelearning.liferay.editor.EditorServlet;
import org.exelearning.liferay.resource.ExeResourceService;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Site administration screen (Content &amp; Data &gt; eXeLearning) to manage
 * the site's eXeLearning resources.
 *
 * @author eXeLearning contributors
 */
@Component(
	property = {
		"com.liferay.portlet.add-default-resource=true",
		"com.liferay.portlet.css-class-wrapper=exelearning-admin-portlet",
		"com.liferay.portlet.display-category=category.hidden",
		"com.liferay.portlet.header-portlet-css=/js/exelearning.css",
		"com.liferay.portlet.header-portlet-javascript=/js/exelearning.js",
		"com.liferay.portlet.preferences-owned-by-group=true",
		"com.liferay.portlet.private-request-attributes=false",
		"com.liferay.portlet.private-session-attributes=false",
		"com.liferay.portlet.use-default-template=true",
		"javax.portlet.display-name=eXeLearning",
		"javax.portlet.init-param.template-path=/",
		"javax.portlet.init-param.view-template=/admin/view.jsp",
		"javax.portlet.name=" + ExeLearningPortletKeys.ADMIN,
		"javax.portlet.resource-bundle=content.Language",
		"javax.portlet.security-role-ref=administrator",
		"javax.portlet.version=3.0"
	},
	service = Portlet.class
)
public class ExeLearningAdminPortlet extends MVCPortlet {

	public static final String API_BASE = "apiBase";

	public static final String DISPLAY_CONTEXT =
		ExeLearningAdminDisplayContext.class.getName();

	public static final String EDITOR_URL = "editorURL";

	public static final String I18N = "i18n";

	@Override
	public void render(
			RenderRequest renderRequest, RenderResponse renderResponse)
		throws IOException, PortletException {

		prepare(renderRequest);

		super.render(renderRequest, renderResponse);
	}

	protected void prepare(RenderRequest renderRequest) {
		ThemeDisplay themeDisplay = (ThemeDisplay)renderRequest.getAttribute(
			WebKeys.THEME_DISPLAY);

		ExeLearningConfiguration exeLearningConfiguration =
			_exeResourceService.getConfiguration();

		RenderParameters renderParameters = renderRequest.getRenderParameters();

		renderRequest.setAttribute(
			DISPLAY_CONTEXT,
			new ExeLearningAdminDisplayContext(
				_exeResourceService, themeDisplay.getScopeGroupId(),
				GetterUtil.getString(renderParameters.getValue("keywords")),
				renderParameters.getValue("displayStyle"),
				exeLearningConfiguration.editorEnabled()));

		renderRequest.setAttribute(
			API_BASE, _portal.getPathModule() + "/exelearning/v1");

		String editorURL = "";

		if (exeLearningConfiguration.editorEnabled()) {
			Locale locale = themeDisplay.getLocale();

			editorURL =
				_portal.getPathModule() + EditorServlet.PATH +
					"/index.html?locale=" + locale.getLanguage();
		}

		renderRequest.setAttribute(EDITOR_URL, editorURL);
		renderRequest.setAttribute(
			I18N,
			FrontendStrings.toJSON(
				_jsonFactory, _language,
				ResourceBundleUtil.getBundle(
					"content.Language", themeDisplay.getLocale(), getClass())));
	}

	@Reference
	private ExeResourceService _exeResourceService;

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private Language _language;

	@Reference
	private Portal _portal;

}