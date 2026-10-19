/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.elpx;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import java.nio.charset.StandardCharsets;

import java.util.Map;

import org.exelearning.liferay.test.TestZips;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author eXeLearning contributors
 */
public class ElpxMetadataParserTest {

	@Test
	public void testDoesNotResolveExternalEntities() throws Exception {
		String xml =
			"<?xml version=\"1.0\"?><!DOCTYPE ode [<!ENTITY xxe SYSTEM " +
				"\"file:///etc/passwd\">]><ode><odeProperties><odeProperty>" +
					"<key>pp_title</key><value>&xxe;</value></odeProperty>" +
						"</odeProperties></ode>";

		try {
			Map<String, String> properties = ElpxMetadataParser.parse(
				_stream(xml));

			Assert.assertFalse(
				properties.getOrDefault(
					"pp_title", ""
				).contains(
					"root:"
				));
		}
		catch (ElpxException elpxException) {
			Assert.assertEquals(
				"the-package-metadata-could-not-be-read",
				elpxException.getKey());
		}
	}

	@Test
	public void testDoesNotExpandEntityBombs() throws Exception {
		String xml =
			"<?xml version=\"1.0\"?><!DOCTYPE ode [<!ENTITY a \"aaaaaaaaaa\">" +
				"<!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">]><ode>" +
					"<odeProperties><odeProperty><key>pp_title</key>" +
						"<value>&b;</value></odeProperty></odeProperties>" +
							"</ode>";

		try {
			Map<String, String> properties = ElpxMetadataParser.parse(
				_stream(xml));

			String title = properties.getOrDefault("pp_title", "");

			Assert.assertTrue(title.length() < 100);
		}
		catch (ElpxException elpxException) {
			Assert.assertEquals(
				"the-package-metadata-could-not-be-read",
				elpxException.getKey());
		}
	}

	@Test
	public void testLimitsValueLengthAndCount() throws Exception {
		StringBuilder sb = new StringBuilder("<ode><odeProperties>");

		sb.append("<odeProperty><key>long</key><value>");

		for (int i = 0; i < (ElpxMetadataParser.MAX_VALUE_LENGTH + 10); i++) {
			sb.append('x');
		}

		sb.append("</value></odeProperty>");

		for (int i = 0; i < (ElpxMetadataParser.MAX_PROPERTIES + 10); i++) {
			sb.append("<odeProperty><key>k");
			sb.append(i);
			sb.append("</key><value>v</value></odeProperty>");
		}

		sb.append("</odeProperties></ode>");

		Map<String, String> properties = ElpxMetadataParser.parse(
			_stream(sb.toString()));

		Assert.assertEquals(
			properties.toString(), ElpxMetadataParser.MAX_PROPERTIES,
			properties.size());
		Assert.assertEquals(
			ElpxMetadataParser.MAX_VALUE_LENGTH,
			properties.get(
				"long"
			).length());
	}

	@Test
	public void testMalformedXml() {
		try {
			ElpxMetadataParser.parse(_stream("<ode><odeProperties>"));

			Assert.fail();
		}
		catch (ElpxException elpxException) {
			Assert.assertEquals(
				"the-package-metadata-could-not-be-read",
				elpxException.getKey());
		}
	}

	@Test
	public void testParse() throws Exception {
		Map<String, String> properties = ElpxMetadataParser.parse(
			_stream(TestZips.CONTENT_XML));

		Assert.assertEquals("Demo & test", properties.get("pp_title"));
		Assert.assertEquals("Ana", properties.get("pp_author"));
		Assert.assertEquals("es", properties.get("pp_lang"));
		Assert.assertEquals("CC BY-SA 4.0", properties.get("pp_license"));
		Assert.assertEquals("v4.0.5", properties.get("exe_version"));
	}

	@Test
	public void testParseCData() throws Exception {
		Map<String, String> properties = ElpxMetadataParser.parse(
			_stream(
				"<ode><odeProperties><odeProperty><key>pp_description</key>" +
					"<value><![CDATA[<b>Bold</b>]]></value></odeProperty>" +
						"<odeProperty><value>orphan</value></odeProperty>" +
							"</odeProperties></ode>"));

		Assert.assertEquals("<b>Bold</b>", properties.get("pp_description"));
		Assert.assertEquals(properties.toString(), 1, properties.size());
	}

	private InputStream _stream(String xml) {
		return new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8));
	}

}