/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.resource;

import com.liferay.document.library.kernel.exception.DuplicateFileEntryException;
import com.liferay.document.library.kernel.exception.DuplicateFolderNameException;
import com.liferay.document.library.kernel.exception.NoSuchFileEntryException;
import com.liferay.document.library.kernel.exception.NoSuchFolderException;
import com.liferay.document.library.kernel.model.DLVersionNumberIncrease;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.document.library.kernel.service.DLAppService;
import com.liferay.expando.kernel.model.ExpandoBridge;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.lock.DuplicateLockException;
import com.liferay.portal.kernel.lock.Lock;
import com.liferay.portal.kernel.lock.LockManager;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.GroupConstants;
import com.liferay.portal.kernel.model.role.RoleConstants;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.FileVersion;
import com.liferay.portal.kernel.repository.model.Folder;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.security.permission.resource.PortletResourcePermission;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.permission.ModelPermissions;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.security.Key;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.crypto.spec.SecretKeySpec;

import org.exelearning.liferay.constants.ExeLearningPortletKeys;
import org.exelearning.liferay.content.ContentToken;
import org.exelearning.liferay.elpx.ElpxException;
import org.exelearning.liferay.publication.PublicationStore;
import org.exelearning.liferay.test.LiferayMocks;
import org.exelearning.liferay.test.TestZips;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * @author eXeLearning contributors
 */
public class ExeResourceServiceTest {

	@Before
	public void setUp() throws Exception {
		_root = temporaryFolder.newFolder(
		).toPath();

		TestZips.setField(
			_exeResourceService, "_companyLocalService", _companyLocalService);
		TestZips.setField(
			_exeResourceService, "_dlAppLocalService", _dlAppLocalService);
		TestZips.setField(_exeResourceService, "_dlAppService", _dlAppService);
		TestZips.setField(
			_exeResourceService, "_fileEntryModelResourcePermission",
			_fileEntryModelResourcePermission);
		TestZips.setField(
			_exeResourceService, "_folderModelResourcePermission",
			_folderModelResourcePermission);
		TestZips.setField(
			_exeResourceService, "_groupLocalService", _groupLocalService);
		TestZips.setField(_exeResourceService, "_lockManager", _lockManager);
		TestZips.setField(_exeResourceService, "_portal", _portal);
		TestZips.setField(
			_exeResourceService, "_portletResourcePermission",
			_portletResourcePermission);
		TestZips.setField(
			_exeResourceService, "_publicationStore",
			new PublicationStore(_root.resolve("publications")));

		TestZips.setField(
			_exeResourceService, "_exeLearningConfiguration",
			TestZips.configuration());

		Mockito.when(
			_permissionChecker.getCompanyId()
		).thenReturn(
			LiferayMocks.COMPANY_ID
		);

		Mockito.when(
			_permissionChecker.getUserId()
		).thenReturn(
			_USER_ID
		);

		Mockito.when(
			_permissionChecker.hasPermission(
				ArgumentMatchers.anyLong(),
				ArgumentMatchers.eq(ExeLearningPortletKeys.ADMIN),
				ArgumentMatchers.eq(ExeLearningPortletKeys.ADMIN),
				ArgumentMatchers.eq(ActionKeys.ACCESS_IN_CONTROL_PANEL))
		).thenAnswer(
			invocation -> _canAuthor
		);

		PermissionThreadLocal.setPermissionChecker(_permissionChecker);

		Group group = Mockito.mock(Group.class);

		Mockito.when(
			group.getType()
		).thenReturn(
			GroupConstants.TYPE_SITE_OPEN
		);

		Mockito.when(
			_groupLocalService.getGroup(LiferayMocks.GROUP_ID)
		).thenReturn(
			group
		);

		Mockito.when(
			_portal.getPathModule()
		).thenReturn(
			"/o"
		);

		Mockito.when(
			_fileEntryModelResourcePermission.contains(
				ArgumentMatchers.any(PermissionChecker.class),
				ArgumentMatchers.any(FileEntry.class),
				ArgumentMatchers.anyString())
		).thenAnswer(
			invocation -> _allowedActionIds.contains(invocation.getArgument(2))
		);

		Mockito.doAnswer(
			invocation -> {
				String actionId = invocation.getArgument(2);

				if (!_allowedActionIds.contains(actionId)) {
					throw new PrincipalException.MustHavePermission(
						_permissionChecker, FileEntry.class.getName(), 0L,
						actionId);
				}

				return null;
			}
		).when(
			_fileEntryModelResourcePermission
		).check(
			ArgumentMatchers.any(PermissionChecker.class),
			ArgumentMatchers.any(FileEntry.class), ArgumentMatchers.anyString()
		);

		_allowFileEntry(ActionKeys.UPDATE, ActionKeys.DELETE);
	}

	@After
	public void tearDown() {
		PermissionThreadLocal.setPermissionChecker(null);
	}

	@Test
	public void testAddResourceCreatesFolderWithViewOnlyPermissions()
		throws Exception {

		_withoutFolder();

		Mockito.when(
			_portletResourcePermission.contains(
				_permissionChecker, LiferayMocks.GROUP_ID,
				ActionKeys.ADD_DOCUMENT)
		).thenReturn(
			true
		);

		Folder folder = LiferayMocks.folder();

		ArgumentCaptor<ServiceContext> serviceContextArgumentCaptor =
			ArgumentCaptor.forClass(ServiceContext.class);

		Mockito.when(
			_dlAppLocalService.addFolder(
				ArgumentMatchers.isNull(), ArgumentMatchers.eq(_USER_ID),
				ArgumentMatchers.eq(LiferayMocks.GROUP_ID),
				ArgumentMatchers.eq(0L),
				ArgumentMatchers.eq(ExeResourceService.FOLDER_NAME),
				ArgumentMatchers.anyString(),
				serviceContextArgumentCaptor.capture())
		).thenReturn(
			folder
		);

		_mockAddFileEntry();

		ExeResource exeResource = _exeResourceService.addResource(
			LiferayMocks.GROUP_ID, "demo.elpx", _stream(_package()));

		Assert.assertEquals("1.0", exeResource.getVersion());

		ServiceContext serviceContext = serviceContextArgumentCaptor.getValue();

		Assert.assertFalse(serviceContext.isAddGroupPermissions());
		Assert.assertFalse(serviceContext.isAddGuestPermissions());

		ModelPermissions modelPermissions =
			serviceContext.getModelPermissions();

		Assert.assertArrayEquals(
			new String[] {ActionKeys.VIEW},
			modelPermissions.getActionIds(
				RoleConstants.PLACEHOLDER_DEFAULT_GROUP_ROLE));
		Assert.assertArrayEquals(
			new String[] {ActionKeys.VIEW},
			modelPermissions.getActionIds(RoleConstants.GUEST));

		Assert.assertEquals(
			WorkflowConstants.ACTION_PUBLISH,
			serviceContext.getWorkflowAction());
	}

	@Test
	public void testAddResourceExtractsAndStoresMetadata() throws Exception {
		_withFolder(true);

		ArgumentCaptor<String> titleArgumentCaptor = ArgumentCaptor.forClass(
			String.class);

		FileEntry fileEntry = _mockAddFileEntry(titleArgumentCaptor);

		ExeResource exeResource = _exeResourceService.addResource(
			LiferayMocks.GROUP_ID, "C:\\fakepath\\demo.elpx",
			_stream(_package()));

		Assert.assertSame(fileEntry, exeResource.getFileEntry());

		Assert.assertEquals("Demo & test", titleArgumentCaptor.getValue());
		Assert.assertEquals(
			"Ana", exeResource.getAttribute(ResourceAttributes.AUTHOR));
		Assert.assertEquals(
			"CC BY-SA 4.0",
			exeResource.getAttribute(ResourceAttributes.LICENSE));
		Assert.assertTrue(exeResource.isPublishable());
		Assert.assertFalse(exeResource.isPublished());
		Assert.assertTrue(
			Files.isRegularFile(
				_publication(
					"1.0"
				).resolve(
					"index.html"
				)));
		Assert.assertEquals(0, _stagingCount());
	}

	@Test
	public void testAddResourceRejectsInvalidPackagesWithoutStoringThem()
		throws Exception {

		_withFolder(true);

		Path file = TestZips.write(
			temporaryFolder.newFile(
			).toPath(),
			TestZips.package_("../../escape.html", "x"));

		try {
			_exeResourceService.addResource(
				LiferayMocks.GROUP_ID, "demo.elpx", _stream(file));

			Assert.fail();
		}
		catch (ElpxException elpxException) {
			Assert.assertEquals(
				"the-package-contains-an-unsafe-path-x",
				elpxException.getKey());
		}

		_assertNoFileEntryAdded();

		Assert.assertEquals(0, _stagingCount());
	}

	@Test
	public void testAddResourceRejectsNonelpxNamesAndOversizedUploads()
		throws Exception {

		_withFolder(true);

		try {
			_exeResourceService.addResource(
				LiferayMocks.GROUP_ID, "demo.zip", _stream(_package()));

			Assert.fail();
		}
		catch (ElpxException elpxException) {
			Assert.assertEquals(
				"only-elpx-files-are-supported", elpxException.getKey());
		}

		Map<String, Object> overrides = new HashMap<>();

		overrides.put("maxUploadSize", 10L);

		TestZips.setField(
			_exeResourceService, "_exeLearningConfiguration",
			TestZips.configuration(overrides));

		try {
			_exeResourceService.addResource(
				LiferayMocks.GROUP_ID, "demo.elpx", _stream(_package()));

			Assert.fail();
		}
		catch (ElpxException elpxException) {
			Assert.assertEquals(
				"the-file-exceeds-the-maximum-upload-size-x",
				elpxException.getKey());
		}

		_assertNoFileEntryAdded();
	}

	@Test
	public void testAddResourceRequiresAddPermission() throws Exception {
		_withFolder(false);

		try {
			_exeResourceService.addResource(
				LiferayMocks.GROUP_ID, "demo.elpx", _stream(_package()));

			Assert.fail();
		}
		catch (PrincipalException principalException) {
			_assertNoFileEntryAdded();
		}
	}

	@Test
	public void testAddResourceRetriesDuplicateNames() throws Exception {
		_withFolder(true);

		FileEntry fileEntry = _fileEntry(LiferayMocks.zip(_root, "a.elpx"));

		Mockito.when(
			_dlAppService.addFileEntry(
				ArgumentMatchers.isNull(),
				ArgumentMatchers.eq(LiferayMocks.GROUP_ID),
				ArgumentMatchers.eq(LiferayMocks.FOLDER_ID),
				ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
				ArgumentMatchers.anyString(), ArgumentMatchers.isNull(),
				ArgumentMatchers.any(), ArgumentMatchers.anyString(),
				ArgumentMatchers.any(File.class), ArgumentMatchers.isNull(),
				ArgumentMatchers.isNull(), ArgumentMatchers.isNull(),
				ArgumentMatchers.any(ServiceContext.class))
		).thenThrow(
			new DuplicateFileEntryException()
		).thenReturn(
			fileEntry
		);

		_exeResourceService.addResource(
			LiferayMocks.GROUP_ID, "demo.elpx", _stream(_package()));

		Mockito.verify(
			_dlAppService
		).addFileEntry(
			ArgumentMatchers.isNull(),
			ArgumentMatchers.eq(LiferayMocks.GROUP_ID),
			ArgumentMatchers.eq(LiferayMocks.FOLDER_ID),
			ArgumentMatchers.eq("demo (2).elpx"), ArgumentMatchers.anyString(),
			ArgumentMatchers.eq("Demo & test (2)"), ArgumentMatchers.isNull(),
			ArgumentMatchers.any(), ArgumentMatchers.anyString(),
			ArgumentMatchers.any(File.class), ArgumentMatchers.isNull(),
			ArgumentMatchers.isNull(), ArgumentMatchers.isNull(),
			ArgumentMatchers.any(ServiceContext.class)
		);
	}

	@Test
	public void testAddResourceWithoutHtmlIsStoredButNotPublishable()
		throws Exception {

		_withFolder(true);

		_mockAddFileEntry();

		ExeResource exeResource = _exeResourceService.addResource(
			LiferayMocks.GROUP_ID, "source-only.elpx",
			_stream(TestZips.fixture("source-only.elpx")));

		Assert.assertFalse(exeResource.isPublishable());

		Assert.assertFalse(Files.exists(_publication("1.0")));
	}

	@Test
	public void testCanAddResource() throws Exception {
		_withFolder(true);

		Assert.assertTrue(
			_exeResourceService.canAddResource(LiferayMocks.GROUP_ID));

		_canAuthor = false;

		Assert.assertFalse(
			"Documents and Media rights alone do not allow authoring",
			_exeResourceService.canAddResource(LiferayMocks.GROUP_ID));

		_canAuthor = true;

		_withoutFolder();

		Assert.assertFalse(
			_exeResourceService.canAddResource(LiferayMocks.GROUP_ID));
	}

	@Test
	public void testConcurrentFolderCreationReusesTheFolder() throws Exception {
		Folder folder = LiferayMocks.folder();

		Mockito.when(
			_dlAppLocalService.getFolder(
				LiferayMocks.GROUP_ID, 0L, ExeResourceService.FOLDER_NAME)
		).thenThrow(
			new NoSuchFolderException()
		).thenThrow(
			new NoSuchFolderException()
		).thenReturn(
			folder
		);

		Mockito.when(
			_portletResourcePermission.contains(
				_permissionChecker, LiferayMocks.GROUP_ID,
				ActionKeys.ADD_DOCUMENT)
		).thenReturn(
			true
		);

		Mockito.when(
			_dlAppLocalService.addFolder(
				ArgumentMatchers.isNull(), ArgumentMatchers.anyLong(),
				ArgumentMatchers.anyLong(), ArgumentMatchers.anyLong(),
				ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
				ArgumentMatchers.any(ServiceContext.class))
		).thenThrow(
			new DuplicateFolderNameException()
		);

		_mockAddFileEntry();

		_exeResourceService.addResource(
			LiferayMocks.GROUP_ID, "demo.elpx", _stream(_package()));

		Mockito.verify(
			_dlAppService
		).addFileEntry(
			ArgumentMatchers.isNull(), ArgumentMatchers.anyLong(),
			ArgumentMatchers.eq(LiferayMocks.FOLDER_ID),
			ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
			ArgumentMatchers.anyString(), ArgumentMatchers.isNull(),
			ArgumentMatchers.any(), ArgumentMatchers.anyString(),
			ArgumentMatchers.any(File.class), ArgumentMatchers.isNull(),
			ArgumentMatchers.isNull(), ArgumentMatchers.isNull(),
			ArgumentMatchers.any(ServiceContext.class)
		);
	}

	@Test
	public void testDeleteResourceRemovesThePublication() throws Exception {
		FileEntry fileEntry = _storedFileEntry("1.0");

		_exeResourceService.publish(_FILE_ENTRY_ID, "1.0");

		Assert.assertTrue(Files.exists(_publication("1.0")));

		_exeResourceService.deleteResource(_FILE_ENTRY_ID);

		Mockito.verify(
			_dlAppService
		).deleteFileEntry(
			_FILE_ENTRY_ID
		);

		Assert.assertFalse(Files.exists(_publication("1.0")));
		Assert.assertNotNull(fileEntry);
	}

	@Test
	public void testDeleteResourceRequiresPermission() throws Exception {
		_storedFileEntry("1.0");

		_allowFileEntry();

		try {
			_exeResourceService.deleteResource(_FILE_ENTRY_ID);

			Assert.fail();
		}
		catch (PrincipalException principalException) {
			Mockito.verify(
				_dlAppService, Mockito.never()
			).deleteFileEntry(
				_FILE_ENTRY_ID
			);
		}
	}

	@Test
	public void testGetContentPathIsSignedAndCacheFriendly() throws Exception {
		_mockCompanyKey();

		String path = _exeResourceService.getContentPath(
			LiferayMocks.COMPANY_ID, _FILE_ENTRY_ID, "1.0", 1000);

		Assert.assertTrue(path.startsWith("/o/exelearning-content/"));
		Assert.assertTrue(path.endsWith("/index.html"));

		String token = path.split("/")[3];

		ContentToken contentToken = ContentToken.verify(
			_exeResourceService.getTokenKey(LiferayMocks.COMPANY_ID),
			LiferayMocks.COMPANY_ID, token, 1000);

		Assert.assertEquals(_FILE_ENTRY_ID, contentToken.getFileEntryId());
		Assert.assertEquals("1.0", contentToken.getVersion());
		Assert.assertEquals(0, contentToken.getExpiresEpochSeconds() % 3600);
		Assert.assertTrue(
			contentToken.getExpiresEpochSeconds() >= (1000 + (480 * 60)));

		Assert.assertEquals(
			path,
			_exeResourceService.getContentPath(
				LiferayMocks.COMPANY_ID, _FILE_ENTRY_ID, "1.0", 1060));
	}

	@Test
	public void testGetPackage() throws Exception {
		FileEntry fileEntry = _storedFileEntry("1.0", "1.1");

		_exeResourceService.publish(_FILE_ENTRY_ID, "1.0");

		_allowFileEntry();

		Assert.assertEquals(
			"1.0",
			_exeResourceService.getPackage(
				_FILE_ENTRY_ID, null
			).getVersion());

		try {
			_exeResourceService.getPackage(_FILE_ENTRY_ID, "1.1");

			Assert.fail();
		}
		catch (PrincipalException principalException) {
			Assert.assertNotNull(principalException);
		}

		_allowFileEntry(ActionKeys.UPDATE);

		Assert.assertEquals(
			"1.1",
			_exeResourceService.getPackage(
				_FILE_ENTRY_ID, "1.1"
			).getVersion());

		_exeResourceService.unpublish(_FILE_ENTRY_ID);

		Assert.assertEquals(
			"1.1",
			_exeResourceService.getPackage(
				_FILE_ENTRY_ID, ""
			).getVersion());
		Assert.assertNotNull(fileEntry);
	}

	@Test
	public void testGetPublicationRebuildsTheCacheFromDocumentsAndMedia()
		throws Exception {

		FileEntry fileEntry = _storedFileEntry("1.0");

		Mockito.when(
			_dlAppLocalService.getFileEntry(_FILE_ENTRY_ID)
		).thenReturn(
			fileEntry
		);

		Path publication = _exeResourceService.getPublication(
			LiferayMocks.COMPANY_ID, _FILE_ENTRY_ID, "1.0");

		Assert.assertTrue(
			Files.isRegularFile(publication.resolve("index.html")));
		Assert.assertEquals(
			publication,
			_exeResourceService.getPublication(
				LiferayMocks.COMPANY_ID, _FILE_ENTRY_ID, "1.0"));

		try {
			_exeResourceService.getPublication(99L, _FILE_ENTRY_ID, "1.0");

			Assert.fail();
		}
		catch (NoSuchFileEntryException noSuchFileEntryException) {
			Assert.assertNotNull(noSuchFileEntryException);
		}

		Assert.assertSame(
			_exeResourceService.getConfiguration(),
			_exeResourceService.getConfiguration());
		Assert.assertEquals(
			_root.resolve("publications"),
			_exeResourceService.getPublicationStore(
			).getRoot());
	}

	@Test
	public void testGetPublicationRejectsRevisionsWithoutHtml()
		throws Exception {

		FileVersion fileVersion = LiferayMocks.fileVersion(
			"1.0", TestZips.fixture("source-only.elpx"));

		FileEntry fileEntry = LiferayMocks.fileEntry(
			_FILE_ENTRY_ID, LiferayMocks.expandoBridge(new HashMap<>()),
			fileVersion);

		Mockito.when(
			_dlAppLocalService.getFileEntry(_FILE_ENTRY_ID)
		).thenReturn(
			fileEntry
		);

		try {
			_exeResourceService.getPublication(
				LiferayMocks.COMPANY_ID, _FILE_ENTRY_ID, "1.0");

			Assert.fail();
		}
		catch (ElpxException elpxException) {
			Assert.assertEquals(
				"the-package-has-no-html-export", elpxException.getKey());
		}
	}

	@Test
	public void testGetResourceHidesWhatTheUserCannotSee() throws Exception {
		_storedFileEntry("1.0");

		_allowFileEntry();

		try {
			_exeResourceService.getResource(_FILE_ENTRY_ID);

			Assert.fail("Unpublished resources are invisible to viewers");
		}
		catch (NoSuchFileEntryException noSuchFileEntryException) {
			Assert.assertNull(
				_exeResourceService.getPublishedResource(_FILE_ENTRY_ID));
		}

		Mockito.when(
			_dlAppService.getFileEntry(77L)
		).thenThrow(
			new PrincipalException()
		);

		try {
			_exeResourceService.getResource(77L);

			Assert.fail();
		}
		catch (NoSuchFileEntryException noSuchFileEntryException) {
			Assert.assertTrue(
				noSuchFileEntryException.getCause() instanceof
					PrincipalException);
		}
	}

	@Test
	public void testGetResourceOnlyAcceptsFilesOfTheExeLearningFolder()
		throws Exception {

		FileEntry fileEntry = _storedFileEntry("1.0");

		Mockito.when(
			fileEntry.getFolderId()
		).thenReturn(
			1L
		);

		try {
			_exeResourceService.getResource(_FILE_ENTRY_ID);

			Assert.fail();
		}
		catch (NoSuchFileEntryException noSuchFileEntryException) {
			Assert.assertNotNull(noSuchFileEntryException);
		}

		_withoutFolder();

		try {
			_exeResourceService.getResource(_FILE_ENTRY_ID);

			Assert.fail();
		}
		catch (NoSuchFileEntryException noSuchFileEntryException) {
			Assert.assertNotNull(noSuchFileEntryException);
		}
	}

	@Test
	public void testGetResources() throws Exception {
		_withoutFolder();

		Assert.assertTrue(
			_exeResourceService.getResources(
				LiferayMocks.GROUP_ID, null
			).isEmpty());

		_withFolder(true);

		FileEntry zebra = _fileEntry(LiferayMocks.zip(_root, "z.elpx"));
		FileEntry alpha = _fileEntry(LiferayMocks.zip(_root, "a.elpx"));
		FileEntry draft = _fileEntry(LiferayMocks.zip(_root, "d.elpx"));

		Mockito.when(
			zebra.getTitle()
		).thenReturn(
			"Zebra"
		);

		Mockito.when(
			alpha.getTitle()
		).thenReturn(
			"alpha"
		);

		Mockito.when(
			draft.getTitle()
		).thenReturn(
			"Draft"
		);

		zebra.getExpandoBridge(
		).setAttribute(
			ResourceAttributes.PUBLISHED_VERSION, "1.0", false
		);

		alpha.getExpandoBridge(
		).setAttribute(
			ResourceAttributes.PUBLISHED_VERSION, "1.0", false
		);

		alpha.getExpandoBridge(
		).setAttribute(
			ResourceAttributes.AUTHOR, "Marta López", false
		);

		Mockito.when(
			_dlAppService.getFileEntries(
				LiferayMocks.GROUP_ID, LiferayMocks.FOLDER_ID)
		).thenReturn(
			Arrays.asList(zebra, draft, alpha)
		);

		_allowFileEntry();

		Assert.assertEquals(
			Arrays.asList("alpha", "Zebra"),
			_titles(
				_exeResourceService.getResources(LiferayMocks.GROUP_ID, "  ")));
		Assert.assertEquals(
			Collections.singletonList("alpha"),
			_titles(
				_exeResourceService.getResources(
					LiferayMocks.GROUP_ID, "LÓPEZ")));

		_allowFileEntry(ActionKeys.UPDATE);

		Assert.assertEquals(
			Arrays.asList("alpha", "Draft", "Zebra"),
			_titles(
				_exeResourceService.getResources(LiferayMocks.GROUP_ID, null)));
	}

	@Test
	public void testGetTokenKey() throws Exception {
		Company company = Mockito.mock(Company.class);

		Mockito.when(
			company.getKey()
		).thenReturn(
			"fallback-key"
		);

		Mockito.when(
			_companyLocalService.getCompany(LiferayMocks.COMPANY_ID)
		).thenReturn(
			company
		);

		byte[] key = _exeResourceService.getTokenKey(LiferayMocks.COMPANY_ID);

		Assert.assertArrayEquals(
			"fallback-key".getBytes(StandardCharsets.UTF_8), key);

		key[0] = 0;

		Assert.assertArrayEquals(
			"fallback-key".getBytes(StandardCharsets.UTF_8),
			_exeResourceService.getTokenKey(LiferayMocks.COMPANY_ID));

		Mockito.verify(
			_companyLocalService, Mockito.times(1)
		).getCompany(
			LiferayMocks.COMPANY_ID
		);
	}

	@Test
	public void testPreviewRequiresUpdatePermission() throws Exception {
		_storedFileEntry("1.0", "1.1");

		_mockCompanyKey();

		String path = _exeResourceService.preview(_FILE_ENTRY_ID, null, 0);

		Assert.assertTrue(path.contains("-1.1-"));

		Assert.assertTrue(Files.exists(_publication("1.1")));

		_allowFileEntry();

		try {
			_exeResourceService.preview(_FILE_ENTRY_ID, "1.0", 0);

			Assert.fail();
		}
		catch (PrincipalException principalException) {
			Assert.assertNotNull(principalException);
		}
	}

	@Test
	public void testPublishAndUnpublish() throws Exception {
		_storedFileEntry("1.0", "1.1", "1.2");

		ExeResource exeResource = _exeResourceService.publish(
			_FILE_ENTRY_ID, "1.0");

		Assert.assertEquals("1.0", exeResource.getPublishedVersion());
		Assert.assertTrue(exeResource.isPublished());

		exeResource = _exeResourceService.publish(_FILE_ENTRY_ID, "");

		Assert.assertEquals("1.2", exeResource.getPublishedVersion());

		Assert.assertFalse(
			"Obsolete cached revisions are pruned",
			Files.exists(_publication("1.0")));
		Assert.assertTrue(Files.exists(_publication("1.2")));

		_assertVersions(exeResource, "1.0", "1.1", "1.2");

		exeResource = _exeResourceService.unpublish(_FILE_ENTRY_ID);

		Assert.assertFalse(exeResource.isPublished());
	}

	@Test
	public void testPublishOnlyApprovedRevisionsWithHtml() throws Exception {
		FileVersion pending = LiferayMocks.fileVersion(
			"1.1", _package(), WorkflowConstants.STATUS_PENDING);

		FileEntry fileEntry = LiferayMocks.fileEntry(
			_FILE_ENTRY_ID, LiferayMocks.expandoBridge(new HashMap<>()),
			LiferayMocks.fileVersion(
				"1.0", TestZips.fixture("source-only.elpx")),
			pending);

		_register(fileEntry);

		try {
			_exeResourceService.publish(_FILE_ENTRY_ID, "1.1");

			Assert.fail();
		}
		catch (ElpxException elpxException) {
			Assert.assertEquals(
				"only-approved-versions-can-be-published",
				elpxException.getKey());
		}

		try {
			_exeResourceService.publish(_FILE_ENTRY_ID, "1.0");

			Assert.fail();
		}
		catch (ElpxException elpxException) {
			Assert.assertEquals(
				"the-package-has-no-html-export", elpxException.getKey());
		}

		ExpandoBridge expandoBridge = fileEntry.getExpandoBridge();

		Assert.assertNull(
			expandoBridge.getAttribute(
				ResourceAttributes.PUBLISHED_VERSION, false));
	}

	@Test
	public void testPublishRequiresUpdatePermission() throws Exception {
		_storedFileEntry("1.0");

		_allowFileEntry();

		try {
			_exeResourceService.publish(_FILE_ENTRY_ID, "1.0");

			Assert.fail();
		}
		catch (PrincipalException principalException) {
			Assert.assertFalse(Files.exists(_publication("1.0")));
		}
	}

	@Test
	public void testSanitizeFileName() throws Exception {
		Assert.assertEquals(
			"course.elpx",
			ExeResourceService.sanitizeFileName("/tmp/../course.elpx"));
		Assert.assertEquals(
			"a b.ELPX", ExeResourceService.sanitizeFileName(" a b.ELPX "));
		Assert.assertEquals(
			"x.elpx", ExeResourceService.sanitizeFileName("x\u0000:*.elpx"));

		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < 300; i++) {
			sb.append('n');
		}

		String longName = ExeResourceService.sanitizeFileName(
			sb.toString() + ".elpx");

		Assert.assertEquals(200, longName.length());
		Assert.assertTrue(longName.endsWith(".elpx"));

		for (String invalid :
				new String[] {null, "", ".elpx", "course.zip", "a.elpx/"}) {

			try {
				ExeResourceService.sanitizeFileName(invalid);

				Assert.fail(invalid);
			}
			catch (ElpxException elpxException) {
				Assert.assertEquals(
					"only-elpx-files-are-supported", elpxException.getKey());
			}
		}
	}

	@Test
	public void testStorageFailureLeavesNoStagedFiles() throws Exception {
		_withFolder(true);

		Mockito.when(
			_dlAppService.addFileEntry(
				ArgumentMatchers.isNull(), ArgumentMatchers.anyLong(),
				ArgumentMatchers.anyLong(), ArgumentMatchers.anyString(),
				ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
				ArgumentMatchers.isNull(), ArgumentMatchers.any(),
				ArgumentMatchers.anyString(), ArgumentMatchers.any(File.class),
				ArgumentMatchers.isNull(), ArgumentMatchers.isNull(),
				ArgumentMatchers.isNull(),
				ArgumentMatchers.any(ServiceContext.class))
		).thenThrow(
			new PortalException("Database unavailable")
		);

		try {
			_exeResourceService.addResource(
				LiferayMocks.GROUP_ID, "demo.elpx", _stream(_package()));

			Assert.fail();
		}
		catch (PortalException portalException) {
			Assert.assertEquals(
				"Database unavailable", portalException.getMessage());
		}

		Assert.assertEquals(0, _stagingCount());
	}

	@Test
	public void testUpdatePackageCreatesRevisionKeepingThePublishedOne()
		throws Exception {

		FileEntry fileEntry = _storedFileEntry("1.0");

		_exeResourceService.publish(_FILE_ENTRY_ID, "1.0");

		FileEntry updatedFileEntry = LiferayMocks.fileEntry(
			_FILE_ENTRY_ID, fileEntry.getExpandoBridge(),
			LiferayMocks.fileVersion("1.0", _package()),
			LiferayMocks.fileVersion("1.1", _package()));

		Mockito.when(
			_dlAppService.updateFileEntry(
				ArgumentMatchers.eq(_FILE_ENTRY_ID),
				ArgumentMatchers.eq("demo.elpx"),
				ArgumentMatchers.eq(ExeResourceService.MIME_TYPE),
				ArgumentMatchers.eq("Demo"), ArgumentMatchers.isNull(),
				ArgumentMatchers.eq("Description"),
				ArgumentMatchers.anyString(),
				ArgumentMatchers.eq(DLVersionNumberIncrease.MINOR),
				ArgumentMatchers.any(File.class), ArgumentMatchers.any(),
				ArgumentMatchers.any(), ArgumentMatchers.any(),
				ArgumentMatchers.any(ServiceContext.class))
		).thenReturn(
			updatedFileEntry
		);

		ExeResource exeResource = _exeResourceService.updatePackage(
			_FILE_ENTRY_ID, "1.0", "renamed.elpx", _stream(_package()));

		Assert.assertEquals("1.1", exeResource.getVersion());
		Assert.assertEquals("1.0", exeResource.getPublishedVersion());

		Assert.assertTrue(Files.exists(_publication("1.0")));
		Assert.assertTrue(Files.exists(_publication("1.1")));

		_assertLockReleased();
	}

	@Test
	public void testUpdatePackageRejectsConcurrentSaves() throws Exception {
		_storedFileEntry("1.0");

		DuplicateLockException duplicateLockException =
			new DuplicateLockException(Mockito.mock(Lock.class));

		Mockito.when(
			_lockManager.lock(
				ArgumentMatchers.anyLong(), ArgumentMatchers.anyString(),
				ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
				ArgumentMatchers.anyBoolean(), ArgumentMatchers.anyLong())
		).thenThrow(
			duplicateLockException
		);

		try {
			_exeResourceService.updatePackage(
				_FILE_ENTRY_ID, "1.0", "demo.elpx", _stream(_package()));

			Assert.fail();
		}
		catch (ResourceConflictException resourceConflictException) {
			Assert.assertEquals(
				"another-save-of-this-resource-is-in-progress",
				resourceConflictException.getKey());
			Assert.assertEquals(
				"1.0", resourceConflictException.getCurrentVersion());
		}

		_assertNoUpdate();
	}

	@Test
	public void testUpdatePackageRejectsInvalidPackages() throws Exception {
		_storedFileEntry("1.0");

		Path file = TestZips.write(
			temporaryFolder.newFile(
			).toPath(),
			TestZips.package_("x.php", "<?php"));

		try {
			_exeResourceService.updatePackage(
				_FILE_ENTRY_ID, "1.0", "demo.elpx", _stream(file));

			Assert.fail();
		}
		catch (ElpxException elpxException) {
			Assert.assertEquals(
				"the-package-contains-a-forbidden-file-x",
				elpxException.getKey());
		}

		_assertNoUpdate();
		_assertLockReleased();
	}

	@Test
	public void testUpdatePackageRejectsStaleEditorSessions() throws Exception {
		_storedFileEntry("1.0", "1.1");

		try {
			_exeResourceService.updatePackage(
				_FILE_ENTRY_ID, "1.0", "demo.elpx", _stream(_package()));

			Assert.fail();
		}
		catch (ResourceConflictException resourceConflictException) {
			Assert.assertEquals(
				"the-resource-was-changed-by-someone-else",
				resourceConflictException.getKey());
			Assert.assertEquals(
				"1.1", resourceConflictException.getCurrentVersion());
		}

		_assertNoUpdate();
		_assertLockReleased();
	}

	@Test
	public void testUpdatePackageRequiresUpdatePermission() throws Exception {
		_storedFileEntry("1.0");

		_allowFileEntry();

		try {
			_exeResourceService.updatePackage(
				_FILE_ENTRY_ID, "1.0", "demo.elpx", _stream(_package()));

			Assert.fail();
		}
		catch (PrincipalException principalException) {
			_assertNoUpdate();
		}
	}

	@Test
	public void testWithoutPermissionChecker() throws Exception {
		PermissionThreadLocal.setPermissionChecker(null);

		try {
			_exeResourceService.canAddResource(LiferayMocks.GROUP_ID);

			Assert.fail();
		}
		catch (PrincipalException principalException) {
			Assert.assertEquals(
				"No permission checker", principalException.getMessage());
		}
	}

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private void _allowFileEntry(String... actionIds) {
		_allowedActionIds.clear();

		_allowedActionIds.add(ActionKeys.VIEW);

		Collections.addAll(_allowedActionIds, actionIds);
	}

	private void _assertLockReleased() {
		Mockito.verify(
			_lockManager
		).unlock(
			ArgumentMatchers.eq(ExeResourceService.class.getName()),
			ArgumentMatchers.eq(String.valueOf(_FILE_ENTRY_ID)),
			ArgumentMatchers.anyString()
		);
	}

	private void _assertNoFileEntryAdded() throws Exception {
		Mockito.verify(
			_dlAppService, Mockito.never()
		).addFileEntry(
			ArgumentMatchers.any(), ArgumentMatchers.anyLong(),
			ArgumentMatchers.anyLong(), ArgumentMatchers.anyString(),
			ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
			ArgumentMatchers.any(), ArgumentMatchers.any(),
			ArgumentMatchers.anyString(), ArgumentMatchers.any(File.class),
			ArgumentMatchers.any(), ArgumentMatchers.any(),
			ArgumentMatchers.any(), ArgumentMatchers.any(ServiceContext.class)
		);
	}

	private void _assertNoUpdate() throws Exception {
		Mockito.verify(
			_dlAppService, Mockito.never()
		).updateFileEntry(
			ArgumentMatchers.anyLong(), ArgumentMatchers.anyString(),
			ArgumentMatchers.anyString(), ArgumentMatchers.anyString(),
			ArgumentMatchers.any(), ArgumentMatchers.anyString(),
			ArgumentMatchers.anyString(),
			ArgumentMatchers.any(DLVersionNumberIncrease.class),
			ArgumentMatchers.any(File.class), ArgumentMatchers.any(),
			ArgumentMatchers.any(), ArgumentMatchers.any(),
			ArgumentMatchers.any(ServiceContext.class)
		);
	}

	private void _assertVersions(ExeResource exeResource, String... versions) {
		FileEntry fileEntry = exeResource.getFileEntry();

		List<FileVersion> fileVersions = fileEntry.getFileVersions(
			WorkflowConstants.STATUS_ANY);

		Assert.assertEquals(
			fileVersions.toString(), versions.length, fileVersions.size());

		for (int i = 0; i < versions.length; i++) {
			FileVersion fileVersion = fileVersions.get(i);

			Assert.assertEquals(versions[i], fileVersion.getVersion());
		}
	}

	private FileEntry _fileEntry(Path content) throws Exception {
		return LiferayMocks.fileEntry(
			_FILE_ENTRY_ID, LiferayMocks.expandoBridge(new HashMap<>()),
			LiferayMocks.fileVersion("1.0", content));
	}

	private FileEntry _mockAddFileEntry() throws Exception {
		return _mockAddFileEntry(ArgumentCaptor.forClass(String.class));
	}

	private FileEntry _mockAddFileEntry(
			ArgumentCaptor<String> titleArgumentCaptor)
		throws Exception {

		FileEntry fileEntry = _fileEntry(_package());

		Mockito.when(
			_dlAppService.addFileEntry(
				ArgumentMatchers.isNull(),
				ArgumentMatchers.eq(LiferayMocks.GROUP_ID),
				ArgumentMatchers.eq(LiferayMocks.FOLDER_ID),
				ArgumentMatchers.anyString(),
				ArgumentMatchers.eq(ExeResourceService.MIME_TYPE),
				titleArgumentCaptor.capture(), ArgumentMatchers.isNull(),
				ArgumentMatchers.any(), ArgumentMatchers.anyString(),
				ArgumentMatchers.any(File.class), ArgumentMatchers.isNull(),
				ArgumentMatchers.isNull(), ArgumentMatchers.isNull(),
				ArgumentMatchers.any(ServiceContext.class))
		).thenReturn(
			fileEntry
		);

		return fileEntry;
	}

	private void _mockCompanyKey() throws Exception {
		Company company = Mockito.mock(Company.class);

		Key key = new SecretKeySpec(
			"0123456789abcdef".getBytes(StandardCharsets.UTF_8), "AES");

		Mockito.when(
			company.getKeyObj()
		).thenReturn(
			key
		);

		Mockito.when(
			_companyLocalService.getCompany(LiferayMocks.COMPANY_ID)
		).thenReturn(
			company
		);
	}

	private Path _package() throws Exception {
		Path path = _root.resolve("package.elpx");

		if (!Files.exists(path)) {
			TestZips.write(path, TestZips.package_());
		}

		return path;
	}

	private Path _publication(String version) {
		return _root.resolve(
			"publications"
		).resolve(
			String.valueOf(LiferayMocks.COMPANY_ID)
		).resolve(
			String.valueOf(_FILE_ENTRY_ID)
		).resolve(
			version
		);
	}

	private void _register(FileEntry fileEntry) throws Exception {
		_withFolder(true);

		Mockito.when(
			_dlAppService.getFileEntry(_FILE_ENTRY_ID)
		).thenReturn(
			fileEntry
		);
	}

	private int _stagingCount() throws Exception {
		Path staging = _root.resolve(
			"publications"
		).resolve(
			".staging"
		);

		if (!Files.exists(staging)) {
			return 0;
		}

		try (Stream<Path> stream = Files.list(staging)) {
			return (int)stream.count();
		}
	}

	private FileEntry _storedFileEntry(String... versions) throws Exception {
		FileVersion[] fileVersions = new FileVersion[versions.length];

		for (int i = 0; i < versions.length; i++) {
			fileVersions[i] = LiferayMocks.fileVersion(versions[i], _package());
		}

		FileEntry fileEntry = LiferayMocks.fileEntry(
			_FILE_ENTRY_ID, LiferayMocks.expandoBridge(new HashMap<>()),
			fileVersions);

		_register(fileEntry);

		return fileEntry;
	}

	private InputStream _stream(Path path) throws Exception {
		return new ByteArrayInputStream(Files.readAllBytes(path));
	}

	private List<String> _titles(List<ExeResource> exeResources) {
		String[] titles = new String[exeResources.size()];

		for (int i = 0; i < titles.length; i++) {
			titles[i] = exeResources.get(
				i
			).getTitle();
		}

		return Arrays.asList(titles);
	}

	private void _withFolder(boolean canAdd) throws Exception {
		Folder folder = LiferayMocks.folder();

		Mockito.doReturn(
			folder
		).when(
			_dlAppLocalService
		).getFolder(
			LiferayMocks.GROUP_ID, 0L, ExeResourceService.FOLDER_NAME
		);

		Mockito.when(
			_folderModelResourcePermission.contains(
				_permissionChecker, folder, ActionKeys.ADD_DOCUMENT)
		).thenReturn(
			canAdd
		);
	}

	private void _withoutFolder() throws Exception {
		Mockito.doThrow(
			new NoSuchFolderException()
		).when(
			_dlAppLocalService
		).getFolder(
			LiferayMocks.GROUP_ID, 0L, ExeResourceService.FOLDER_NAME
		);
	}

	private static final long _FILE_ENTRY_ID = 3000L;

	private static final long _USER_ID = 42L;

	private final Set<String> _allowedActionIds = new HashSet<>();
	private volatile boolean _canAuthor = true;
	private final CompanyLocalService _companyLocalService = Mockito.mock(
		CompanyLocalService.class);
	private final DLAppLocalService _dlAppLocalService = Mockito.mock(
		DLAppLocalService.class);
	private final DLAppService _dlAppService = Mockito.mock(DLAppService.class);
	private final ExeResourceService _exeResourceService =
		new ExeResourceService();

	@SuppressWarnings("unchecked")
	private final ModelResourcePermission<FileEntry>
		_fileEntryModelResourcePermission = Mockito.mock(
			ModelResourcePermission.class);

	@SuppressWarnings("unchecked")
	private final ModelResourcePermission<Folder>
		_folderModelResourcePermission = Mockito.mock(
			ModelResourcePermission.class);

	private final GroupLocalService _groupLocalService = Mockito.mock(
		GroupLocalService.class);
	private final LockManager _lockManager = Mockito.mock(LockManager.class);
	private final PermissionChecker _permissionChecker = Mockito.mock(
		PermissionChecker.class);
	private final Portal _portal = Mockito.mock(Portal.class);
	private final PortletResourcePermission _portletResourcePermission =
		Mockito.mock(PortletResourcePermission.class);
	private Path _root;

}