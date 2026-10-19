/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.content;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;

import org.exelearning.liferay.test.ServletMocks;

import org.junit.Assert;
import org.junit.Test;

import org.mockito.Mockito;

/**
 * @author eXeLearning contributors
 */
public class ContentResponsesTest {

	@Test
	public void testAttachmentDisposition() {
		Assert.assertEquals(
			"attachment; filename=\"Curso de f_sica _x_.elpx\"; " +
				"filename*=UTF-8''Curso%20de%20f%C3%ADsica%20%22x%22.elpx",
			ContentDispositions.attachment("Curso de física \"x\".elpx"));
	}

	@Test
	public void testContentTypes() {
		Assert.assertEquals(
			"text/html; charset=utf-8", ContentTypes.get("index.HTML"));
		Assert.assertEquals("font/woff2", ContentTypes.get("a/b.woff2"));
		Assert.assertEquals("video/mp4", ContentTypes.get("clip.mp4"));
		Assert.assertEquals(ContentTypes.DEFAULT, ContentTypes.get("Makefile"));
		Assert.assertEquals(ContentTypes.DEFAULT, ContentTypes.get("x.exe"));
		Assert.assertTrue(ContentTypes.isKnown("a.svg"));
		Assert.assertFalse(ContentTypes.isKnown("a.docx"));
	}

	@Test
	public void testErrorResponse() throws Exception {
		ServletMocks.Recorder recorder = ServletMocks.response();

		FileResponses.error(recorder.httpServletResponse, 404);

		Assert.assertEquals(404, recorder.status);
		Assert.assertEquals("404", recorder.bodyText());
		Assert.assertEquals("no-store", recorder.headers.get("Cache-Control"));
		Assert.assertEquals(
			"nosniff", recorder.headers.get("X-Content-Type-Options"));
	}

	@Test
	public void testHeadSendsHeadersOnly() throws Exception {
		ServletMocks.Recorder recorder = _send("HEAD", null, "a.css");

		Assert.assertEquals(10, recorder.contentLength);
		Assert.assertEquals("", recorder.bodyText());
		Assert.assertEquals("text/css; charset=utf-8", recorder.contentType);
	}

	@Test
	public void testRanges() throws Exception {
		ServletMocks.Recorder recorder = _send("GET", "bytes=2-4", "v.mp4");

		Assert.assertEquals(206, recorder.status);
		Assert.assertEquals("234", recorder.bodyText());
		Assert.assertEquals(
			"bytes 2-4/10", recorder.headers.get("Content-Range"));
		Assert.assertEquals(3, recorder.contentLength);

		Assert.assertEquals(
			"6789",
			_send(
				"GET", "bytes=-4", "v.mp4"
			).bodyText());
		Assert.assertEquals(
			"89",
			_send(
				"GET", "bytes=8-", "v.mp4"
			).bodyText());
		Assert.assertEquals(
			"89",
			_send(
				"GET", "bytes=8-99", "v.mp4"
			).bodyText());
		Assert.assertEquals(
			"0123456789",
			_send(
				"GET", "bytes=-99", "v.mp4"
			).bodyText());

		ServletMocks.Recorder invalid = _send("GET", "bytes=20-30", "v.mp4");

		Assert.assertEquals(416, invalid.status);
		Assert.assertEquals("bytes */10", invalid.headers.get("Content-Range"));

		Assert.assertEquals(
			"0123456789",
			_send(
				"GET", "bytes=-", "v.mp4"
			).bodyText());
		Assert.assertEquals(
			"0123456789",
			_send(
				"GET", "items=1-2", "v.mp4"
			).bodyText());
	}

	@Test
	public void testSendsKnownAndUnknownTypes() throws Exception {
		ServletMocks.Recorder recorder = _send("GET", null, "index.html");

		Assert.assertEquals("0123456789", recorder.bodyText());
		Assert.assertEquals("bytes", recorder.headers.get("Accept-Ranges"));
		Assert.assertNull(recorder.headers.get("Content-Disposition"));

		recorder = _send("GET", null, "worksheet.docx");

		Assert.assertEquals(
			"attachment", recorder.headers.get("Content-Disposition"));
	}

	@Test
	public void testStopsAtTheEndOfShortStreams() throws Exception {
		ServletMocks.Recorder recorder = ServletMocks.response();

		FileResponses.send(
			_request("GET", null), recorder.httpServletResponse, "a.txt", 50,
			new ByteArrayInputStream(new byte[] {65}));

		Assert.assertEquals("A", recorder.bodyText());
	}

	@Test(expected = IOException.class)
	public void testUnskippableStreamsFail() throws Exception {
		ServletMocks.Recorder recorder = ServletMocks.response();

		InputStream inputStream = new InputStream() {

			@Override
			public int read() {
				return -1;
			}

			@Override
			public long skip(long n) {
				return 0;
			}

		};

		FileResponses.send(
			_request("GET", "bytes=5-"), recorder.httpServletResponse, "a.mp4",
			10, inputStream);
	}

	private HttpServletRequest _request(String method, String range) {
		HttpServletRequest httpServletRequest = Mockito.mock(
			HttpServletRequest.class);

		Mockito.when(
			httpServletRequest.getMethod()
		).thenReturn(
			method
		);

		Mockito.when(
			httpServletRequest.getHeader("Range")
		).thenReturn(
			range
		);

		return httpServletRequest;
	}

	private ServletMocks.Recorder _send(
			String method, String range, String fileName)
		throws Exception {

		ServletMocks.Recorder recorder = ServletMocks.response();

		byte[] bytes = "0123456789".getBytes(StandardCharsets.UTF_8);

		FileResponses.send(
			_request(method, range), recorder.httpServletResponse, fileName,
			bytes.length, new ByteArrayInputStream(bytes));

		return recorder;
	}

}