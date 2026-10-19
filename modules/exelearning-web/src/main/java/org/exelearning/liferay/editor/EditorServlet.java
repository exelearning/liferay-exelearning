/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.editor;

import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.util.Portal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.net.URL;

import java.nio.charset.StandardCharsets;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exelearning.liferay.configuration.ExeLearningConfiguration;
import org.exelearning.liferay.content.FileResponses;
import org.exelearning.liferay.elpx.ElpxEntryNames;
import org.exelearning.liferay.elpx.ElpxException;
import org.exelearning.liferay.resource.ExeResourceService;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Serves the bundled static eXeLearning editor at
 * <code>/o/exelearning-editor/</code> from the
 * <code>org.exelearning.liferay.editor</code> bundle. The editor's
 * <code>index.html</code> receives <code>__EXE_EMBEDDING_CONFIG__</code>
 * before any editor script runs, restricting accepted messages to the portal
 * origin. No editor code is ever downloaded at runtime.
 *
 * @author eXeLearning contributors
 */
@Component(
	property = {
		"osgi.http.whiteboard.context.path=/",
		"osgi.http.whiteboard.servlet.name=org.exelearning.liferay.editor.EditorServlet",
		"osgi.http.whiteboard.servlet.pattern=" + EditorServlet.PATH + "/*"
	},
	service = Servlet.class
)
public class EditorServlet extends HttpServlet {

	public static final String EDITOR_BUNDLE_SYMBOLIC_NAME =
		"org.exelearning.liferay.editor";

	public static final String PATH = "/exelearning-editor";

	public static final String STATIC_DIRECTORY = "static/";

	/**
	 * Returns the bundle with the static editor, or <code>null</code> when it
	 * is not installed.
	 */
	public Bundle getEditorBundle() {
		Bundle bundle = _editorBundle;

		if ((bundle != null) && (bundle.getState() != Bundle.UNINSTALLED)) {
			return bundle;
		}

		for (Bundle candidate : _bundleContext.getBundles()) {
			if (EDITOR_BUNDLE_SYMBOLIC_NAME.equals(
					candidate.getSymbolicName()) &&
				(candidate.getState() != Bundle.UNINSTALLED)) {

				_editorBundle = candidate;

				return candidate;
			}
		}

		return null;
	}

	/**
	 * Inserts the embedding configuration and the editor language right after
	 * the opening <code>head</code> tag.
	 */
	protected static String injectConfiguration(
		String html, String configurationJSON, String locale) {

		StringBuilder sb = new StringBuilder();

		sb.append("<script>window.__EXE_EMBEDDING_CONFIG__=");
		sb.append(configurationJSON.replace("<", "\\u003c"));
		sb.append(";");

		if (locale != null) {

			// The editor reads its UI language from this preference; the
			// embedding configuration has no locale option

			sb.append("(function(){try{var k='exe_user_preferences',");
			sb.append("p=JSON.parse(localStorage.getItem(k)||'{}')||{};");
			sb.append("p.userPreferences=p.userPreferences||{};");
			sb.append("p.userPreferences.locale={value:'");
			sb.append(locale);
			sb.append("'};localStorage.setItem(k,JSON.stringify(p));}");
			sb.append("catch(e){}})();");
		}

		sb.append("</script>");

		Matcher matcher = _headPattern.matcher(html);

		if (!matcher.find()) {
			return sb.toString() + html;
		}

		return html.substring(0, matcher.end()) + sb.toString() +
			html.substring(matcher.end());
	}

	@Activate
	protected void activate(BundleContext bundleContext) {
		_bundleContext = bundleContext;
	}

	@Override
	protected void doGet(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse)
		throws IOException {

		ExeLearningConfiguration exeLearningConfiguration =
			_exeResourceService.getConfiguration();

		Bundle bundle = getEditorBundle();

		if (!exeLearningConfiguration.editorEnabled() || (bundle == null)) {
			FileResponses.error(
				httpServletResponse, HttpServletResponse.SC_NOT_FOUND);

			return;
		}

		String path = httpServletRequest.getPathInfo();

		if ((path == null) || path.equals("/")) {
			path = "/index.html";
		}

		String name;

		try {
			name = ElpxEntryNames.normalize(path.substring(1));
		}
		catch (ElpxException elpxException) {
			FileResponses.error(
				httpServletResponse, HttpServletResponse.SC_NOT_FOUND);

			return;
		}

		URL url = bundle.getEntry(STATIC_DIRECTORY + name);

		if ((url == null) || name.endsWith("/")) {
			FileResponses.error(
				httpServletResponse, HttpServletResponse.SC_NOT_FOUND);

			return;
		}

		Set<String> origins = new LinkedHashSet<>();

		origins.add(_getOrigin(httpServletRequest));

		for (String origin :
				exeLearningConfiguration.allowedEditorParentOrigins()) {

			if (!origin.isBlank()) {
				origins.add(origin.trim());
			}
		}

		httpServletResponse.setHeader(
			"Content-Security-Policy",
			"frame-ancestors 'self' " + String.join(" ", origins));
		httpServletResponse.setHeader(
			"Referrer-Policy", "strict-origin-when-cross-origin");

		if (name.equals("index.html")) {
			httpServletResponse.setHeader("Cache-Control", "no-cache");

			_sendIndex(
				httpServletRequest, httpServletResponse, bundle, url, origins);

			return;
		}

		httpServletResponse.setHeader("Cache-Control", "public, max-age=86400");

		// Bundle entry connections do not report a reliable content length

		byte[] bytes;

		try (InputStream inputStream = url.openStream()) {
			bytes = inputStream.readAllBytes();
		}

		FileResponses.send(
			httpServletRequest, httpServletResponse, name, bytes.length,
			new ByteArrayInputStream(bytes));
	}

	private String _getLocale(
		HttpServletRequest httpServletRequest, Bundle bundle) {

		String locale = httpServletRequest.getParameter("locale");

		if ((locale == null) ||
			!_localePattern.matcher(
				locale
			).matches()) {

			return null;
		}

		URL url = bundle.getEntry(
			STATIC_DIRECTORY + "app/common/i18n/common_i18n." + locale + ".js");

		if (url == null) {
			return null;
		}

		return locale;
	}

	private String _getOrigin(HttpServletRequest httpServletRequest) {
		String portalURL = _portal.getPortalURL(httpServletRequest);

		int index = portalURL.indexOf('/', portalURL.indexOf("//") + 2);

		if (index > 0) {
			return portalURL.substring(0, index);
		}

		return portalURL;
	}

	private void _sendIndex(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, Bundle bundle, URL url,
			Set<String> origins)
		throws IOException {

		String html;

		try (InputStream inputStream = url.openStream()) {
			html = new String(
				inputStream.readAllBytes(), StandardCharsets.UTF_8);
		}

		JSONObject jsonObject = _jsonFactory.createJSONObject();

		jsonObject.put(
			"basePath", _portal.getPathModule() + PATH
		).put(
			"hideUI",
			_jsonFactory.createJSONObject(
			).put(
				"fileMenu", true
			).put(
				"saveButton", true
			).put(
				"shareButton", true
			).put(
				"userMenu", true
			)
		).put(
			"parentOrigin", _getOrigin(httpServletRequest)
		).put(
			"trustedOrigins", _jsonFactory.createJSONArray(origins)
		);

		byte[] bytes = injectConfiguration(
			html, jsonObject.toString(), _getLocale(httpServletRequest, bundle)
		).getBytes(
			StandardCharsets.UTF_8
		);

		httpServletResponse.setContentType("text/html; charset=utf-8");
		httpServletResponse.setContentLength(bytes.length);
		httpServletResponse.setHeader("X-Content-Type-Options", "nosniff");

		httpServletResponse.getOutputStream(
		).write(
			bytes
		);
	}

	private static final Pattern _headPattern = Pattern.compile(
		"<head(\\s[^>]*)?>", Pattern.CASE_INSENSITIVE);
	private static final Pattern _localePattern = Pattern.compile("[a-z]{2,3}");
	private static final long serialVersionUID = 1L;

	private BundleContext _bundleContext;
	private volatile Bundle _editorBundle;

	@Reference
	private ExeResourceService _exeResourceService;

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private Portal _portal;

}