/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.elpx;

import com.liferay.portal.kernel.exception.PortalException;

/**
 * Rejection of an eXeLearning package. The key is a language key of this
 * module, so the message shown to users is always localized and never echoes
 * raw archive content beyond the offending entry name.
 *
 * @author eXeLearning contributors
 */
public class ElpxException extends PortalException {

	public ElpxException(String key, Object... arguments) {
		super(key);

		_key = key;
		_arguments = arguments;
	}

	public ElpxException(String key, Throwable throwable) {
		super(key, throwable);

		_key = key;

		_arguments = new Object[0];
	}

	public Object[] getArguments() {
		return _arguments.clone();
	}

	public String getKey() {
		return _key;
	}

	private final Object[] _arguments;
	private final String _key;

}