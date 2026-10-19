/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.portlet;

import com.liferay.portal.kernel.repository.model.FileEntry;

import java.io.Serializable;

import java.nio.file.Path;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.portlet.PortletPreferences;

import org.exelearning.liferay.content.ContentServlet;
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
public class ViewerDisplayContextTest {

	@Before
	public void setUp() throws Exception {
		Mockito.when(
			_portletPreferences.getValue(
				ArgumentMatchers.anyString(), ArgumentMatchers.any())
		).thenAnswer(
			invocation -> _preferences.getOrDefault(
				invocation.getArgument(0), invocation.getArgument(1))
		);

		Path file = TestZips.write(
			temporaryFolder.newFile(
			).toPath(),
			TestZips.package_());

		FileEntry fileEntry = LiferayMocks.fileEntry(
			_FILE_ENTRY_ID, LiferayMocks.expandoBridge(new HashMap<>()),
			LiferayMocks.fileVersion("1.2", file));

		Map<String, Serializable> attributes = new HashMap<>();

		attributes.put(ResourceAttributes.PUBLISHED_VERSION, "1.1");

		_editable = new ExeResource(
			fileEntry, attributes, true, true, true, Collections.emptyList());
		_readOnly = new ExeResource(
			fileEntry, attributes, false, false, false,
			Collections.emptyList());

		Mockito.when(
			_exeResourceService.getContentPath(
				LiferayMocks.COMPANY_ID, _FILE_ENTRY_ID, "1.1", 99)
		).thenReturn(
			"/o/exelearning-content/token/index.html"
		);
	}

	@Test
	public void testDefaultsAndUnconfiguredWidgets() throws Exception {
		ViewerDisplayContext viewerDisplayContext = _create(
			LiferayMocks.GROUP_ID);

		Assert.assertFalse(viewerDisplayContext.isConfigured());
		Assert.assertFalse(viewerDisplayContext.isVisible());
		Assert.assertNull(viewerDisplayContext.getContentURL());
		Assert.assertNull(viewerDisplayContext.getPackageURL());
		Assert.assertEquals(
			ViewerDisplayContext.DEFAULT_HEIGHT,
			viewerDisplayContext.getHeight());
		Assert.assertTrue(viewerDisplayContext.isShowTitle());
		Assert.assertTrue(viewerDisplayContext.isAllowFullscreen());
		Assert.assertFalse(viewerDisplayContext.isShowDownload());
		Assert.assertFalse(viewerDisplayContext.isTeacherMode());
		Assert.assertEquals(
			ContentServlet.SANDBOX, viewerDisplayContext.getSandbox());

		Mockito.verifyNoInteractions(_exeResourceService);
	}

	@Test
	public void testHeightIsValidated() throws Exception {
		for (String height : new String[] {"80vh", " 450px "}) {
			_preferences.put(ViewerDisplayContext.PREFERENCE_HEIGHT, height);

			Assert.assertEquals(
				height.trim(),
				_create(
					LiferayMocks.GROUP_ID
				).getHeight());
		}

		for (String height :
				new String[] {"100%", "1px", "600px;background:red", ""}) {

			_preferences.put(ViewerDisplayContext.PREFERENCE_HEIGHT, height);

			Assert.assertEquals(
				ViewerDisplayContext.DEFAULT_HEIGHT,
				_create(
					LiferayMocks.GROUP_ID
				).getHeight());
		}
	}

	@Test
	public void testHidesResourcesOfOtherSitesAndUnavailableOnes()
		throws Exception {

		_preferences.put(
			ViewerDisplayContext.PREFERENCE_FILE_ENTRY_ID,
			String.valueOf(_FILE_ENTRY_ID));

		ViewerDisplayContext viewerDisplayContext = _create(
			LiferayMocks.GROUP_ID);

		Assert.assertTrue(viewerDisplayContext.isConfigured());
		Assert.assertFalse(viewerDisplayContext.isVisible());

		Mockito.when(
			_exeResourceService.getPublishedResource(_FILE_ENTRY_ID)
		).thenReturn(
			_readOnly
		);

		Assert.assertFalse(
			_create(
				999L
			).isVisible());
	}

	@Test
	public void testShowsThePublishedRevision() throws Exception {
		_preferences.put(
			ViewerDisplayContext.PREFERENCE_ALLOW_FULLSCREEN, "false");
		_preferences.put(
			ViewerDisplayContext.PREFERENCE_FILE_ENTRY_ID,
			String.valueOf(_FILE_ENTRY_ID));
		_preferences.put(ViewerDisplayContext.PREFERENCE_SHOW_DOWNLOAD, "true");
		_preferences.put(ViewerDisplayContext.PREFERENCE_SHOW_TITLE, "false");
		_preferences.put(ViewerDisplayContext.PREFERENCE_TEACHER_MODE, "true");

		Mockito.when(
			_exeResourceService.getPublishedResource(_FILE_ENTRY_ID)
		).thenReturn(
			_readOnly
		);

		ViewerDisplayContext viewerDisplayContext = _create(
			LiferayMocks.GROUP_ID);

		Assert.assertTrue(viewerDisplayContext.isVisible());
		Assert.assertSame(_readOnly, viewerDisplayContext.getExeResource());
		Assert.assertEquals(
			_FILE_ENTRY_ID, viewerDisplayContext.getFileEntryId());
		Assert.assertEquals(
			"/o/exelearning-content/token/index.html",
			viewerDisplayContext.getContentURL());
		Assert.assertEquals(
			"/o/exelearning-content/token",
			viewerDisplayContext.getPackageURL());
		Assert.assertTrue(viewerDisplayContext.isShowDownload());
		Assert.assertFalse(viewerDisplayContext.isShowTitle());
		Assert.assertFalse(viewerDisplayContext.isAllowFullscreen());
		Assert.assertTrue(viewerDisplayContext.isTeacherMode());

		Mockito.when(
			_exeResourceService.getPublishedResource(_FILE_ENTRY_ID)
		).thenReturn(
			_editable
		);

		viewerDisplayContext = _create(LiferayMocks.GROUP_ID);

		Assert.assertEquals(
			"/o/exelearning-content/token/index.html?exe-teacher=1",
			viewerDisplayContext.getContentURL());
		Assert.assertEquals(
			"/o/exelearning-content/token",
			viewerDisplayContext.getPackageURL());
	}

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private ViewerDisplayContext _create(long groupId) throws Exception {
		return new ViewerDisplayContext(
			_portletPreferences, _exeResourceService, groupId, 99);
	}

	private static final long _FILE_ENTRY_ID = 3000L;

	private ExeResource _editable;
	private final ExeResourceService _exeResourceService = Mockito.mock(
		ExeResourceService.class);
	private final PortletPreferences _portletPreferences = Mockito.mock(
		PortletPreferences.class);
	private final Map<String, String> _preferences = new HashMap<>();
	private ExeResource _readOnly;

}