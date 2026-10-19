/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.test;

import com.liferay.petra.string.StringBundler;

import java.io.IOException;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.exelearning.liferay.configuration.ExeLearningConfiguration;

/**
 * Builders for test archives and configuration.
 *
 * @author eXeLearning contributors
 */
public class TestZips {

	public static final String CONTENT_XML = StringBundler.concat(
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n",
		"<!DOCTYPE ode SYSTEM \"content.dtd\">\n",
		"<ode xmlns=\"http://www.intef.es/xsd/ode\" version=\"2.0\">",
		"<odeResources><odeResource><key>exe_version</key><value>v4.0.5",
		"</value></odeResource></odeResources><odeProperties><odeProperty>",
		"<key>pp_title</key><value>Demo &amp; test</value></odeProperty>",
		"<odeProperty><key>pp_author</key><value>Ana</value></odeProperty>",
		"<odeProperty><key>pp_lang</key><value>es</value></odeProperty>",
		"<odeProperty><key>pp_license</key><value>CC BY-SA 4.0</value>",
		"</odeProperty></odeProperties><odeNavStructures><odeNavStructure>",
		"<odePageId>p1</odePageId></odeNavStructure></odeNavStructures></ode>");

	public static ExeLearningConfiguration configuration() {
		return configuration(new HashMap<>());
	}

	public static ExeLearningConfiguration configuration(
		Map<String, Object> overrides) {

		Map<String, Object> values = new HashMap<>();

		values.put("allowedEditorParentOrigins", new String[0]);
		values.put("contentURLLifetimeMinutes", 480);
		values.put("editorEnabled", true);
		values.put("maxCompressionRatio", 200);
		values.put("maxEntries", 10000);
		values.put("maxExtractedSize", 1024L * 1024 * 1024);
		values.put("maxUploadSize", 256L * 1024 * 1024);

		values.putAll(overrides);

		InvocationHandler invocationHandler = (proxy, method, args) -> {
			if (method.getDeclaringClass() == Object.class) {
				return method.invoke(values, args);
			}

			return values.get(method.getName());
		};

		return (ExeLearningConfiguration)Proxy.newProxyInstance(
			ExeLearningConfiguration.class.getClassLoader(),
			new Class<?>[] {ExeLearningConfiguration.class}, invocationHandler);
	}

	public static Path fixture(String name) {
		Path path = Paths.get("../../tests/fixtures/valid", name);

		if (!Files.exists(path)) {
			throw new IllegalStateException("Missing fixture " + path);
		}

		return path;
	}

	public static Map<String, String> package_(String... namesAndContents) {
		Map<String, String> entries = new LinkedHashMap<>();

		entries.put("content.xml", CONTENT_XML);
		entries.put("index.html", "<html><body>Home</body></html>");
		entries.put("html/page-2.html", "<html><body>Page 2</body></html>");
		entries.put("content/css/base.css", "body{}");

		for (int i = 0; i < namesAndContents.length; i += 2) {
			entries.put(namesAndContents[i], namesAndContents[i + 1]);
		}

		return entries;
	}

	public static void setField(Object target, String name, Object value) {
		Class<?> clazz = target.getClass();

		while (clazz != null) {
			try {
				Field field = clazz.getDeclaredField(name);

				field.setAccessible(true);

				field.set(target, value);

				return;
			}
			catch (NoSuchFieldException noSuchFieldException) {
				clazz = clazz.getSuperclass();
			}
			catch (IllegalAccessException illegalAccessException) {
				throw new IllegalStateException(illegalAccessException);
			}
		}

		throw new IllegalArgumentException("No field " + name);
	}

	public static Path write(Path file, Map<String, String> entries)
		throws IOException {

		try (ZipOutputStream zipOutputStream = new ZipOutputStream(
				Files.newOutputStream(file))) {

			for (Map.Entry<String, String> entry : entries.entrySet()) {
				zipOutputStream.putNextEntry(new ZipEntry(entry.getKey()));

				if (entry.getValue() != null) {
					zipOutputStream.write(
						entry.getValue(
						).getBytes(
							StandardCharsets.UTF_8
						));
				}

				zipOutputStream.closeEntry();
			}
		}

		return file;
	}

	/**
	 * Writes a stored (uncompressed) entry whose header declares a size, so
	 * tests can produce archives whose central directory lies.
	 */
	public static Path writeStored(Path file, String name, byte[] bytes)
		throws IOException {

		try (ZipOutputStream zipOutputStream = new ZipOutputStream(
				Files.newOutputStream(file))) {

			ZipEntry zipEntry = new ZipEntry(name);

			CRC32 crc32 = new CRC32();

			crc32.update(bytes);

			zipEntry.setCompressedSize(bytes.length);
			zipEntry.setCrc(crc32.getValue());
			zipEntry.setMethod(ZipEntry.STORED);
			zipEntry.setSize(bytes.length);

			zipOutputStream.putNextEntry(zipEntry);
			zipOutputStream.write(bytes);
			zipOutputStream.closeEntry();
		}

		return file;
	}

}