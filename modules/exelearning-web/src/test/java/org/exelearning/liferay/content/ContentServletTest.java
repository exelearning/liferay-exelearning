/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.content;

import com.liferay.document.library.kernel.exception.NoSuchFileEntryException;
import com.liferay.portal.kernel.repository.model.FileVersion;
import com.liferay.portal.kernel.util.Portal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.servlet.http.HttpServletRequest;

import org.exelearning.liferay.publication.PublicationStore;
import org.exelearning.liferay.resource.ExeResourceService;
import org.exelearning.liferay.test.LiferayMocks;
import org.exelearning.liferay.test.ServletMocks;
import org.exelearning.liferay.test.TestZips;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.mockito.Mockito;

/**
 * @author eXeLearning contributors
 */
public class ContentServletTest {

	@Before
	public void setUp() throws Exception {
		Path root = temporaryFolder.newFolder(
		).toPath();

		_publicationStore = new PublicationStore(root.resolve("store"));

		_package = TestZips.write(root.resolve("p.elpx"), TestZips.package_());

		_publication = _publicationStore.publish(
			_COMPANY_ID, _FILE_ENTRY_ID, "1.0", _package,
			TestZips.configuration());

		Mockito.when(
			_exeResourceService.getTokenKey(_COMPANY_ID)
		).thenReturn(
			_KEY
		);

		Mockito.when(
			_exeResourceService.getPublication(
				_COMPANY_ID, _FILE_ENTRY_ID, "1.0")
		).thenReturn(
			_publication
		);

		Mockito.when(
			_exeResourceService.getPublicationStore()
		).thenReturn(
			_publicationStore
		);

		Mockito.when(
			_portal.getCompanyId(Mockito.any(HttpServletRequest.class))
		).thenReturn(
			_COMPANY_ID
		);

		TestZips.setField(
			_contentServlet, "_exeResourceService", _exeResourceService);
		TestZips.setField(_contentServlet, "_portal", _portal);
	}

	@Test
	public void testDownloadsThePackageOfTheTokenRevision() throws Exception {
		FileVersion fileVersion = LiferayMocks.fileVersion("1.0", _package);

		Mockito.when(
			_exeResourceService.getFileVersion(
				_COMPANY_ID, _FILE_ENTRY_ID, "1.0")
		).thenReturn(
			fileVersion
		);

		ServletMocks.Recorder recorder = _get("/" + _token());

		Assert.assertEquals(200, recorder.status);
		Assert.assertEquals("application/zip", recorder.contentType);
		Assert.assertArrayEquals(
			Files.readAllBytes(_package), recorder.body.toByteArray());
		Assert.assertTrue(
			recorder.headers.get(
				"Content-Disposition"
			).startsWith(
				"attachment; filename=\"demo.elpx\""
			));
	}

	@Test
	public void testRejectsMissingForgedAndExpiredTokens() throws Exception {
		Assert.assertEquals(404, _get(null).status);
		Assert.assertEquals(404, _get("/").status);
		Assert.assertEquals(403, _get("/garbage/index.html").status);

		String expired = ContentToken.issue(
			_KEY, _COMPANY_ID, _FILE_ENTRY_ID, "1.0", 1);

		Assert.assertEquals(403, _get("/" + expired + "/index.html").status);

		String foreign = ContentToken.issue(
			_KEY, 99, _FILE_ENTRY_ID, "1.0", Long.MAX_VALUE / 2000);

		Assert.assertEquals(403, _get("/" + foreign + "/index.html").status);
	}

	@Test
	public void testReportsMissingFilesAndRevisions() throws Exception {
		Assert.assertEquals(404, _get("/" + _token() + "/missing.html").status);
		Assert.assertEquals(404, _get("/" + _token() + "/../../p.elpx").status);

		Mockito.when(
			_exeResourceService.getPublication(
				_COMPANY_ID, _FILE_ENTRY_ID, "1.0")
		).thenThrow(
			new NoSuchFileEntryException()
		);

		Assert.assertEquals(404, _get("/" + _token() + "/index.html").status);
	}

	@Test
	public void testServesSandboxedContent() throws Exception {
		ServletMocks.Recorder recorder = _get("/" + _token() + "/");

		Assert.assertEquals(200, recorder.status);
		Assert.assertEquals(
			"<html><body>Home</body></html>", recorder.bodyText());
		Assert.assertEquals("text/html; charset=utf-8", recorder.contentType);

		String contentSecurityPolicy = recorder.headers.get(
			"Content-Security-Policy");

		Assert.assertTrue(
			contentSecurityPolicy.startsWith(
				"sandbox " + ContentServlet.SANDBOX + ";"));
		Assert.assertFalse(contentSecurityPolicy.contains("allow-same-origin"));
		Assert.assertTrue(
			contentSecurityPolicy.contains("frame-ancestors 'self'"));

		Assert.assertEquals(
			"nosniff", recorder.headers.get("X-Content-Type-Options"));
		Assert.assertEquals(
			"strict-origin-when-cross-origin",
			recorder.headers.get("Referrer-Policy"));
		Assert.assertEquals(
			"private, max-age=3600", recorder.headers.get("Cache-Control"));

		recorder = _get("/" + _token() + "/html/page-2.html");

		Assert.assertEquals(
			"<html><body>Page 2</body></html>", recorder.bodyText());
	}

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private ServletMocks.Recorder _get(String pathInfo) throws Exception {
		HttpServletRequest httpServletRequest = Mockito.mock(
			HttpServletRequest.class);

		Mockito.when(
			httpServletRequest.getMethod()
		).thenReturn(
			"GET"
		);

		Mockito.when(
			httpServletRequest.getPathInfo()
		).thenReturn(
			pathInfo
		);

		ServletMocks.Recorder recorder = ServletMocks.response();

		_contentServlet.doGet(httpServletRequest, recorder.httpServletResponse);

		return recorder;
	}

	private String _token() {
		return ContentToken.issue(
			_KEY, _COMPANY_ID, _FILE_ENTRY_ID, "1.0",
			(System.currentTimeMillis() / 1000) + 3600);
	}

	private static final long _COMPANY_ID = 10L;

	private static final long _FILE_ENTRY_ID = 20L;

	private static final byte[] _KEY = "key".getBytes(StandardCharsets.UTF_8);

	private final ContentServlet _contentServlet = new ContentServlet();
	private final ExeResourceService _exeResourceService = Mockito.mock(
		ExeResourceService.class);
	private Path _package;
	private final Portal _portal = Mockito.mock(Portal.class);
	private Path _publication;
	private PublicationStore _publicationStore;

}