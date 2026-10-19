/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.editor;

import com.liferay.portal.kernel.util.Portal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.exelearning.liferay.configuration.ExeLearningConfiguration;
import org.exelearning.liferay.resource.ExeResourceService;
import org.exelearning.liferay.test.ServletMocks;
import org.exelearning.liferay.test.TestJSON;
import org.exelearning.liferay.test.TestZips;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

/**
 * @author eXeLearning contributors
 */
public class EditorServletTest {

	@Before
	public void setUp() throws Exception {
		_static = temporaryFolder.newFolder(
			"static"
		).toPath();

		String html =
			"<!DOCTYPE html><html><HEAD lang=\"en\"><title>e</title></head>" +
				"</html>";

		Files.write(
			_static.resolve("index.html"),
			html.getBytes(StandardCharsets.UTF_8));

		Files.createDirectories(_static.resolve("app/common/i18n"));
		Files.write(
			_static.resolve("app/common/i18n/common_i18n.es.js"),
			"var x = 1;".getBytes(StandardCharsets.UTF_8));

		Mockito.when(
			_bundle.getSymbolicName()
		).thenReturn(
			EditorServlet.EDITOR_BUNDLE_SYMBOLIC_NAME
		);

		Mockito.when(
			_bundle.getState()
		).thenReturn(
			Bundle.ACTIVE
		);

		Mockito.when(
			_bundle.getEntry(ArgumentMatchers.anyString())
		).thenAnswer(
			invocation -> {
				String entry = invocation.getArgument(0);

				Path path = _static.resolve(
					entry.substring(EditorServlet.STATIC_DIRECTORY.length()));

				if (!Files.exists(path)) {
					return null;
				}

				return path.toUri(
				).toURL();
			}
		);

		Bundle other = Mockito.mock(Bundle.class);

		Mockito.when(
			other.getSymbolicName()
		).thenReturn(
			"other"
		);

		Mockito.when(
			_bundleContext.getBundles()
		).thenReturn(
			new Bundle[] {other, _bundle}
		);

		_configure(true, "https://cms.example", " ");

		Mockito.when(
			_portal.getPathModule()
		).thenReturn(
			"/o"
		);

		Mockito.when(
			_portal.getPortalURL(Mockito.any(HttpServletRequest.class))
		).thenReturn(
			"https://portal.example/web"
		);

		TestZips.setField(
			_editorServlet, "_exeResourceService", _exeResourceService);
		TestZips.setField(_editorServlet, "_jsonFactory", TestJSON.factory());
		TestZips.setField(_editorServlet, "_portal", _portal);

		_editorServlet.activate(_bundleContext);
	}

	@Test
	public void testFindsAndCachesTheEditorBundle() {
		Assert.assertSame(_bundle, _editorServlet.getEditorBundle());
		Assert.assertSame(_bundle, _editorServlet.getEditorBundle());

		Mockito.verify(
			_bundleContext, Mockito.times(1)
		).getBundles();

		Mockito.when(
			_bundle.getState()
		).thenReturn(
			Bundle.UNINSTALLED
		);

		Assert.assertNull(_editorServlet.getEditorBundle());
	}

	@Test
	public void testInjectsTheEmbeddingConfiguration() throws Exception {
		ServletMocks.Recorder recorder = _get(null, "es");

		String html = recorder.bodyText();

		Assert.assertEquals(200, recorder.status);
		Assert.assertEquals("text/html; charset=utf-8", recorder.contentType);

		Assert.assertTrue(
			html,
			html.startsWith(
				"<!DOCTYPE html><html><HEAD lang=\"en\"><script>" +
					"window.__EXE_EMBEDDING_CONFIG__="));
		Assert.assertTrue(
			html.contains("\"basePath\":\"/o/exelearning-editor\""));
		Assert.assertTrue(
			html.contains(
				"\"trustedOrigins\":[\"https://portal.example\"," +
					"\"https://cms.example\"]"));
		Assert.assertTrue(
			html.contains("\"parentOrigin\":\"https://portal.example\""));
		Assert.assertTrue(html.contains("\"saveButton\":true"));
		Assert.assertTrue(html.contains("locale={value:'es'}"));
		Assert.assertEquals("no-cache", recorder.headers.get("Cache-Control"));
		Assert.assertEquals(
			"frame-ancestors 'self' https://portal.example https://cms.example",
			recorder.headers.get("Content-Security-Policy"));

		Assert.assertFalse(
			_get(
				"/", "../../x"
			).bodyText(
			).contains(
				"locale="
			));
		Assert.assertFalse(
			_get(
				"/index.html", "fr"
			).bodyText(
			).contains(
				"locale="
			));
	}

	@Test
	public void testInjectsWithoutHeadAndEscapesScriptBreakouts() {
		Assert.assertEquals(
			"<script>window.__EXE_EMBEDDING_CONFIG__=" +
				"{\"a\":\"\\u003c/script>\"};</script><p>x</p>",
			EditorServlet.injectConfiguration(
				"<p>x</p>", "{\"a\":\"</script>\"}", null));
	}

	@Test
	public void testReturnsNotFoundWhenDisabledOrMissing() throws Exception {
		Assert.assertEquals(404, _get("/../secret", null).status);
		Assert.assertEquals(404, _get("/missing.js", null).status);
		Assert.assertEquals(404, _get("/app/", null).status);

		_configure(false);

		Assert.assertEquals(404, _get("/index.html", null).status);

		_configure(true);

		Mockito.when(
			_bundleContext.getBundles()
		).thenReturn(
			new Bundle[0]
		);

		Mockito.when(
			_bundle.getState()
		).thenReturn(
			Bundle.UNINSTALLED
		);

		Assert.assertEquals(404, _get("/index.html", null).status);
	}

	@Test
	public void testServesStaticFiles() throws Exception {
		ServletMocks.Recorder recorder = _get(
			"/app/common/i18n/common_i18n.es.js", null);

		Assert.assertEquals("var x = 1;", recorder.bodyText());
		Assert.assertEquals(10, recorder.contentLength);
		Assert.assertEquals(
			"text/javascript; charset=utf-8", recorder.contentType);
		Assert.assertEquals(
			"public, max-age=86400", recorder.headers.get("Cache-Control"));
	}

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private void _configure(boolean enabled, String... origins) {
		Map<String, Object> overrides = new HashMap<>();

		overrides.put("allowedEditorParentOrigins", origins);
		overrides.put("editorEnabled", enabled);

		ExeLearningConfiguration exeLearningConfiguration =
			TestZips.configuration(overrides);

		Mockito.when(
			_exeResourceService.getConfiguration()
		).thenReturn(
			exeLearningConfiguration
		);
	}

	private ServletMocks.Recorder _get(String pathInfo, String locale)
		throws Exception {

		HttpServletRequest httpServletRequest = Mockito.mock(
			HttpServletRequest.class);

		Mockito.when(
			httpServletRequest.getMethod()
		).thenReturn(
			"GET"
		);

		Mockito.when(
			httpServletRequest.getParameter("locale")
		).thenReturn(
			locale
		);

		Mockito.when(
			httpServletRequest.getPathInfo()
		).thenReturn(
			pathInfo
		);

		ServletMocks.Recorder recorder = ServletMocks.response();

		_editorServlet.doGet(httpServletRequest, recorder.httpServletResponse);

		return recorder;
	}

	private final Bundle _bundle = Mockito.mock(Bundle.class);
	private final BundleContext _bundleContext = Mockito.mock(
		BundleContext.class);
	private final EditorServlet _editorServlet = new EditorServlet();
	private final ExeResourceService _exeResourceService = Mockito.mock(
		ExeResourceService.class);
	private final Portal _portal = Mockito.mock(Portal.class);
	private Path _static;

}