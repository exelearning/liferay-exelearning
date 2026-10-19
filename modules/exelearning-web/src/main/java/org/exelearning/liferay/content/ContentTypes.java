/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.content;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Fixed, explicit content types for files of eXeLearning exports and the
 * static editor. Unknown types are served as downloads, never sniffed.
 *
 * @author eXeLearning contributors
 */
public class ContentTypes {

	public static final String DEFAULT = "application/octet-stream";

	public static String get(String fileName) {
		int index = fileName.lastIndexOf('.');

		if (index < 0) {
			return DEFAULT;
		}

		String extension = fileName.substring(
			index + 1
		).toLowerCase(
			Locale.ROOT
		);

		return _contentTypes.getOrDefault(extension, DEFAULT);
	}

	public static boolean isKnown(String fileName) {
		return !DEFAULT.equals(get(fileName));
	}

	private static Map<String, String> _getContentTypes() {
		Map<String, String> contentTypesMap = new HashMap<>();

		String[][] contentTypes = {
			{"aac", "audio/aac"}, {"avif", "image/avif"}, {"bmp", "image/bmp"},
			{"css", "text/css; charset=utf-8"}, {"csv", "text/csv"},
			{"elpx", "application/zip"}, {"epub", "application/epub+zip"},
			{"flac", "audio/flac"}, {"gif", "image/gif"},
			{"htm", "text/html; charset=utf-8"},
			{"html", "text/html; charset=utf-8"}, {"ico", "image/x-icon"},
			{"jpeg", "image/jpeg"}, {"jpg", "image/jpeg"},
			{"js", "text/javascript; charset=utf-8"},
			{"json", "application/json"}, {"m4a", "audio/mp4"},
			{"m4v", "video/mp4"}, {"map", "application/json"},
			{"md", "text/markdown; charset=utf-8"},
			{"mjs", "text/javascript; charset=utf-8"}, {"mp3", "audio/mpeg"},
			{"mp4", "video/mp4"}, {"oga", "audio/ogg"}, {"ogg", "audio/ogg"},
			{"ogv", "video/ogg"}, {"otf", "font/otf"},
			{"pdf", "application/pdf"}, {"png", "image/png"},
			{"svg", "image/svg+xml"}, {"ttf", "font/ttf"},
			{"txt", "text/plain; charset=utf-8"}, {"vtt", "text/vtt"},
			{"wasm", "application/wasm"}, {"wav", "audio/wav"},
			{"webm", "video/webm"},
			{"webmanifest", "application/manifest+json"},
			{"webp", "image/webp"}, {"woff", "font/woff"},
			{"woff2", "font/woff2"}, {"xml", "application/xml"},
			{"zip", "application/zip"}, {"zst", "application/zstd"}
		};

		for (String[] contentType : contentTypes) {
			contentTypesMap.put(contentType[0], contentType[1]);
		}

		return contentTypesMap;
	}

	private static final Map<String, String> _contentTypes = _getContentTypes();

}