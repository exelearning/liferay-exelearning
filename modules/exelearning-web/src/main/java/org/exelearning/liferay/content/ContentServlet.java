/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.content;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.repository.model.FileVersion;
import com.liferay.portal.kernel.util.Portal;

import java.io.IOException;
import java.io.InputStream;

import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.Servlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exelearning.liferay.elpx.ElpxArchive;
import org.exelearning.liferay.resource.ExeResourceService;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * Serves published HTML5 exports at
 * <code>/o/exelearning-content/{token}/{path}</code>. The signed token is the
 * only credential, so relative links keep working inside the sandboxed iframe
 * that has an opaque origin and sends no cookies. Every response carries the
 * same sandbox in its Content-Security-Policy, so the untrusted content stays
 * in an opaque origin even when its URL is opened directly.
 *
 * @author eXeLearning contributors
 */
@Component(
	property = {
		"osgi.http.whiteboard.context.path=/",
		"osgi.http.whiteboard.servlet.name=org.exelearning.liferay.content.ContentServlet",
		"osgi.http.whiteboard.servlet.pattern=" + ExeResourceService.CONTENT_SERVLET_PATH + "/*"
	},
	service = Servlet.class
)
public class ContentServlet extends HttpServlet {

	/**
	 * Sandbox tokens shared by the iframe attribute and the response CSP. They
	 * match eXeLearning's public viewer policy and deliberately omit
	 * <code>allow-same-origin</code>.
	 */
	public static final String SANDBOX =
		"allow-scripts allow-popups allow-forms allow-downloads " +
			"allow-popups-to-escape-sandbox";

	public static final String CONTENT_SECURITY_POLICY = String.join(
		"; ", "sandbox " + SANDBOX, "default-src 'self' data: blob: https:",
		"img-src 'self' data: blob: https:",
		"media-src 'self' data: blob: https:", "font-src 'self' data: https:",
		"style-src 'self' 'unsafe-inline' https:",
		"script-src 'self' 'unsafe-inline' 'unsafe-eval' https:",
		"connect-src 'self' https:", "frame-src 'self' https:",
		"child-src 'self' https:", "object-src 'none'", "base-uri 'none'",
		"frame-ancestors 'self'");

	@Override
	protected void doGet(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse)
		throws IOException {

		String pathInfo = httpServletRequest.getPathInfo();

		if ((pathInfo == null) || (pathInfo.length() < 2)) {
			FileResponses.error(
				httpServletResponse, HttpServletResponse.SC_NOT_FOUND);

			return;
		}

		int index = pathInfo.indexOf('/', 1);

		String token =
			(index < 0) ? pathInfo.substring(1) : pathInfo.substring(1, index);

		try {
			long companyId = _portal.getCompanyId(httpServletRequest);

			ContentToken contentToken = ContentToken.verify(
				_exeResourceService.getTokenKey(companyId), companyId, token,
				System.currentTimeMillis() / 1000);

			if (contentToken == null) {
				FileResponses.error(
					httpServletResponse, HttpServletResponse.SC_FORBIDDEN);

				return;
			}

			if (index < 0) {
				_sendPackage(
					httpServletRequest, httpServletResponse, companyId,
					contentToken);

				return;
			}

			String relativePath = pathInfo.substring(index + 1);

			if (relativePath.isEmpty() || relativePath.endsWith("/")) {
				relativePath += ElpxArchive.INDEX_HTML;
			}

			Path file = _exeResourceService.getPublicationStore(
			).resolve(
				_exeResourceService.getPublication(
					companyId, contentToken.getFileEntryId(),
					contentToken.getVersion()),
				relativePath
			);

			if (file == null) {
				FileResponses.error(
					httpServletResponse, HttpServletResponse.SC_NOT_FOUND);

				return;
			}

			httpServletResponse.setHeader("Access-Control-Allow-Origin", "*");
			httpServletResponse.setHeader(
				"Cache-Control", "private, max-age=3600");
			httpServletResponse.setHeader(
				"Content-Security-Policy", CONTENT_SECURITY_POLICY);
			httpServletResponse.setHeader(
				"Cross-Origin-Resource-Policy", "cross-origin");
			httpServletResponse.setHeader(
				"Permissions-Policy",
				"camera=(), microphone=(), geolocation=(), payment=()");
			httpServletResponse.setHeader(
				"Referrer-Policy", "strict-origin-when-cross-origin");

			try (InputStream inputStream = Files.newInputStream(file)) {
				FileResponses.send(
					httpServletRequest, httpServletResponse,
					file.getFileName(
					).toString(),
					Files.size(file), inputStream);
			}
		}
		catch (PortalException portalException) {
			if (_log.isDebugEnabled()) {
				_log.debug(portalException);
			}

			FileResponses.error(
				httpServletResponse, HttpServletResponse.SC_NOT_FOUND);
		}
	}

	/**
	 * Sends the original package of the revision a token grants access to,
	 * which backs the widget's download button for every viewer, guests
	 * included.
	 */
	private void _sendPackage(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, long companyId,
			ContentToken contentToken)
		throws IOException, PortalException {

		FileVersion fileVersion = _exeResourceService.getFileVersion(
			companyId, contentToken.getFileEntryId(),
			contentToken.getVersion());

		httpServletResponse.setHeader("Cache-Control", "private, no-store");
		httpServletResponse.setHeader(
			"Content-Disposition",
			ContentDispositions.attachment(fileVersion.getFileName()));

		try (InputStream inputStream = fileVersion.getContentStream(true)) {
			FileResponses.send(
				httpServletRequest, httpServletResponse, "package.zip",
				fileVersion.getSize(), inputStream);
		}
	}

	private static final Log _log = LogFactoryUtil.getLog(ContentServlet.class);

	private static final long serialVersionUID = 1L;

	@Reference
	private ExeResourceService _exeResourceService;

	@Reference
	private Portal _portal;

}