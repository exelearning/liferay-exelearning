/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.elpx;

import java.io.InputStream;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Reads the project properties of <code>content.xml</code>
 * (<code>odeProperties</code>, <code>odeResources</code> and
 * <code>userPreferences</code> key/value pairs) with a streaming parser that
 * never loads DTDs or resolves entities, and stops before the page tree.
 *
 * @author eXeLearning contributors
 */
public class ElpxMetadataParser {

	public static final int MAX_PROPERTIES = 500;

	public static final int MAX_VALUE_LENGTH = 8192;

	public static Map<String, String> parse(InputStream inputStream)
		throws ElpxException {

		Map<String, String> properties = new LinkedHashMap<>();

		try {

			// The caller owns and closes the input stream

			XMLStreamReader xmlStreamReader =
				_xmlInputFactory.createXMLStreamReader(inputStream);

			String key = null;
			StringBuilder text = null;

			while (xmlStreamReader.hasNext()) {
				int event = xmlStreamReader.next();

				if (event == XMLStreamConstants.START_ELEMENT) {
					String name = xmlStreamReader.getLocalName();

					if (name.equals("odeNavStructures")) {
						break;
					}

					if (name.equals("key") || name.equals("value")) {
						text = new StringBuilder();
					}
				}
				else if ((event == XMLStreamConstants.CHARACTERS) ||
						 (event == XMLStreamConstants.CDATA)) {

					if ((text != null) && (text.length() < MAX_VALUE_LENGTH)) {
						text.append(xmlStreamReader.getText());
					}
				}
				else if ((event == XMLStreamConstants.END_ELEMENT) &&
						 (text != null)) {

					String value = text.toString();

					if (value.length() > MAX_VALUE_LENGTH) {
						value = value.substring(0, MAX_VALUE_LENGTH);
					}

					if (Objects.equals(xmlStreamReader.getLocalName(), "key")) {
						key = value.trim();
					}
					else if (key != null) {
						if (properties.size() >= MAX_PROPERTIES) {
							break;
						}

						properties.putIfAbsent(key, value);

						key = null;
					}

					text = null;
				}
			}
		}
		catch (XMLStreamException xmlStreamException) {
			throw new ElpxException(
				"the-package-metadata-could-not-be-read", xmlStreamException);
		}

		return properties;
	}

	private static final XMLInputFactory _xmlInputFactory;

	static {
		_xmlInputFactory = XMLInputFactory.newDefaultFactory();

		_xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		_xmlInputFactory.setProperty(
			XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		_xmlInputFactory.setXMLResolver(
			(publicId, systemId, baseURI, namespace) -> {
				throw new XMLStreamException(
					"External resources are not allowed");
			});
	}

}