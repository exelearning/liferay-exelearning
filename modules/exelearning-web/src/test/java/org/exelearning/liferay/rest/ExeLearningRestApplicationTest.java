/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.rest;

import com.liferay.document.library.kernel.exception.NoSuchFileEntryException;
import com.liferay.portal.json.JSONObjectImpl;
import com.liferay.portal.kernel.exception.NoSuchGroupException;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.FileVersion;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.GroupLocalService;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import java.nio.file.Path;

import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.exelearning.liferay.resource.ExeResource;
import org.exelearning.liferay.resource.ExeResourceService;
import org.exelearning.liferay.test.LiferayMocks;
import org.exelearning.liferay.test.TestJSON;
import org.exelearning.liferay.test.TestZips;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.mockito.Mockito;

/**
 * @author eXeLearning contributors
 */
public class ExeLearningRestApplicationTest {

	@Before
	public void setUp() throws Exception {
		TestZips.setField(
			_application, "_exeResourceService", _exeResourceService);
		TestZips.setField(
			_application, "_groupLocalService", _groupLocalService);
		TestZips.setField(_application, "_jsonFactory", TestJSON.factory());
		TestZips.setField(
			_application, "_language", Mockito.mock(Language.class));

		PermissionChecker permissionChecker = Mockito.mock(
			PermissionChecker.class);

		Mockito.when(
			permissionChecker.getCompanyId()
		).thenReturn(
			LiferayMocks.COMPANY_ID
		);

		PermissionThreadLocal.setPermissionChecker(permissionChecker);

		Group group = Mockito.mock(Group.class);

		Mockito.when(
			group.getCompanyId()
		).thenReturn(
			LiferayMocks.COMPANY_ID
		);

		Mockito.when(
			_groupLocalService.fetchGroup(LiferayMocks.GROUP_ID)
		).thenReturn(
			group
		);

		Group foreign = Mockito.mock(Group.class);

		Mockito.when(
			foreign.getCompanyId()
		).thenReturn(
			99L
		);

		Mockito.when(
			_groupLocalService.fetchGroup(_FOREIGN_GROUP_ID)
		).thenReturn(
			foreign
		);

		Path file = TestZips.write(
			temporaryFolder.newFile(
			).toPath(),
			TestZips.package_());

		_fileVersion = LiferayMocks.fileVersion("1.0", file);

		FileEntry fileEntry = LiferayMocks.fileEntry(
			_ID, LiferayMocks.expandoBridge(new HashMap<>()), _fileVersion);

		_exeResource = new ExeResource(
			fileEntry, Collections.emptyMap(), true, false, false,
			Collections.emptyList());
	}

	@After
	public void tearDown() {
		PermissionThreadLocal.setPermissionChecker(null);
	}

	@Test
	public void testDeleteResource() throws Exception {
		Assert.assertEquals(
			204,
			_application.deleteResource(
				_ID
			).getStatus());

		Mockito.verify(
			_exeResourceService
		).deleteResource(
			_ID
		);
	}

	@Test
	public void testGetPackageStreamsTheRequestedRevision() throws Exception {
		Mockito.when(
			_exeResourceService.getPackage(_ID, "1.0")
		).thenReturn(
			_fileVersion
		);

		Response response = _application.getPackage(_ID, "1.0");

		Assert.assertEquals(200, response.getStatus());
		Assert.assertEquals(
			"attachment; filename=\"demo.elpx\"; filename*=UTF-8''demo.elpx",
			response.getHeaderString("Content-Disposition"));
		Assert.assertEquals(
			"private, no-store", response.getHeaderString("Cache-Control"));

		StreamingOutput streamingOutput = (StreamingOutput)response.getEntity();

		ByteArrayOutputStream byteArrayOutputStream =
			new ByteArrayOutputStream();

		streamingOutput.write(byteArrayOutputStream);

		try (InputStream inputStream = _fileVersion.getContentStream(false)) {
			Assert.assertArrayEquals(
				inputStream.readAllBytes(),
				byteArrayOutputStream.toByteArray());
		}
	}

	@Test(expected = IOException.class)
	public void testGetPackageWrapsStorageErrors() throws Exception {
		FileVersion fileVersion = Mockito.mock(FileVersion.class);

		Mockito.when(
			fileVersion.getFileName()
		).thenReturn(
			"x.elpx"
		);

		Mockito.when(
			fileVersion.getContentStream(true)
		).thenThrow(
			new NoSuchFileEntryException()
		);

		Mockito.when(
			_exeResourceService.getPackage(_ID, null)
		).thenReturn(
			fileVersion
		);

		StreamingOutput streamingOutput =
			(StreamingOutput)_application.getPackage(
				_ID, null
			).getEntity();

		streamingOutput.write(new ByteArrayOutputStream());
	}

	@Test
	public void testGetResourcesChecksTheSite() throws Exception {
		Mockito.when(
			_exeResourceService.getResources(LiferayMocks.GROUP_ID, "x")
		).thenReturn(
			Collections.singletonList(_exeResource)
		);

		Mockito.when(
			_exeResourceService.canAddResource(LiferayMocks.GROUP_ID)
		).thenReturn(
			true
		);

		JSONObject jsonObject = _json(
			_application.getResources(LiferayMocks.GROUP_ID, "x"));

		Assert.assertTrue(jsonObject.getBoolean("canAdd"));
		Assert.assertEquals(
			_ID,
			jsonObject.getJSONArray(
				"items"
			).getJSONObject(
				0
			).getLong(
				"id"
			));

		for (long groupId : new long[] {_FOREIGN_GROUP_ID, 404L}) {
			try {
				_application.getResources(groupId, null);

				Assert.fail();
			}
			catch (NoSuchGroupException noSuchGroupException) {
				Assert.assertNotNull(noSuchGroupException);
			}
		}
	}

	@Test
	public void testParseETag() {
		Assert.assertEquals(
			"1.2", ExeLearningRestApplication.parseETag("\"1.2\""));
		Assert.assertEquals(
			"1.2", ExeLearningRestApplication.parseETag(" W/\"1.2\" "));
		Assert.assertEquals("1.2", ExeLearningRestApplication.parseETag("1.2"));
		Assert.assertEquals("\"", ExeLearningRestApplication.parseETag("\""));
		Assert.assertNull(ExeLearningRestApplication.parseETag(""));
		Assert.assertNull(ExeLearningRestApplication.parseETag(null));
	}

	@Test
	public void testResourceOperations() throws Exception {
		Mockito.when(
			_exeResourceService.getResource(_ID)
		).thenReturn(
			_exeResource
		);

		Mockito.when(
			_exeResourceService.publish(_ID, "1.0")
		).thenReturn(
			_exeResource
		);

		Mockito.when(
			_exeResourceService.unpublish(_ID)
		).thenReturn(
			_exeResource
		);

		Mockito.when(
			_exeResourceService.preview(
				Mockito.eq(_ID), Mockito.eq("1.0"), Mockito.anyLong())
		).thenReturn(
			"/o/exelearning-content/t/index.html"
		);

		Assert.assertEquals(
			"demo.elpx",
			_json(
				_application.getResource(_ID)
			).getString(
				"fileName"
			));
		Assert.assertEquals(
			"1.0",
			_json(
				_application.publishResource(_ID, "1.0")
			).getString(
				"version"
			));
		Assert.assertFalse(
			_json(
				_application.unpublishResource(_ID)
			).getJSONObject(
				"permissions"
			).getBoolean(
				"delete"
			));
		Assert.assertEquals(
			"/o/exelearning-content/t/index.html",
			_json(
				_application.previewResource(_ID, "1.0")
			).getString(
				"url"
			));
	}

	@Test
	public void testSingletonsIncludeTheExceptionMapper() {
		Set<Object> singletons = _application.getSingletons();

		Assert.assertTrue(singletons.contains(_application));
		Assert.assertEquals(singletons.toString(), 2, singletons.size());
		Assert.assertTrue(
			singletons.stream(
			).anyMatch(
				ExeLearningExceptionMapper.class::isInstance
			));
	}

	@Test
	public void testUpdatePackageRequiresIfMatch() throws Exception {
		InputStream inputStream = new ByteArrayInputStream(new byte[0]);

		Response response = _application.updatePackage(
			_ID, "demo.elpx", null, inputStream);

		Assert.assertEquals(428, response.getStatus());
		Assert.assertEquals(
			"if-match-required",
			_json(
				response
			).getString(
				"code"
			));

		Mockito.when(
			_exeResourceService.updatePackage(
				_ID, "1.0", "demo.elpx", inputStream)
		).thenReturn(
			_exeResource
		);

		Assert.assertEquals(
			200,
			_application.updatePackage(
				_ID, "demo.elpx", "W/\"1.0\"", inputStream
			).getStatus());
	}

	@Test
	public void testUploadResource() throws Exception {
		InputStream inputStream = new ByteArrayInputStream(new byte[0]);

		Mockito.when(
			_exeResourceService.addResource(
				LiferayMocks.GROUP_ID, "demo.elpx", inputStream)
		).thenReturn(
			_exeResource
		);

		Response response = _application.uploadResource(
			LiferayMocks.GROUP_ID, "demo.elpx", inputStream);

		Assert.assertEquals(201, response.getStatus());
		Assert.assertEquals(
			"application/json;charset=UTF-8",
			response.getMediaType(
			).toString());
		Assert.assertEquals(
			_ID,
			_json(
				response
			).getLong(
				"id"
			));
	}

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private JSONObject _json(Response response) throws Exception {
		return new JSONObjectImpl((String)response.getEntity());
	}

	private static final long _FOREIGN_GROUP_ID = 30L;

	private static final long _ID = 3000L;

	private final ExeLearningRestApplication _application =
		new ExeLearningRestApplication();
	private ExeResource _exeResource;
	private final ExeResourceService _exeResourceService = Mockito.mock(
		ExeResourceService.class);
	private FileVersion _fileVersion;
	private final GroupLocalService _groupLocalService = Mockito.mock(
		GroupLocalService.class);

}