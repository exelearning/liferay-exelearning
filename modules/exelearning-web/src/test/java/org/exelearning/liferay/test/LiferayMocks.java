/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.test;

import com.liferay.expando.kernel.model.ExpandoBridge;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.FileVersion;
import com.liferay.portal.kernel.repository.model.Folder;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.Serializable;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * Mockito doubles for Documents and Media objects with real state, so tests
 * verify behavior instead of interactions.
 *
 * @author eXeLearning contributors
 */
public class LiferayMocks {

	public static final long COMPANY_ID = 10L;

	public static final long FOLDER_ID = 500L;

	public static final long GROUP_ID = 20L;

	public static ExpandoBridge expandoBridge(
		Map<String, Serializable> values) {

		ExpandoBridge expandoBridge = Mockito.mock(ExpandoBridge.class);

		Set<String> columns = new HashSet<>();

		Mockito.when(
			expandoBridge.hasAttribute(ArgumentMatchers.anyString())
		).thenAnswer(
			invocation -> columns.contains(invocation.getArgument(0))
		);

		try {
			Mockito.doAnswer(
				invocation -> columns.add(invocation.getArgument(0))
			).when(
				expandoBridge
			).addAttribute(
				ArgumentMatchers.anyString(), ArgumentMatchers.anyInt(),
				ArgumentMatchers.any(Serializable.class),
				ArgumentMatchers.anyBoolean()
			);
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}

		Mockito.doAnswer(
			invocation -> values.put(
				invocation.getArgument(0), invocation.getArgument(1))
		).when(
			expandoBridge
		).setAttribute(
			ArgumentMatchers.anyString(),
			ArgumentMatchers.any(Serializable.class),
			ArgumentMatchers.anyBoolean()
		);

		Mockito.when(
			expandoBridge.getAttribute(
				ArgumentMatchers.anyString(), ArgumentMatchers.anyBoolean())
		).thenAnswer(
			invocation -> values.get(invocation.getArgument(0))
		);

		Mockito.when(
			expandoBridge.getAttributes(
				ArgumentMatchers.<Collection<String>>any(),
				ArgumentMatchers.anyBoolean())
		).thenAnswer(
			invocation -> {
				Map<String, Serializable> attributes = new HashMap<>();

				for (String name :
						invocation.<Collection<String>>getArgument(0)) {

					attributes.put(name, values.get(name));
				}

				return attributes;
			}
		);

		return expandoBridge;
	}

	public static FileEntry fileEntry(
			long fileEntryId, ExpandoBridge expandoBridge,
			FileVersion... fileVersions)
		throws Exception {

		FileEntry fileEntry = Mockito.mock(FileEntry.class);

		List<FileVersion> versions = new ArrayList<>();

		for (FileVersion fileVersion : fileVersions) {
			versions.add(fileVersion);

			String fileVersionVersion = fileVersion.getVersion();

			Mockito.when(
				fileVersion.getFileEntry()
			).thenReturn(
				fileEntry
			);

			Mockito.when(
				fileEntry.getFileVersion(fileVersionVersion)
			).thenReturn(
				fileVersion
			);
		}

		FileVersion latest = fileVersions[fileVersions.length - 1];

		long size = latest.getSize();
		String version = latest.getVersion();

		Mockito.when(
			fileEntry.getCompanyId()
		).thenReturn(
			COMPANY_ID
		);

		Mockito.when(
			fileEntry.getDescription()
		).thenReturn(
			"Description"
		);

		Mockito.when(
			fileEntry.getExpandoBridge()
		).thenReturn(
			expandoBridge
		);

		Mockito.when(
			fileEntry.getFileEntryId()
		).thenReturn(
			fileEntryId
		);

		Mockito.when(
			fileEntry.getFileName()
		).thenReturn(
			"demo.elpx"
		);

		Mockito.when(
			fileEntry.getFileVersions(WorkflowConstants.STATUS_ANY)
		).thenReturn(
			versions
		);

		Mockito.when(
			fileEntry.getFolderId()
		).thenReturn(
			FOLDER_ID
		);

		Mockito.when(
			fileEntry.getGroupId()
		).thenReturn(
			GROUP_ID
		);

		Mockito.when(
			fileEntry.getModifiedDate()
		).thenReturn(
			new Date(0)
		);

		Mockito.when(
			fileEntry.getSize()
		).thenReturn(
			size
		);

		Mockito.when(
			fileEntry.getTitle()
		).thenReturn(
			"Demo"
		);

		Mockito.when(
			fileEntry.getVersion()
		).thenReturn(
			version
		);

		return fileEntry;
	}

	public static FileVersion fileVersion(String version, Path content)
		throws Exception {

		return fileVersion(version, content, WorkflowConstants.STATUS_APPROVED);
	}

	public static FileVersion fileVersion(
			String version, Path content, int status)
		throws Exception {

		FileVersion fileVersion = Mockito.mock(FileVersion.class);

		byte[] bytes = Files.readAllBytes(content);

		Mockito.when(
			fileVersion.getChangeLog()
		).thenReturn(
			"change"
		);

		Mockito.when(
			fileVersion.getContentStream(ArgumentMatchers.anyBoolean())
		).thenAnswer(
			invocation -> new ByteArrayInputStream(bytes)
		);

		Mockito.when(
			fileVersion.getCreateDate()
		).thenReturn(
			new Date(1000)
		);

		Mockito.when(
			fileVersion.getFileName()
		).thenReturn(
			"demo.elpx"
		);

		Mockito.when(
			fileVersion.getSize()
		).thenReturn(
			(long)bytes.length
		);

		Mockito.when(
			fileVersion.getStatus()
		).thenReturn(
			status
		);

		Mockito.when(
			fileVersion.getUserName()
		).thenReturn(
			"Ana"
		);

		Mockito.when(
			fileVersion.getVersion()
		).thenReturn(
			version
		);

		return fileVersion;
	}

	public static Folder folder() {
		Folder folder = Mockito.mock(Folder.class);

		Mockito.when(
			folder.getFolderId()
		).thenReturn(
			FOLDER_ID
		);

		return folder;
	}

	public static Path zip(Path directory, String name) throws IOException {
		return TestZips.write(directory.resolve(name), TestZips.package_());
	}

}