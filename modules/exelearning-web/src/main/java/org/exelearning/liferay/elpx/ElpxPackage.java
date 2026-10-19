/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.elpx;

import java.util.Collections;
import java.util.Map;

/**
 * Result of inspecting a valid package: its project properties and whether it
 * ships the HTML5 export that can be published.
 *
 * @author eXeLearning contributors
 */
public class ElpxPackage {

	public ElpxPackage(
		Map<String, String> properties, boolean publishable, int entryCount,
		long extractedSize) {

		_properties = Collections.unmodifiableMap(properties);
		_publishable = publishable;
		_entryCount = entryCount;
		_extractedSize = extractedSize;
	}

	public String getAuthor() {
		return _get("pp_author");
	}

	public String getDescription() {
		return _get("pp_description");
	}

	public int getEntryCount() {
		return _entryCount;
	}

	public String getExeLearningVersion() {
		String version = _get("pp_exelearning_version");

		if (version.isEmpty()) {
			version = _get("exe_version");
		}

		return version;
	}

	public long getExtractedSize() {
		return _extractedSize;
	}

	public String getLanguage() {
		String language = _get("pp_lang");

		if (language.isEmpty()) {
			language = _get("lom_general_language");
		}

		return language;
	}

	public String getLicense() {
		String license = _get("pp_license");

		if (license.isEmpty()) {
			license = _get("license");
		}

		return license;
	}

	public Map<String, String> getProperties() {
		return _properties;
	}

	public String getTitle() {
		return _get("pp_title");
	}

	/**
	 * Returns <code>true</code> when the package contains a root
	 * <code>index.html</code>, i.e. the HTML5 export written by eXeLearning 4.
	 */
	public boolean isPublishable() {
		return _publishable;
	}

	private String _get(String key) {
		String value = _properties.get(key);

		if (value == null) {
			return "";
		}

		return value.trim();
	}

	private final int _entryCount;
	private final long _extractedSize;
	private final Map<String, String> _properties;
	private final boolean _publishable;

}