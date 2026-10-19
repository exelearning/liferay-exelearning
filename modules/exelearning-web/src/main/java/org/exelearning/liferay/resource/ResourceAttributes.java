/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.resource;

import com.liferay.expando.kernel.model.ExpandoBridge;
import com.liferay.expando.kernel.model.ExpandoColumnConstants;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.util.GetterUtil;
import com.liferay.portal.kernel.util.UnicodeProperties;

import java.io.Serializable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.exelearning.liferay.elpx.ElpxPackage;

/**
 * Plugin-owned state of a resource, stored as hidden custom fields (Expando)
 * of its Documents and Media file entry: the package metadata of the latest
 * revision and the version that is currently published. No custom tables are
 * needed and the values follow the file entry's lifecycle.
 *
 * @author eXeLearning contributors
 */
public class ResourceAttributes {

	public static final String AUTHOR = "exelearningAuthor";

	public static final String EXELEARNING_VERSION = "exelearningVersion";

	public static final String LANGUAGE = "exelearningLanguage";

	public static final String LICENSE = "exelearningLicense";

	public static final String PUBLISHABLE = "exelearningPublishable";

	public static final String PUBLISHED_VERSION =
		"exelearningPublishedVersion";

	public static final List<String> names = Collections.unmodifiableList(
		Arrays.asList(
			AUTHOR, EXELEARNING_VERSION, LANGUAGE, LICENSE, PUBLISHABLE,
			PUBLISHED_VERSION));

	public Map<String, Serializable> get(FileEntry fileEntry)
		throws PortalException {

		ExpandoBridge expandoBridge = _getExpandoBridge(fileEntry);

		return expandoBridge.getAttributes(names, false);
	}

	public String getPublishedVersion(FileEntry fileEntry)
		throws PortalException {

		ExpandoBridge expandoBridge = _getExpandoBridge(fileEntry);

		return GetterUtil.getString(
			expandoBridge.getAttribute(PUBLISHED_VERSION, false));
	}

	public void setMetadata(FileEntry fileEntry, ElpxPackage elpxPackage)
		throws PortalException {

		ExpandoBridge expandoBridge = _getExpandoBridge(fileEntry);

		expandoBridge.setAttribute(AUTHOR, elpxPackage.getAuthor(), false);
		expandoBridge.setAttribute(
			EXELEARNING_VERSION, elpxPackage.getExeLearningVersion(), false);
		expandoBridge.setAttribute(LANGUAGE, elpxPackage.getLanguage(), false);
		expandoBridge.setAttribute(LICENSE, elpxPackage.getLicense(), false);
		expandoBridge.setAttribute(
			PUBLISHABLE, String.valueOf(elpxPackage.isPublishable()), false);
	}

	public void setPublishedVersion(FileEntry fileEntry, String version)
		throws PortalException {

		ExpandoBridge expandoBridge = _getExpandoBridge(fileEntry);

		expandoBridge.setAttribute(PUBLISHED_VERSION, version, false);
	}

	private ExpandoBridge _getExpandoBridge(FileEntry fileEntry)
		throws PortalException {

		ExpandoBridge expandoBridge = fileEntry.getExpandoBridge();

		if (_initializedCompanyIds.contains(fileEntry.getCompanyId())) {
			return expandoBridge;
		}

		UnicodeProperties unicodeProperties = new UnicodeProperties(true);

		unicodeProperties.setProperty(
			ExpandoColumnConstants.INDEX_TYPE,
			String.valueOf(ExpandoColumnConstants.INDEX_TYPE_NONE));
		unicodeProperties.setProperty(
			ExpandoColumnConstants.PROPERTY_HIDDEN, Boolean.TRUE.toString());

		for (String name : names) {
			if (!expandoBridge.hasAttribute(name)) {
				expandoBridge.addAttribute(
					name, ExpandoColumnConstants.STRING, "", false);
				expandoBridge.setAttributeProperties(
					name, unicodeProperties, false);
			}
		}

		_initializedCompanyIds.add(fileEntry.getCompanyId());

		return expandoBridge;
	}

	private final Set<Long> _initializedCompanyIds =
		ConcurrentHashMap.newKeySet();

}