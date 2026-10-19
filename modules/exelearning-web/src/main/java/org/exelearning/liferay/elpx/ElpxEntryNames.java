/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.elpx;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validation rules for ZIP entry names and relative content paths. The rules
 * mirror the WordPress and Omeka S integrations (no absolute, Windows, URL or
 * parent paths; no server configuration or server-executable files) and add
 * control-character and length checks.
 *
 * @author eXeLearning contributors
 */
public class ElpxEntryNames {

	public static final int MAX_NAME_LENGTH = 1024;

	public static final int MAX_SEGMENT_LENGTH = 255;

	/**
	 * Returns <code>true</code> for server configuration files and
	 * server-executable types, which never belong in an eXeLearning package.
	 */
	public static boolean isForbidden(String name) {
		int index = name.lastIndexOf('/');

		String baseName = name.substring(index + 1);

		baseName = _trailingDotsAndSpacesPattern.matcher(
			baseName
		).replaceAll(
			""
		).toLowerCase(
			Locale.ROOT
		);

		if (_forbiddenBaseNames.contains(baseName)) {
			return true;
		}

		String[] parts = baseName.split("\\.");

		for (int i = 1; i < parts.length; i++) {
			if (_forbiddenAnyExtensions.contains(parts[i])) {
				return true;
			}
		}

		if ((parts.length > 1) &&
			_forbiddenLastExtensions.contains(parts[parts.length - 1])) {

			return true;
		}

		return false;
	}

	/**
	 * Returns the canonical relative path of an entry, or throws when the name
	 * could escape the extraction directory or is otherwise unsafe.
	 */
	public static String normalize(String name) throws ElpxException {
		if ((name == null) || name.isEmpty() ||
			(name.length() > MAX_NAME_LENGTH)) {

			throw new ElpxException(
				"the-package-contains-an-invalid-path-x", name);
		}

		if ((name.indexOf('\\') >= 0) || name.startsWith("/") ||
			_driveOrSchemePattern.matcher(
				name
			).find()) {

			throw new ElpxException(
				"the-package-contains-an-unsafe-path-x", name);
		}

		for (int i = 0; i < name.length(); i++) {
			char c = name.charAt(i);

			if ((c < 0x20) || (c == 0x7f)) {
				throw new ElpxException(
					"the-package-contains-an-invalid-path-x", name);
			}
		}

		boolean directory = name.endsWith("/");

		StringBuilder sb = new StringBuilder();

		for (String segment : name.split("/")) {
			if (segment.isEmpty() || segment.equals(".")) {
				continue;
			}

			if (segment.equals("..")) {
				throw new ElpxException(
					"the-package-contains-an-unsafe-path-x", name);
			}

			if (segment.length() > MAX_SEGMENT_LENGTH) {
				throw new ElpxException(
					"the-package-contains-an-invalid-path-x", name);
			}

			if (sb.length() > 0) {
				sb.append('/');
			}

			sb.append(segment);
		}

		if (sb.length() == 0) {
			throw new ElpxException(
				"the-package-contains-an-invalid-path-x", name);
		}

		if (directory) {
			sb.append('/');
		}

		return sb.toString();
	}

	private static final Pattern _driveOrSchemePattern = Pattern.compile(
		"^[a-zA-Z][a-zA-Z0-9+.-]*:");
	private static final Set<String> _forbiddenAnyExtensions = new HashSet<>(
		Arrays.asList(
			"phar", "php", "php3", "php4", "php5", "php7", "php8", "phps",
			"pht", "phtm", "phtml", "phpt", "shtml"));
	private static final Set<String> _forbiddenBaseNames = new HashSet<>(
		Arrays.asList(
			".htaccess", ".htpasswd", ".user.ini", "php.ini", "web.config"));
	private static final Set<String> _forbiddenLastExtensions = new HashSet<>(
		Arrays.asList("asp", "aspx", "cgi", "jsp", "jspf", "jspx", "pl", "py"));
	private static final Pattern _trailingDotsAndSpacesPattern =
		Pattern.compile("[.\\s]+$");

}