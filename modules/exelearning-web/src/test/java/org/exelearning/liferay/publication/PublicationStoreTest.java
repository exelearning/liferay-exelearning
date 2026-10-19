/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.publication;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Stream;

import org.exelearning.liferay.elpx.ElpxException;
import org.exelearning.liferay.test.TestZips;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * @author eXeLearning contributors
 */
public class PublicationStoreTest {

	@Before
	public void setUp() throws Exception {
		_root = temporaryFolder.newFolder(
		).toPath();

		_publicationStore = new PublicationStore(_root.resolve("store"));
	}

	@Test
	public void testCommitIgnoresConcurrentlyPublishedRevisions()
		throws Exception {

		Path first = _publicationStore.stage(
			_package(), TestZips.configuration());
		Path second = _publicationStore.stage(
			_package(), TestZips.configuration());

		Path target = _publicationStore.commit(first, 1, 2, "1.0");

		Assert.assertEquals(
			target, _publicationStore.commit(second, 1, 2, "1.0"));

		Assert.assertFalse(Files.exists(second));
		Assert.assertTrue(_publicationStore.isPublished(target));
	}

	@Test
	public void testDeleteAndPrune() throws Exception {
		for (String version : new String[] {"1.0", "1.1", "1.2"}) {
			_publicationStore.publish(
				1, 2, version, _package(), TestZips.configuration());
		}

		_publicationStore.prune(1, 2, new HashSet<>(Arrays.asList("1.2")));

		Assert.assertFalse(
			Files.exists(_publicationStore.getPath(1, 2, "1.0")));
		Assert.assertTrue(Files.exists(_publicationStore.getPath(1, 2, "1.2")));

		_publicationStore.prune(1, 99, Collections.emptySet());
		_publicationStore.delete(1, 2);
		_publicationStore.delete(1, 2);
		_publicationStore.discard(null);

		Assert.assertFalse(
			Files.exists(_publicationStore.getPath(1, 2, "1.2")));
	}

	@Test
	public void testGetPathValidatesVersions() {
		Assert.assertEquals(
			_root.resolve("store/7/8/1.10"),
			_publicationStore.getPath(7, 8, "1.10"));

		for (String version : new String[] {"../1.0", "1", "1.0/..", "a.b"}) {
			try {
				_publicationStore.getPath(7, 8, version);

				Assert.fail(version);
			}
			catch (IllegalArgumentException illegalArgumentException) {
				Assert.assertTrue(
					illegalArgumentException.getMessage(
					).contains(
						version
					));
			}
		}
	}

	@Test
	public void testPublishIsAtomicAndIdempotent() throws Exception {
		Path published = _publicationStore.publish(
			1, 2, "1.0", _package(), TestZips.configuration());

		Assert.assertTrue(_publicationStore.isPublished(published));
		Assert.assertEquals(
			published,
			_publicationStore.publish(
				1, 2, "1.0", _root.resolve("missing.elpx"),
				TestZips.configuration()));

		Assert.assertEquals(0, _stagingCount());
	}

	@Test
	public void testResolve() throws Exception {
		Path published = _publicationStore.publish(
			1, 2, "1.0", _package(), TestZips.configuration());

		Assert.assertEquals(
			published.resolve("html/page-2.html"),
			_publicationStore.resolve(published, "html/page-2.html"));
		Assert.assertNull(_publicationStore.resolve(published, "html"));
		Assert.assertNull(_publicationStore.resolve(published, "missing.js"));
		Assert.assertNull(
			_publicationStore.resolve(published, "../../../store/x"));
		Assert.assertNull(_publicationStore.resolve(published, "/etc/passwd"));
		Assert.assertNull(_publicationStore.resolve(published, ""));
	}

	@Test
	public void testStageRejectsPackagesWithoutHtmlAndCleansUp()
		throws Exception {

		Map<String, String> entries = TestZips.package_();

		entries.remove("index.html");

		Path file = TestZips.write(_root.resolve("no-html.elpx"), entries);

		try {
			_publicationStore.stage(file, TestZips.configuration());

			Assert.fail();
		}
		catch (ElpxException elpxException) {
			Assert.assertEquals(
				"the-package-has-no-html-export", elpxException.getKey());
		}

		Assert.assertEquals(0, _stagingCount());

		try {
			_publicationStore.stage(
				TestZips.write(
					_root.resolve("bad.elpx"),
					TestZips.package_("../x.html", "x")),
				TestZips.configuration());

			Assert.fail();
		}
		catch (ElpxException elpxException) {
			Assert.assertEquals(0, _stagingCount());
		}
	}

	@Rule
	public TemporaryFolder temporaryFolder = new TemporaryFolder();

	private Path _package() throws Exception {
		Path path = _root.resolve("package.elpx");

		if (!Files.exists(path)) {
			TestZips.write(path, TestZips.package_());
		}

		return path;
	}

	private int _stagingCount() throws Exception {
		Path staging = _root.resolve("store/.staging");

		if (!Files.exists(staging)) {
			return 0;
		}

		try (Stream<Path> stream = Files.list(staging)) {
			return (int)stream.count();
		}
	}

	private PublicationStore _publicationStore;
	private Path _root;

}