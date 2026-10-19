/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.resource;

import com.liferay.portal.kernel.exception.PortalException;

/**
 * Thrown when an update is based on a revision that is no longer the latest
 * one, or while another update of the same resource is in progress.
 *
 * @author eXeLearning contributors
 */
public class ResourceConflictException extends PortalException {

	public ResourceConflictException(String key, String currentVersion) {
		super(key);

		_key = key;
		_currentVersion = currentVersion;
	}

	public String getCurrentVersion() {
		return _currentVersion;
	}

	public String getKey() {
		return _key;
	}

	private final String _currentVersion;
	private final String _key;

}