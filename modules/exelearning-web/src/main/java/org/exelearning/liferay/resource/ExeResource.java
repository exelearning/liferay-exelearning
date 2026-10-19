/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.resource;

import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.FileVersion;
import com.liferay.portal.kernel.util.GetterUtil;

import java.io.Serializable;

import java.time.Instant;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Read model of an eXeLearning resource: a Documents and Media file entry plus
 * the plugin attributes and the current user's permissions on it.
 *
 * @author eXeLearning contributors
 */
public class ExeResource {

	public ExeResource(
		FileEntry fileEntry, Map<String, Serializable> attributes,
		boolean canUpdate, boolean canDelete, boolean canPermissions,
		List<FileVersion> fileVersions) {

		_fileEntry = fileEntry;
		_attributes = attributes;
		_canUpdate = canUpdate;
		_canDelete = canDelete;
		_canPermissions = canPermissions;
		_fileVersions = fileVersions;
	}

	public boolean canDelete() {
		return _canDelete;
	}

	public boolean canPermissions() {
		return _canPermissions;
	}

	public boolean canUpdate() {
		return _canUpdate;
	}

	public String getAuthor() {
		return getAttribute(ResourceAttributes.AUTHOR);
	}

	public String getAttribute(String name) {
		return GetterUtil.getString(_attributes.get(name));
	}

	public String getExeLearningVersion() {
		return getAttribute(ResourceAttributes.EXELEARNING_VERSION);
	}

	public String getDescription() {
		return _fileEntry.getDescription();
	}

	public FileEntry getFileEntry() {
		return _fileEntry;
	}

	public long getFileEntryId() {
		return _fileEntry.getFileEntryId();
	}

	public List<FileVersion> getFileVersions() {
		return _fileVersions;
	}

	public String getFileName() {
		return _fileEntry.getFileName();
	}

	public long getGroupId() {
		return _fileEntry.getGroupId();
	}

	public String getLanguage() {
		return getAttribute(ResourceAttributes.LANGUAGE);
	}

	public String getLicense() {
		return getAttribute(ResourceAttributes.LICENSE);
	}

	public String getPublishedVersion() {
		return getAttribute(ResourceAttributes.PUBLISHED_VERSION);
	}

	public String getTitle() {
		return _fileEntry.getTitle();
	}

	public String getVersion() {
		return _fileEntry.getVersion();
	}

	public boolean isPublishable() {
		return GetterUtil.getBoolean(
			getAttribute(ResourceAttributes.PUBLISHABLE));
	}

	public boolean isPublished() {
		return !getPublishedVersion().isEmpty();
	}

	public JSONObject toJSONObject(JSONFactory jsonFactory) {
		JSONObject jsonObject = jsonFactory.createJSONObject();

		jsonObject.put(
			"author", getAttribute(ResourceAttributes.AUTHOR)
		).put(
			"description", getDescription()
		).put(
			"exelearningVersion",
			getAttribute(ResourceAttributes.EXELEARNING_VERSION)
		).put(
			"fileName", getFileName()
		).put(
			"groupId", getGroupId()
		).put(
			"id", getFileEntryId()
		).put(
			"language", getAttribute(ResourceAttributes.LANGUAGE)
		).put(
			"license", getAttribute(ResourceAttributes.LICENSE)
		).put(
			"modifiedDate", _format(_fileEntry.getModifiedDate())
		).put(
			"permissions",
			jsonFactory.createJSONObject(
			).put(
				"delete", _canDelete
			).put(
				"update", _canUpdate
			)
		).put(
			"publishable", isPublishable()
		).put(
			"publishedVersion", isPublished() ? getPublishedVersion() : null
		).put(
			"size", _fileEntry.getSize()
		).put(
			"title", getTitle()
		).put(
			"version", getVersion()
		);

		if (!_fileVersions.isEmpty()) {
			JSONArray jsonArray = jsonFactory.createJSONArray();

			for (FileVersion fileVersion : _fileVersions) {
				jsonArray.put(
					jsonFactory.createJSONObject(
					).put(
						"changeLog", fileVersion.getChangeLog()
					).put(
						"createDate", _format(fileVersion.getCreateDate())
					).put(
						"size", fileVersion.getSize()
					).put(
						"userName", fileVersion.getUserName()
					).put(
						"version", fileVersion.getVersion()
					));
			}

			jsonObject.put("versions", jsonArray);
		}

		return jsonObject;
	}

	private String _format(Date date) {
		if (date == null) {
			return null;
		}

		Instant instant = date.toInstant();

		return instant.toString();
	}

	private final Map<String, Serializable> _attributes;
	private final boolean _canDelete;
	private final boolean _canPermissions;
	private final boolean _canUpdate;
	private final FileEntry _fileEntry;
	private final List<FileVersion> _fileVersions;

}