/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.elpx;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.exelearning.liferay.configuration.ExeLearningConfiguration;

/**
 * Inspects and extracts <code>.elpx</code> packages, treating them as
 * untrusted input. Inspection walks the central directory and rejects the
 * whole package on the first unsafe entry; extraction re-applies every rule
 * and counts the bytes actually inflated, so lying size headers cannot bypass
 * the limits.
 *
 * @author eXeLearning contributors
 */
public class ElpxArchive {

	public static final String CONTENT_XML = "content.xml";

	public static final String INDEX_HTML = "index.html";

	public static final String LEGACY_CONTENT_XML = "contentv3.xml";

	public static void extract(
			Path file, Path directory,
			ExeLearningConfiguration exeLearningConfiguration)
		throws ElpxException, IOException {

		Path root = directory.toAbsolutePath(
		).normalize();

		Files.createDirectories(root);

		long total = 0;

		try (ZipFile zipFile = new ZipFile(
				file.toFile(), StandardCharsets.UTF_8)) {

			Enumeration<? extends ZipEntry> enumeration = zipFile.entries();

			byte[] buffer = new byte[_BUFFER_SIZE];
			int count = 0;

			while (enumeration.hasMoreElements()) {
				ZipEntry zipEntry = enumeration.nextElement();

				if (++count > exeLearningConfiguration.maxEntries()) {
					throw new ElpxException(
						"the-package-has-too-many-files-x",
						exeLearningConfiguration.maxEntries());
				}

				String name = _check(zipEntry);

				Path path = root.resolve(
					name
				).normalize();

				if (!path.startsWith(root) || path.equals(root)) {
					throw new ElpxException(
						"the-package-contains-an-unsafe-path-x",
						zipEntry.getName());
				}

				if (zipEntry.isDirectory()) {
					Files.createDirectories(path);

					continue;
				}

				Files.createDirectories(path.getParent());

				try (InputStream inputStream = zipFile.getInputStream(zipEntry);
					OutputStream outputStream = Files.newOutputStream(
						path, StandardOpenOption.CREATE_NEW,
						StandardOpenOption.WRITE)) {

					int read;

					while ((read = inputStream.read(buffer)) != -1) {
						total += read;

						if (total >
								exeLearningConfiguration.maxExtractedSize()) {

							throw new ElpxException(
								"the-extracted-package-is-too-large");
						}

						outputStream.write(buffer, 0, read);
					}
				}
				catch (FileAlreadyExistsException fileAlreadyExistsException) {
					throw new ElpxException(
						"the-package-contains-duplicate-entries-x",
						zipEntry.getName());
				}
			}
		}
		catch (ZipException zipException) {
			throw new ElpxException(
				"the-file-is-not-a-valid-zip-archive", zipException);
		}

		_checkRatio(file, total, exeLearningConfiguration);
	}

	public static ElpxPackage inspect(
			Path file, ExeLearningConfiguration exeLearningConfiguration)
		throws ElpxException, IOException {

		if (Files.size(file) > exeLearningConfiguration.maxUploadSize()) {
			throw new ElpxException(
				"the-file-exceeds-the-maximum-upload-size-x",
				exeLearningConfiguration.maxUploadSize());
		}

		try (ZipFile zipFile = new ZipFile(
				file.toFile(), StandardCharsets.UTF_8)) {

			Enumeration<? extends ZipEntry> enumeration = zipFile.entries();
			Map<String, ZipEntry> entries = new HashMap<>();
			Set<String> keys = new HashSet<>();

			long declaredSize = 0;

			while (enumeration.hasMoreElements()) {
				ZipEntry zipEntry = enumeration.nextElement();

				if (keys.size() >= exeLearningConfiguration.maxEntries()) {
					throw new ElpxException(
						"the-package-has-too-many-files-x",
						exeLearningConfiguration.maxEntries());
				}

				String name = _check(zipEntry);

				String key = name;

				if (key.endsWith("/")) {
					key = key.substring(0, key.length() - 1);
				}

				if (!keys.add(key.toLowerCase(Locale.ROOT))) {
					throw new ElpxException(
						"the-package-contains-duplicate-entries-x",
						zipEntry.getName());
				}

				entries.put(name, zipEntry);

				if (zipEntry.getSize() > 0) {
					declaredSize += zipEntry.getSize();
				}

				if (declaredSize >
						exeLearningConfiguration.maxExtractedSize()) {

					throw new ElpxException(
						"the-extracted-package-is-too-large");
				}
			}

			_checkRatio(file, declaredSize, exeLearningConfiguration);

			ZipEntry contentXml = entries.get(CONTENT_XML);

			if (contentXml == null) {
				if (entries.containsKey(LEGACY_CONTENT_XML)) {
					throw new ElpxException(
						"legacy-elp-packages-are-not-supported");
				}

				throw new ElpxException(
					"the-file-is-not-an-exelearning-project");
			}

			Map<String, String> properties;

			try (InputStream inputStream = zipFile.getInputStream(contentXml)) {
				properties = ElpxMetadataParser.parse(inputStream);
			}

			return new ElpxPackage(
				properties, entries.containsKey(INDEX_HTML), keys.size(),
				declaredSize);
		}
		catch (ZipException zipException) {
			throw new ElpxException(
				"the-file-is-not-a-valid-zip-archive", zipException);
		}
	}

	private static String _check(ZipEntry zipEntry) throws ElpxException {
		String name = ElpxEntryNames.normalize(zipEntry.getName());

		if (!zipEntry.isDirectory() && ElpxEntryNames.isForbidden(name)) {
			throw new ElpxException(
				"the-package-contains-a-forbidden-file-x", zipEntry.getName());
		}

		return name;
	}

	private static void _checkRatio(
			Path file, long extractedSize,
			ExeLearningConfiguration exeLearningConfiguration)
		throws ElpxException, IOException {

		long size = Math.max(Files.size(file), 1);

		if ((extractedSize > _RATIO_THRESHOLD) &&
			((extractedSize / size) >
				exeLearningConfiguration.maxCompressionRatio())) {

			throw new ElpxException(
				"the-package-compression-ratio-is-suspicious");
		}
	}

	private static final int _BUFFER_SIZE = 8192;

	/**
	 * Packages that expand to less than this are never reported as
	 * compression bombs; tiny text files legitimately compress very well.
	 */
	private static final long _RATIO_THRESHOLD = 10L * 1024 * 1024;

}