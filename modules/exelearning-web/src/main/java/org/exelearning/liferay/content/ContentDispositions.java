/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.content;

import java.net.URLEncoder;

import java.nio.charset.StandardCharsets;

/**
 * @author eXeLearning contributors
 */
public class ContentDispositions {

	/**
	 * Returns an attachment disposition with an ASCII fallback and the RFC
	 * 5987 UTF-8 file name.
	 */
	public static String attachment(String fileName) {
		String ascii = fileName.replaceAll("[^\\x20-\\x7e]|[\"\\\\]", "_");

		String encoded = URLEncoder.encode(
			fileName, StandardCharsets.UTF_8
		).replace(
			"+", "%20"
		);

		return "attachment; filename=\"" + ascii + "\"; filename*=UTF-8''" +
			encoded;
	}

}