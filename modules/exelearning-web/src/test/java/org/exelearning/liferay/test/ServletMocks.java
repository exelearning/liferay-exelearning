/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.nio.charset.StandardCharsets;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletResponse;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Records what a servlet writes to its response.
 *
 * @author eXeLearning contributors
 */
public class ServletMocks {

	public static Recorder response() throws Exception {
		Recorder recorder = new Recorder();

		HttpServletResponse httpServletResponse = Mockito.mock(
			HttpServletResponse.class);

		Mockito.doAnswer(
			invocation -> recorder.headers.put(
				invocation.getArgument(0), invocation.getArgument(1))
		).when(
			httpServletResponse
		).setHeader(
			ArgumentMatchers.anyString(), ArgumentMatchers.anyString()
		);

		Mockito.doAnswer(
			invocation -> {
				recorder.status = invocation.getArgument(0);

				return null;
			}
		).when(
			httpServletResponse
		).setStatus(
			ArgumentMatchers.anyInt()
		);

		Mockito.doAnswer(
			invocation -> {
				recorder.contentType = invocation.getArgument(0);

				return null;
			}
		).when(
			httpServletResponse
		).setContentType(
			ArgumentMatchers.anyString()
		);

		Mockito.doAnswer(
			invocation -> {
				recorder.contentLength = invocation.getArgument(0);

				return null;
			}
		).when(
			httpServletResponse
		).setContentLengthLong(
			ArgumentMatchers.anyLong()
		);

		Mockito.doAnswer(
			invocation -> {
				recorder.contentLength = (int)invocation.getArgument(0);

				return null;
			}
		).when(
			httpServletResponse
		).setContentLength(
			ArgumentMatchers.anyInt()
		);

		Mockito.when(
			httpServletResponse.getOutputStream()
		).thenReturn(
			new ServletOutputStream() {

				@Override
				public boolean isReady() {
					return true;
				}

				@Override
				public void setWriteListener(WriteListener writeListener) {
				}

				@Override
				public void write(int b) {
					recorder.body.write(b);
				}

			}
		);

		Mockito.when(
			httpServletResponse.getWriter()
		).thenReturn(
			new PrintWriter(recorder.text, true)
		);

		recorder.httpServletResponse = httpServletResponse;

		return recorder;
	}

	public static class Recorder {

		public String bodyText() {
			return new String(body.toByteArray(), StandardCharsets.UTF_8) +
				text.toString();
		}

		public final ByteArrayOutputStream body = new ByteArrayOutputStream();
		public long contentLength = -1;
		public String contentType;
		public final Map<String, String> headers = new HashMap<>();
		public HttpServletResponse httpServletResponse;
		public int status = 200;
		public final StringWriter text = new StringWriter();

	}

}