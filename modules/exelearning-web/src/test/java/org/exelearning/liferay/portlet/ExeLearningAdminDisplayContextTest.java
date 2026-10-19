/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.portlet;

import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.language.LanguageUtil;
import com.liferay.portal.kernel.repository.model.FileEntry;

import java.io.Serializable;

import java.nio.file.Path;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.portlet.PortletURL;

import org.exelearning.liferay.publication.PublicationStore;
import org.exelearning.liferay.resource.ExeResource;
import org.exelearning.liferay.resource.ExeResourceService;
import org.exelearning.liferay.resource.ResourceAttributes;
import org.exelearning.liferay.test.LiferayMocks;
import org.exelearning.liferay.test.TestZips;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * @author eXeLearning contributors
 */
public class ExeLearningAdminDisplayContextTest {

	@Before
	public void setUp() throws Exception {
		_package = TestZips.write(
			temporaryFolder.newFile(
			).toPath(),
			TestZips.package_());
	}

	@Test
	public void testCreationMenuOnlyForAuthors() throws Exception {
		Mockito.when(
			_exeResourceService.canAddResource(LiferayMocks.GROUP_ID)
		).thenReturn(
			true, false
		);

		ExeLearningAdminDisplayContext exeLearningAdminDisplayContext = _create(
			"");

		String creationMenu = String.valueOf(
			exeLearningAdminDisplayContext.getCreationMenu("/add", "Add"));

		Assert.assertTrue(creationMenu.contains("/add"));

		Assert.assertNull(
			exeLearningAdminDisplayContext.getCreationMenu("/add", "Add"));
	}

	@Test
	public void testPreviewURL() throws Exception {
		ExeResource editable = _resource("1.1", "1.0", true);

		Mockito.when(
			_exeResourceService.preview(3000L, "1.1", 7)
		).thenReturn(
			"/preview"
		);

		Mockito.when(
			_exeResourceService.getContentPath(
				LiferayMocks.COMPANY_ID, 3000L, "1.0", 7)
		).thenReturn(
			"/published"
		);

		ExeLearningAdminDisplayContext exeLearningAdminDisplayContext = _create(
			"");

		Assert.assertEquals(
			"/preview",
			exeLearningAdminDisplayContext.getPreviewURL(editable, "1.1", 7));
		Assert.assertEquals(
			"/published",
			exeLearningAdminDisplayContext.getPreviewURL(
				_resource("1.1", "1.0", false), "1.1", 7));
		Assert.assertNull(
			exeLearningAdminDisplayContext.getPreviewURL(
				_resource("1.1", "", false), "1.1", 7));
	}

	@Test
	public void testResourcesAreLoadedOnce() throws Exception {
		List<ExeResource> exeResources = Collections.singletonList(
			_resource("1.0", "1.0", true));

		Mockito.when(
			_exeResourceService.getResources(LiferayMocks.GROUP_ID, "demo")
		).thenReturn(
			exeResources
		);

		Mockito.when(
			_exeResourceService.getResource(3000L)
		).thenReturn(
			exeResources.get(0)
		);

		ExeLearningAdminDisplayContext exeLearningAdminDisplayContext = _create(
			"demo");

		Assert.assertEquals(
			"demo", exeLearningAdminDisplayContext.getKeywords());
		Assert.assertSame(
			exeResources, exeLearningAdminDisplayContext.getResources());
		Assert.assertSame(
			exeResources, exeLearningAdminDisplayContext.getResources());
		Assert.assertSame(
			exeResources.get(0),
			exeLearningAdminDisplayContext.getResource(3000L));
		Assert.assertTrue(exeLearningAdminDisplayContext.isEditorEnabled());

		Mockito.verify(
			_exeResourceService, Mockito.times(1)
		).getResources(
			LiferayMocks.GROUP_ID, "demo"
		);
	}

	@Test
	public void testStatus() throws Exception {
		ExeLearningAdminDisplayContext exeLearningAdminDisplayContext = _create(
			"");

		ExeResource current = _resource("1.0", "1.0", true);
		ExeResource outdated = _resource("1.1", "1.0", true);
		ExeResource draft = _resource("1.0", "", true);

		Assert.assertEquals(
			"published", exeLearningAdminDisplayContext.getStatusKey(current));
		Assert.assertEquals(
			"success",
			exeLearningAdminDisplayContext.getStatusDisplayType(current));
		Assert.assertFalse(
			exeLearningAdminDisplayContext.isPublishable(current));

		Assert.assertEquals(
			"newer-version-available",
			exeLearningAdminDisplayContext.getStatusKey(outdated));
		Assert.assertEquals(
			"warning",
			exeLearningAdminDisplayContext.getStatusDisplayType(outdated));
		Assert.assertTrue(
			exeLearningAdminDisplayContext.isPublishable(outdated));

		Assert.assertEquals(
			"not-published",
			exeLearningAdminDisplayContext.getStatusKey(draft));
		Assert.assertEquals(
			"secondary",
			exeLearningAdminDisplayContext.getStatusDisplayType(draft));
		Assert.assertTrue(exeLearningAdminDisplayContext.isPublishable(draft));
		Assert.assertFalse(
			exeLearningAdminDisplayContext.isPublishable(
				_resource("1.0", "", false)));

		ExeResource sourceOnly = new ExeResource(
			current.getFileEntry(), Collections.emptyMap(), true, true, true,
			Collections.emptyList());

		Assert.assertEquals(
			"no-html-export",
			exeLearningAdminDisplayContext.getStatusKey(sourceOnly));
		Assert.assertFalse(
			exeLearningAdminDisplayContext.isPublishable(sourceOnly));
	}

	@Test
	public void testDisplayStyleAndViewTypes() throws Exception {
		Assert.assertEquals(
			ExeLearningAdminDisplayContext.DISPLAY_STYLE_ICON,
			_create(
				""
			).getDisplayStyle());

		ExeLearningAdminDisplayContext exeLearningAdminDisplayContext =
			new ExeLearningAdminDisplayContext(
				_exeResourceService, LiferayMocks.GROUP_ID, "",
				ExeLearningAdminDisplayContext.DISPLAY_STYLE_DESCRIPTIVE,
				false);

		Assert.assertEquals(
			ExeLearningAdminDisplayContext.DISPLAY_STYLE_DESCRIPTIVE,
			exeLearningAdminDisplayContext.getDisplayStyle());
		Assert.assertFalse(exeLearningAdminDisplayContext.isEditorEnabled());

		Language language = Mockito.mock(Language.class);

		Mockito.when(
			language.get(
				ArgumentMatchers.nullable(Locale.class),
				ArgumentMatchers.anyString())
		).thenAnswer(
			invocation -> invocation.getArgument(1)
		);

		new LanguageUtil(
		).setLanguage(
			language
		);

		PortletURL portletURL = Mockito.mock(PortletURL.class);

		Mockito.when(
			portletURL.toString()
		).thenReturn(
			"/view"
		);

		Assert.assertEquals(
			2,
			exeLearningAdminDisplayContext.getViewTypeItems(
				portletURL
			).size());
	}

	@Test
	public void testThumbnailURL() throws Exception {
		Path root = temporaryFolder.newFolder(
		).toPath();

		PublicationStore publicationStore = new PublicationStore(root);

		Path withScreenshot = publicationStore.publish(
			LiferayMocks.COMPANY_ID, 3000L, "1.1",
			TestZips.write(
				root.resolve("a.elpx"),
				TestZips.package_("screenshot.png", "png")),
			TestZips.configuration());

		Path withoutScreenshot = publicationStore.publish(
			LiferayMocks.COMPANY_ID, 3000L, "1.0", _package,
			TestZips.configuration());

		Mockito.when(
			_exeResourceService.getPublicationStore()
		).thenReturn(
			publicationStore
		);

		Mockito.when(
			_exeResourceService.getPublication(
				LiferayMocks.COMPANY_ID, 3000L, "1.1")
		).thenReturn(
			withScreenshot
		);

		Mockito.when(
			_exeResourceService.getPublication(
				LiferayMocks.COMPANY_ID, 3000L, "1.0")
		).thenReturn(
			withoutScreenshot
		);

		Mockito.when(
			_exeResourceService.getContentPath(
				LiferayMocks.COMPANY_ID, 3000L, "1.1", 9)
		).thenReturn(
			"/o/exelearning-content/token/index.html"
		);

		ExeLearningAdminDisplayContext exeLearningAdminDisplayContext = _create(
			"");

		Assert.assertEquals(
			"/o/exelearning-content/token/screenshot.png",
			exeLearningAdminDisplayContext.getThumbnailURL(
				_resource("1.1", "1.0", true), 9));
		Assert.assertNull(
			exeLearningAdminDisplayContext.getThumbnailURL(
				_resource("1.1", "1.0", false), 9));
		Assert.assertNull(
			exeLearningAdminDisplayContext.getThumbnailURL(
				_resource("1.1", "", false), 9));
		Assert.assertNull(
			exeLearningAdminDisplayContext.getThumbnailURL(
				new ExeResource(
					_resource(
						"1.1", "", true
					).getFileEntry(),
					Collections.emptyMap(), true, true, true,
					Collections.emptyList()),
				9));
	}

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private ExeLearningAdminDisplayContext _create(String keywords) {
		return new ExeLearningAdminDisplayContext(
			_exeResourceService, LiferayMocks.GROUP_ID, keywords, null, true);
	}

	private ExeResource _resource(
			String version, String publishedVersion, boolean canUpdate)
		throws Exception {

		FileEntry fileEntry = LiferayMocks.fileEntry(
			3000L, LiferayMocks.expandoBridge(new HashMap<>()),
			LiferayMocks.fileVersion(version, _package));

		Map<String, Serializable> attributes = new HashMap<>();

		attributes.put(ResourceAttributes.PUBLISHABLE, "true");
		attributes.put(ResourceAttributes.PUBLISHED_VERSION, publishedVersion);

		return new ExeResource(
			fileEntry, attributes, canUpdate, canUpdate, canUpdate,
			Collections.emptyList());
	}

	private final ExeResourceService _exeResourceService = Mockito.mock(
		ExeResourceService.class);
	private Path _package;

}