/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.content;

import com.liferay.portal.kernel.util.GetterUtil;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Streams files with an explicit content type and support for a single byte
 * range, which browsers need to seek in audio and video.
 *
 * @author eXeLearning contributors
 */
public class FileResponses {

	public static void error(
			HttpServletResponse httpServletResponse, int status)
		throws IOException {

		httpServletResponse.setStatus(status);
		httpServletResponse.setContentType("text/plain; charset=utf-8");
		httpServletResponse.setHeader("Cache-Control", "no-store");
		httpServletResponse.setHeader("X-Content-Type-Options", "nosniff");

		httpServletResponse.getWriter(
		).write(
			String.valueOf(status)
		);
	}

	public static void send(
			HttpServletRequest httpServletRequest,
			HttpServletResponse httpServletResponse, String fileName,
			long length, InputStream inputStream)
		throws IOException {

		httpServletResponse.setContentType(ContentTypes.get(fileName));
		httpServletResponse.setHeader("Accept-Ranges", "bytes");
		httpServletResponse.setHeader("X-Content-Type-Options", "nosniff");

		if (!ContentTypes.isKnown(fileName)) {
			httpServletResponse.setHeader("Content-Disposition", "attachment");
		}

		long start = 0;
		long end = length - 1;

		String range = httpServletRequest.getHeader("Range");

		if (range != null) {
			Matcher matcher = _rangePattern.matcher(range.trim());

			if (matcher.matches() &&
				!(matcher.group(
					1
				).isEmpty() &&
				  matcher.group(
					  2
				  ).isEmpty())) {

				if (matcher.group(
						1
					).isEmpty()) {

					start = Math.max(
						0, length - GetterUtil.getLong(matcher.group(2)));
				}
				else {
					start = GetterUtil.getLong(matcher.group(1));

					if (!matcher.group(
							2
						).isEmpty()) {

						end = Math.min(
							end, GetterUtil.getLong(matcher.group(2)));
					}
				}

				if ((start > end) || (start >= length)) {
					httpServletResponse.setHeader(
						"Content-Range", "bytes */" + length);

					error(
						httpServletResponse,
						HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);

					return;
				}

				httpServletResponse.setStatus(
					HttpServletResponse.SC_PARTIAL_CONTENT);
				httpServletResponse.setHeader(
					"Content-Range",
					"bytes " + start + "-" + end + "/" + length);
			}
		}

		long count = end - start + 1;

		httpServletResponse.setContentLengthLong(Math.max(count, 0));

		if (Objects.equals(httpServletRequest.getMethod(), "HEAD")) {
			return;
		}

		long skip = start;

		while (skip > 0) {
			long skipped = inputStream.skip(skip);

			if (skipped <= 0) {
				throw new IOException("Unable to skip to byte " + start);
			}

			skip -= skipped;
		}

		OutputStream outputStream = httpServletResponse.getOutputStream();

		byte[] buffer = new byte[16384];

		while (count > 0) {
			int read = inputStream.read(
				buffer, 0, (int)Math.min(buffer.length, count));

			if (read == -1) {
				break;
			}

			outputStream.write(buffer, 0, read);

			count -= read;
		}
	}

	private static final Pattern _rangePattern = Pattern.compile(
		"bytes=(\\d{0,18})-(\\d{0,18})");

}