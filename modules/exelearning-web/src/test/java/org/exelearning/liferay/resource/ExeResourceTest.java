/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.resource;

import com.liferay.expando.kernel.model.ExpandoBridge;
import com.liferay.expando.kernel.model.ExpandoColumnConstants;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.UnicodeProperties;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.io.Serializable;

import java.nio.file.Path;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.exelearning.liferay.elpx.ElpxArchive;
import org.exelearning.liferay.test.LiferayMocks;
import org.exelearning.liferay.test.TestJSON;
import org.exelearning.liferay.test.TestZips;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * @author eXeLearning contributors
 */
public class ExeResourceTest {

	@Test
	public void testAttributesCreateHiddenColumnsOncePerCompany()
		throws Exception {

		Map<String, Serializable> values = new HashMap<>();

		ExpandoBridge expandoBridge = LiferayMocks.expandoBridge(values);

		FileEntry fileEntry = LiferayMocks.fileEntry(
			1, expandoBridge, LiferayMocks.fileVersion("1.0", _package()));

		ResourceAttributes resourceAttributes = new ResourceAttributes();

		resourceAttributes.setMetadata(
			fileEntry,
			ElpxArchive.inspect(_package(), TestZips.configuration()));
		resourceAttributes.setPublishedVersion(fileEntry, "1.0");

		Assert.assertEquals(
			"1.0", resourceAttributes.getPublishedVersion(fileEntry));

		Assert.assertEquals("Ana", values.get(ResourceAttributes.AUTHOR));
		Assert.assertEquals("true", values.get(ResourceAttributes.PUBLISHABLE));
		Assert.assertEquals(
			"v4.0.5", values.get(ResourceAttributes.EXELEARNING_VERSION));
		Assert.assertEquals(
			"1.0",
			resourceAttributes.get(
				fileEntry
			).get(
				ResourceAttributes.PUBLISHED_VERSION
			));

		ArgumentCaptor<UnicodeProperties> unicodePropertiesArgumentCaptor =
			ArgumentCaptor.forClass(UnicodeProperties.class);

		Mockito.verify(
			expandoBridge, Mockito.times(ResourceAttributes.names.size())
		).setAttributeProperties(
			ArgumentMatchers.anyString(),
			unicodePropertiesArgumentCaptor.capture(),
			ArgumentMatchers.eq(false)
		);

		UnicodeProperties unicodeProperties =
			unicodePropertiesArgumentCaptor.getValue();

		Assert.assertEquals(
			"true",
			unicodeProperties.getProperty(
				ExpandoColumnConstants.PROPERTY_HIDDEN));

		Mockito.verify(
			expandoBridge, Mockito.times(ResourceAttributes.names.size())
		).hasAttribute(
			ArgumentMatchers.anyString()
		);
	}

	@Test
	public void testJSON() throws Exception {
		Map<String, Serializable> attributes = new HashMap<>();

		attributes.put(ResourceAttributes.AUTHOR, "Ana");
		attributes.put(ResourceAttributes.PUBLISHABLE, "true");
		attributes.put(ResourceAttributes.PUBLISHED_VERSION, "1.0");

		FileEntry fileEntry = LiferayMocks.fileEntry(
			7, LiferayMocks.expandoBridge(new HashMap<>()),
			LiferayMocks.fileVersion("1.0", _package()),
			LiferayMocks.fileVersion("1.1", _package()));

		ExeResource exeResource = new ExeResource(
			fileEntry, attributes, true, false, false,
			fileEntry.getFileVersions(WorkflowConstants.STATUS_ANY));

		JSONObject jsonObject = exeResource.toJSONObject(TestJSON.factory());

		Assert.assertEquals(7, jsonObject.getLong("id"));
		Assert.assertEquals(
			LiferayMocks.GROUP_ID, jsonObject.getLong("groupId"));
		Assert.assertEquals("Ana", jsonObject.getString("author"));
		Assert.assertEquals("", jsonObject.getString("license"));
		Assert.assertEquals("1.0", jsonObject.getString("publishedVersion"));
		Assert.assertEquals("1.1", jsonObject.getString("version"));
		Assert.assertTrue(jsonObject.getBoolean("publishable"));
		Assert.assertEquals(
			"1970-01-01T00:00:00Z", jsonObject.getString("modifiedDate"));
		Assert.assertTrue(
			jsonObject.getJSONObject(
				"permissions"
			).getBoolean(
				"update"
			));

		JSONArray versions = jsonObject.getJSONArray("versions");

		Assert.assertEquals(2, versions.length());
		Assert.assertEquals(
			"Ana",
			versions.getJSONObject(
				1
			).getString(
				"userName"
			));

		Assert.assertTrue(exeResource.canUpdate());
		Assert.assertFalse(exeResource.canDelete());

		Mockito.when(
			fileEntry.getModifiedDate()
		).thenReturn(
			null
		);

		JSONObject unpublished = new ExeResource(
			fileEntry, Collections.emptyMap(), false, false, false,
			Collections.emptyList()
		).toJSONObject(
			TestJSON.factory()
		);

		Assert.assertTrue(unpublished.isNull("publishedVersion"));
		Assert.assertTrue(unpublished.isNull("modifiedDate"));
		Assert.assertFalse(unpublished.has("versions"));
	}

	@Test
	public void testResourceConflictException() {
		ResourceConflictException resourceConflictException =
			new ResourceConflictException("key", "1.4");

		Assert.assertEquals("key", resourceConflictException.getKey());
		Assert.assertEquals(
			"1.4", resourceConflictException.getCurrentVersion());

		Assert.assertEquals(
			Arrays.asList("exelearningAuthor", "exelearningVersion"),
			ResourceAttributes.names.subList(0, 2));
	}

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private Path _package() throws Exception {
		return TestZips.write(
			temporaryFolder.newFile(
			).toPath(),
			TestZips.package_());
	}

}