/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.elpx;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import org.exelearning.liferay.configuration.ExeLearningConfiguration;
import org.exelearning.liferay.test.TestZips;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * @author eXeLearning contributors
 */
public class ElpxArchiveTest {

	@Test
	public void testExtractCountsInflatedBytes() throws Exception {
		Map<String, String> entries = TestZips.package_();

		entries.put("content/big.txt", _repeat('a', 20000));

		Path file = TestZips.write(_file(), entries);

		_assertExtractRejected(
			file, _configuration("maxExtractedSize", 10000L),
			"the-extracted-package-is-too-large");
	}

	@Test
	public void testExtractRealFixture() throws Exception {
		Path target = temporaryFolder.newFolder(
		).toPath(
		).resolve(
			"out"
		);

		ElpxArchive.extract(
			TestZips.fixture("really-simple.elpx"), target,
			TestZips.configuration());

		Assert.assertTrue(Files.isRegularFile(target.resolve("index.html")));
		Assert.assertTrue(Files.isRegularFile(target.resolve("content.xml")));

		try (Stream<Path> stream = Files.list(target.resolve("html"))) {
			Assert.assertTrue(stream.count() >= 5);
		}
	}

	@Test
	public void testExtractRejectsTooManyEntries() throws Exception {
		_assertExtractRejected(
			TestZips.write(_file(), TestZips.package_()),
			_configuration("maxEntries", 2),
			"the-package-has-too-many-files-x");
	}

	@Test
	public void testExtractRejectsUnsafeEntriesWithoutEscaping()
		throws Exception {

		Path root = temporaryFolder.newFolder(
		).toPath();

		Path target = root.resolve("out");

		Path file = TestZips.write(
			_file(), TestZips.package_("../escaped.html", "x"));

		_assertRejected(
			() -> ElpxArchive.extract(file, target, TestZips.configuration()),
			"the-package-contains-an-unsafe-path-x");

		Assert.assertFalse(Files.exists(root.resolve("escaped.html")));
	}

	@Test
	public void testExtractWritesTree() throws Exception {
		Path target = temporaryFolder.newFolder(
		).toPath(
		).resolve(
			"out"
		);

		Map<String, String> entries = TestZips.package_();

		entries.put("empty/", null);

		ElpxArchive.extract(
			TestZips.write(_file(), entries), target, TestZips.configuration());

		Assert.assertEquals(
			"<html><body>Page 2</body></html>",
			new String(
				Files.readAllBytes(target.resolve("html/page-2.html")),
				StandardCharsets.UTF_8));
		Assert.assertTrue(Files.isDirectory(target.resolve("empty")));
	}

	@Test
	public void testInspectAcceptsPackageWithoutHtml() throws Exception {
		ElpxPackage elpxPackage = ElpxArchive.inspect(
			TestZips.fixture("source-only.elpx"), TestZips.configuration());

		Assert.assertFalse(elpxPackage.isPublishable());
	}

	@Test
	public void testInspectReadsMetadata() throws Exception {
		ElpxPackage elpxPackage = ElpxArchive.inspect(
			TestZips.write(_file(), TestZips.package_()),
			TestZips.configuration());

		Assert.assertEquals("Demo & test", elpxPackage.getTitle());
		Assert.assertEquals("Ana", elpxPackage.getAuthor());
		Assert.assertEquals("", elpxPackage.getDescription());
		Assert.assertEquals("es", elpxPackage.getLanguage());
		Assert.assertEquals("CC BY-SA 4.0", elpxPackage.getLicense());
		Assert.assertEquals("v4.0.5", elpxPackage.getExeLearningVersion());
		Assert.assertEquals(4, elpxPackage.getEntryCount());
		Assert.assertTrue(elpxPackage.getExtractedSize() > 0);
		Assert.assertTrue(elpxPackage.isPublishable());
		Assert.assertEquals(
			"Ana",
			elpxPackage.getProperties(
			).get(
				"pp_author"
			));
	}

	@Test
	public void testInspectRealFixtures() throws Exception {
		ElpxPackage elpxPackage = ElpxArchive.inspect(
			TestZips.fixture("multipage-interactive.elpx"),
			TestZips.configuration());

		Assert.assertEquals(
			"Un contenido de ejemplo para probar estilos y catalogación",
			elpxPackage.getTitle());
		Assert.assertEquals("Ignacio Gros", elpxPackage.getAuthor());
		Assert.assertEquals("es", elpxPackage.getLanguage());
		Assert.assertTrue(elpxPackage.isPublishable());

		elpxPackage = ElpxArchive.inspect(
			TestZips.fixture("really-simple.elpx"), TestZips.configuration());

		Assert.assertEquals(
			"Really Simple Test Project", elpxPackage.getTitle());
		Assert.assertEquals("en", elpxPackage.getLanguage());
		Assert.assertFalse(
			elpxPackage.getLicense(
			).isEmpty());
	}

	@Test
	public void testInspectRejectsCaseInsensitiveDuplicates() throws Exception {
		_assertInspectRejected(
			TestZips.package_("HTML/Page-2.html", "dup"),
			"the-package-contains-duplicate-entries-x");
	}

	@Test
	public void testInspectRejectsCompressionBombs() throws Exception {
		Map<String, String> entries = TestZips.package_();

		entries.put("bomb.txt", _repeat('0', 12 * 1024 * 1024));

		_assertRejected(
			() -> ElpxArchive.inspect(
				TestZips.write(_file(), entries), TestZips.configuration()),
			"the-package-compression-ratio-is-suspicious");
	}

	@Test
	public void testInspectRejectsForbiddenFiles() throws Exception {
		_assertInspectRejected(
			TestZips.package_("libs/shell.php", "<?php"),
			"the-package-contains-a-forbidden-file-x");
		_assertInspectRejected(
			TestZips.package_(".htaccess", "Deny"),
			"the-package-contains-a-forbidden-file-x");
	}

	@Test
	public void testInspectRejectsLargeDeclaredSize() throws Exception {
		_assertRejected(
			() -> ElpxArchive.inspect(
				TestZips.write(_file(), TestZips.package_()),
				_configuration("maxExtractedSize", 10L)),
			"the-extracted-package-is-too-large");
	}

	@Test
	public void testInspectRejectsLargeUploads() throws Exception {
		_assertRejected(
			() -> ElpxArchive.inspect(
				TestZips.write(_file(), TestZips.package_()),
				_configuration("maxUploadSize", 10L)),
			"the-file-exceeds-the-maximum-upload-size-x");
	}

	@Test
	public void testInspectRejectsLegacyAndForeignArchives() throws Exception {
		Map<String, String> legacy = new LinkedHashMap<>();

		legacy.put("contentv3.xml", "<instance/>");

		_assertInspectRejected(legacy, "legacy-elp-packages-are-not-supported");

		_assertInspectRejected(
			Collections.singletonMap("readme.txt", "x"),
			"the-file-is-not-an-exelearning-project");
	}

	@Test
	public void testInspectRejectsNonzipFiles() throws Exception {
		Path file = _file();

		Files.write(file, "not a zip".getBytes(StandardCharsets.UTF_8));

		_assertRejected(
			() -> ElpxArchive.inspect(file, TestZips.configuration()),
			"the-file-is-not-a-valid-zip-archive");
	}

	@Test
	public void testInspectRejectsTooManyEntries() throws Exception {
		_assertRejected(
			() -> ElpxArchive.inspect(
				TestZips.write(_file(), TestZips.package_()),
				_configuration("maxEntries", 3)),
			"the-package-has-too-many-files-x");
	}

	@Test
	public void testInspectRejectsUnsafePaths() throws Exception {
		for (String name :
				new String[] {
					"../../evil.html", "/etc/cron.d/x", "C:/x.html",
					"a\\..\\..\\b.html"
				}) {

			_assertInspectRejected(
				TestZips.package_(name, "x"),
				"the-package-contains-an-unsafe-path-x");
		}
	}

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private void _assertExtractRejected(
			Path file, ExeLearningConfiguration exeLearningConfiguration,
			String key)
		throws Exception {

		Path target = temporaryFolder.newFolder(
		).toPath(
		).resolve(
			"out"
		);

		_assertRejected(
			() -> ElpxArchive.extract(file, target, exeLearningConfiguration),
			key);
	}

	private void _assertInspectRejected(Map<String, String> entries, String key)
		throws Exception {

		Path file = TestZips.write(_file(), entries);

		_assertRejected(
			() -> ElpxArchive.inspect(file, TestZips.configuration()), key);
	}

	private void _assertRejected(ThrowingRunnable throwingRunnable, String key)
		throws Exception {

		try {
			throwingRunnable.run();

			Assert.fail("Expected " + key);
		}
		catch (ElpxException elpxException) {
			Assert.assertEquals(key, elpxException.getKey());
		}
	}

	private ExeLearningConfiguration _configuration(String name, Object value) {
		Map<String, Object> overrides = new HashMap<>();

		overrides.put(name, value);

		return TestZips.configuration(overrides);
	}

	private Path _file() throws Exception {
		return temporaryFolder.newFile(
		).toPath();
	}

	private String _repeat(char c, int count) {
		char[] chars = new char[count];

		Arrays.fill(chars, c);

		return new String(chars);
	}

	private interface ThrowingRunnable {

		public void run() throws Exception;

	}

}