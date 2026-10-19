/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.publication;

import java.io.IOException;

import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.Collection;
import java.util.UUID;
import java.util.regex.Pattern;

import org.exelearning.liferay.configuration.ExeLearningConfiguration;
import org.exelearning.liferay.elpx.ElpxArchive;
import org.exelearning.liferay.elpx.ElpxEntryNames;
import org.exelearning.liferay.elpx.ElpxException;

/**
 * Filesystem cache of extracted HTML5 exports, one immutable directory per
 * resource revision: <code>{root}/{companyId}/{fileEntryId}/{version}</code>.
 * Documents and Media remains the source of truth; any directory can be
 * rebuilt from the stored package. New revisions are extracted into a private
 * staging directory, verified and then moved into place atomically, so a
 * reader never sees a partially extracted publication.
 *
 * @author eXeLearning contributors
 */
public class PublicationStore {

	public PublicationStore(Path root) {
		_root = root.toAbsolutePath(
		).normalize();
	}

	/**
	 * Removes every cached revision of a resource except the given ones.
	 */
	public void prune(long companyId, long fileEntryId, Collection<String> keep)
		throws IOException {

		Path resourcePath = _resourcePath(companyId, fileEntryId);

		if (!Files.isDirectory(resourcePath)) {
			return;
		}

		try (DirectoryStream<Path> directoryStream = Files.newDirectoryStream(
				resourcePath)) {

			for (Path path : directoryStream) {
				if (!keep.contains(
						path.getFileName(
						).toString())) {

					_delete(path);
				}
			}
		}
	}

	/**
	 * Moves a verified staging directory into place as the given revision. If
	 * the revision was already published by a concurrent request the staging
	 * directory is discarded.
	 */
	public Path commit(
			Path staging, long companyId, long fileEntryId, String version)
		throws IOException {

		Path target = getPath(companyId, fileEntryId, version);

		try {
			if (!isPublished(target)) {
				Files.createDirectories(target.getParent());

				_move(staging, target);
			}
		}
		finally {
			discard(staging);
		}

		return target;
	}

	public void discard(Path staging) throws IOException {
		if (staging != null) {
			_delete(staging);
		}
	}

	/**
	 * Extracts and publishes a package as the given revision unless it is
	 * already published. Returns the published directory.
	 */
	public Path publish(
			long companyId, long fileEntryId, String version, Path packageFile,
			ExeLearningConfiguration exeLearningConfiguration)
		throws ElpxException, IOException {

		Path target = getPath(companyId, fileEntryId, version);

		if (isPublished(target)) {
			return target;
		}

		return commit(
			stage(packageFile, exeLearningConfiguration), companyId,
			fileEntryId, version);
	}

	/**
	 * Extracts a package into a private staging directory and verifies that
	 * it contains the HTML5 export. Nothing is visible to readers until
	 * {@link #commit(Path, long, long, String)} succeeds.
	 */
	public Path stage(
			Path packageFile, ExeLearningConfiguration exeLearningConfiguration)
		throws ElpxException, IOException {

		Path staging = _root.resolve(
			_STAGING
		).resolve(
			UUID.randomUUID(
			).toString()
		);

		try {
			ElpxArchive.extract(packageFile, staging, exeLearningConfiguration);

			if (!Files.isRegularFile(
					staging.resolve(ElpxArchive.INDEX_HTML),
					LinkOption.NOFOLLOW_LINKS)) {

				throw new ElpxException("the-package-has-no-html-export");
			}

			return staging;
		}
		catch (ElpxException | IOException | RuntimeException exception) {
			_delete(staging);

			throw exception;
		}
	}

	public void delete(long companyId, long fileEntryId) throws IOException {
		_delete(_resourcePath(companyId, fileEntryId));
	}

	public Path getPath(long companyId, long fileEntryId, String version) {
		if (!_versionPattern.matcher(
				version
			).matches()) {

			throw new IllegalArgumentException("Invalid version " + version);
		}

		return _resourcePath(
			companyId, fileEntryId
		).resolve(
			version
		);
	}

	public Path getRoot() {
		return _root;
	}

	public boolean isPublished(Path path) {
		return Files.isRegularFile(
			path.resolve(ElpxArchive.INDEX_HTML), LinkOption.NOFOLLOW_LINKS);
	}

	/**
	 * Resolves a relative URL path inside a published revision. Returns
	 * <code>null</code> for unsafe paths, missing files and anything that is
	 * not a regular file.
	 */
	public Path resolve(Path publication, String relativePath) {
		String name;

		try {
			name = ElpxEntryNames.normalize(relativePath);
		}
		catch (ElpxException elpxException) {
			return null;
		}

		Path path = publication.resolve(
			name
		).normalize();

		if (!path.startsWith(publication) ||
			!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {

			return null;
		}

		return path;
	}

	private void _delete(Path path) throws IOException {
		if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
			return;
		}

		Files.walkFileTree(
			path,
			new SimpleFileVisitor<Path>() {

				@Override
				public FileVisitResult postVisitDirectory(
						Path directory, IOException ioException)
					throws IOException {

					if (ioException != null) {
						throw ioException;
					}

					Files.deleteIfExists(directory);

					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(
						Path file, BasicFileAttributes basicFileAttributes)
					throws IOException {

					Files.deleteIfExists(file);

					return FileVisitResult.CONTINUE;
				}

			});
	}

	private void _move(Path source, Path target) throws IOException {
		try {
			Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException
					atomicMoveNotSupportedException) {

			throw new IOException(
				"The publication directory must be on one file system",
				atomicMoveNotSupportedException);
		}
		catch (DirectoryNotEmptyException | FileAlreadyExistsException
					exception) {

			// Another request published the same immutable revision first

			if (!isPublished(target)) {
				throw exception;
			}
		}
	}

	private Path _resourcePath(long companyId, long fileEntryId) {
		return _root.resolve(
			String.valueOf(companyId)
		).resolve(
			String.valueOf(fileEntryId)
		);
	}

	private static final String _STAGING = ".staging";

	private static final Pattern _versionPattern = Pattern.compile(
		"\\d{1,6}\\.\\d{1,6}");

	private final Path _root;

}