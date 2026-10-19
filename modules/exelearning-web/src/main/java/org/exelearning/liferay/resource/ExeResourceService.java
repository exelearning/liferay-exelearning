/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.resource;

import com.liferay.document.library.kernel.exception.DuplicateFileEntryException;
import com.liferay.document.library.kernel.exception.DuplicateFolderNameException;
import com.liferay.document.library.kernel.exception.NoSuchFileEntryException;
import com.liferay.document.library.kernel.exception.NoSuchFolderException;
import com.liferay.document.library.kernel.model.DLFolder;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.model.DLVersionNumberIncrease;
import com.liferay.document.library.kernel.service.DLAppLocalService;
import com.liferay.document.library.kernel.service.DLAppService;
import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.lock.DuplicateLockException;
import com.liferay.portal.kernel.lock.LockManager;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.Company;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.model.GroupConstants;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.FileVersion;
import com.liferay.portal.kernel.repository.model.Folder;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.ActionKeys;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.security.permission.resource.ModelResourcePermission;
import com.liferay.portal.kernel.security.permission.resource.PortletResourcePermission;
import com.liferay.portal.kernel.service.CompanyLocalService;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.permission.ModelPermissionsFactory;
import com.liferay.portal.kernel.util.Portal;
import com.liferay.portal.kernel.util.PropsKeys;
import com.liferay.portal.kernel.util.PropsUtil;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;
import com.liferay.portal.kernel.workflow.WorkflowConstants;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import java.security.Key;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.exelearning.liferay.configuration.ExeLearningConfiguration;
import org.exelearning.liferay.constants.ExeLearningPortletKeys;
import org.exelearning.liferay.content.ContentToken;
import org.exelearning.liferay.elpx.ElpxArchive;
import org.exelearning.liferay.elpx.ElpxException;
import org.exelearning.liferay.elpx.ElpxPackage;
import org.exelearning.liferay.publication.PublicationStore;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;

/**
 * Application service for eXeLearning resources. Packages are stored as
 * Documents and Media file entries in a per-site folder, so Liferay's own
 * permissions, versioning, trash-independent deletion and site scoping apply.
 * Every public method runs with the current user's permission checker and
 * calls the permission-checking <code>DLAppService</code>.
 *
 * <p>
 * Revision lifecycle: validate the package, extract and verify it in a
 * staging directory, persist the new Documents and Media version, move the
 * staging directory into place, and only then switch the published pointer.
 * A failure at any step leaves the previously published revision untouched.
 * </p>
 *
 * @author eXeLearning contributors
 */
@Component(
	configurationPid = "org.exelearning.liferay.configuration.ExeLearningConfiguration",
	service = ExeResourceService.class
)
public class ExeResourceService {

	public static final String CONTENT_SERVLET_PATH = "/exelearning-content";

	public static final String FOLDER_NAME = "eXeLearning";

	public static final String MIME_TYPE = "application/zip";

	public ExeResource addResource(
			long groupId, String fileName, InputStream inputStream)
		throws IOException, PortalException {

		if (!canAddResource(groupId)) {
			throw new PrincipalException.MustHavePermission(
				_getPermissionChecker(), Folder.class.getName(), groupId,
				ActionKeys.ADD_DOCUMENT);
		}

		String sourceFileName = sanitizeFileName(fileName);

		Path file = _copy(inputStream);

		try {
			ElpxPackage elpxPackage = ElpxArchive.inspect(
				file, _exeLearningConfiguration);

			Path staging = _stage(file, elpxPackage);

			try {
				FileEntry fileEntry = _addFileEntry(
					groupId, _getOrAddFolder(groupId), sourceFileName,
					elpxPackage, file);

				_resourceAttributes.setMetadata(fileEntry, elpxPackage);

				if (staging != null) {
					_publicationStore.commit(
						staging, fileEntry.getCompanyId(),
						fileEntry.getFileEntryId(), fileEntry.getVersion());
				}

				return _toExeResource(fileEntry, true);
			}
			finally {
				_publicationStore.discard(staging);
			}
		}
		finally {
			Files.deleteIfExists(file);
		}
	}

	/**
	 * Returns whether the current user may add resources to a site. Authoring
	 * requires the eXeLearning application's "Access in Site Administration"
	 * permission, because Documents and Media lets every site member add
	 * documents by default, and Documents and Media's own add permission.
	 */
	public boolean canAddResource(long groupId) throws PortalException {
		PermissionChecker permissionChecker = _getPermissionChecker();

		if (!permissionChecker.hasPermission(
				groupId, ExeLearningPortletKeys.ADMIN,
				ExeLearningPortletKeys.ADMIN,
				ActionKeys.ACCESS_IN_CONTROL_PANEL)) {

			return false;
		}

		Folder folder = _fetchFolder(groupId);

		if (folder != null) {
			return _folderModelResourcePermission.contains(
				permissionChecker, folder, ActionKeys.ADD_DOCUMENT);
		}

		return _portletResourcePermission.contains(
			permissionChecker, groupId, ActionKeys.ADD_DOCUMENT);
	}

	public void deleteResource(long fileEntryId)
		throws IOException, PortalException {

		FileEntry fileEntry = _getFileEntry(fileEntryId, ActionKeys.DELETE);

		_dlAppService.deleteFileEntry(fileEntryId);

		_publicationStore.delete(fileEntry.getCompanyId(), fileEntryId);
	}

	public ExeLearningConfiguration getConfiguration() {
		return _exeLearningConfiguration;
	}

	/**
	 * Returns the signed content path of a revision. Callers must have checked
	 * that the current user may read that revision.
	 */
	public String getContentPath(
			long companyId, long fileEntryId, String version,
			long nowEpochSeconds)
		throws PortalException {

		long lifetime =
			_exeLearningConfiguration.contentURLLifetimeMinutes() * 60L;

		// Round up to the hour so URLs stay stable and cacheable for a while

		long expires = (((nowEpochSeconds + lifetime) / 3600) + 1) * 3600;

		String token = ContentToken.issue(
			getTokenKey(companyId), companyId, fileEntryId, version, expires);

		return _portal.getPathModule() + CONTENT_SERVLET_PATH + "/" + token +
			"/" + ElpxArchive.INDEX_HTML;
	}

	/**
	 * Returns a stored revision for a verified content token. Performs no
	 * permission check.
	 */
	public FileVersion getFileVersion(
			long companyId, long fileEntryId, String version)
		throws PortalException {

		FileEntry fileEntry = _dlAppLocalService.getFileEntry(fileEntryId);

		if (fileEntry.getCompanyId() != companyId) {
			throw new NoSuchFileEntryException(
				"No file entry " + fileEntryId + " in company " + companyId);
		}

		return fileEntry.getFileVersion(version);
	}

	/**
	 * Returns a revision of a package for download. Unpublished revisions are
	 * only available to users who can update the resource.
	 */
	public FileVersion getPackage(long fileEntryId, String version)
		throws PortalException {

		FileEntry fileEntry = _getFileEntry(fileEntryId, ActionKeys.VIEW);

		String publishedVersion = _resourceAttributes.getPublishedVersion(
			fileEntry);

		if (Validator.isNull(version)) {
			version = publishedVersion;
		}

		if (Validator.isNull(version) || !version.equals(publishedVersion)) {
			_fileEntryModelResourcePermission.check(
				_getPermissionChecker(), fileEntry, ActionKeys.UPDATE);

			if (Validator.isNull(version)) {
				version = fileEntry.getVersion();
			}
		}

		return fileEntry.getFileVersion(version);
	}

	/**
	 * Returns the extracted publication of a revision, rebuilding it from
	 * Documents and Media when the cache was cleared. Used by the content
	 * servlet after verifying a content token; performs no permission check.
	 */
	public Path getPublication(long companyId, long fileEntryId, String version)
		throws IOException, PortalException {

		Path path = _publicationStore.getPath(companyId, fileEntryId, version);

		if (_publicationStore.isPublished(path)) {
			return path;
		}

		FileVersion fileVersion = getFileVersion(
			companyId, fileEntryId, version);

		return _ensurePublication(fileVersion.getFileEntry(), fileVersion);
	}

	public PublicationStore getPublicationStore() {
		return _publicationStore;
	}

	/**
	 * Returns a published resource the current user can view, or
	 * <code>null</code> when it does not exist, is not visible or is not
	 * published.
	 */
	public ExeResource getPublishedResource(long fileEntryId)
		throws PortalException {

		try {
			ExeResource exeResource = getResource(fileEntryId);

			if (exeResource.isPublished()) {
				return exeResource;
			}
		}
		catch (NoSuchFileEntryException noSuchFileEntryException) {
			if (_log.isDebugEnabled()) {
				_log.debug(noSuchFileEntryException);
			}
		}

		return null;
	}

	public ExeResource getResource(long fileEntryId) throws PortalException {
		ExeResource exeResource = _toExeResource(
			_getFileEntry(fileEntryId, ActionKeys.VIEW), true);

		if (!exeResource.isPublished() && !exeResource.canUpdate()) {
			throw new NoSuchFileEntryException(
				"Resource " + fileEntryId + " is not published");
		}

		return exeResource;
	}

	public List<ExeResource> getResources(long groupId, String keywords)
		throws PortalException {

		Folder folder = _fetchFolder(groupId);

		if (folder == null) {
			return Collections.emptyList();
		}

		String query = StringUtil.toLowerCase(
			StringUtil.trim(keywords), Locale.ROOT);

		List<ExeResource> exeResources = new ArrayList<>();

		// ponytail: in-memory filter, use the search index when a site holds
		// thousands of packages

		for (FileEntry fileEntry :
				_dlAppService.getFileEntries(groupId, folder.getFolderId())) {

			ExeResource exeResource = _toExeResource(fileEntry, false);

			if ((exeResource.isPublished() || exeResource.canUpdate()) &&
				_matches(exeResource, query)) {

				exeResources.add(exeResource);
			}
		}

		exeResources.sort(
			Comparator.comparing(
				exeResource -> StringUtil.toLowerCase(exeResource.getTitle())));

		return exeResources;
	}

	public byte[] getTokenKey(long companyId) throws PortalException {
		byte[] key = _tokenKeys.get(companyId);

		if (key == null) {
			Company company = _companyLocalService.getCompany(companyId);

			Key keyObj = company.getKeyObj();

			if (keyObj != null) {
				key = keyObj.getEncoded();
			}
			else {
				key = company.getKey(
				).getBytes(
					StandardCharsets.UTF_8
				);
			}

			_tokenKeys.put(companyId, key);
		}

		return key.clone();
	}

	public ExeResource publish(long fileEntryId, String version)
		throws IOException, PortalException {

		FileEntry fileEntry = _getFileEntry(fileEntryId, ActionKeys.UPDATE);

		if (Validator.isNull(version)) {
			version = fileEntry.getVersion();
		}

		FileVersion fileVersion = fileEntry.getFileVersion(version);

		if (fileVersion.getStatus() != WorkflowConstants.STATUS_APPROVED) {
			throw new ElpxException(
				"only-approved-versions-can-be-published", version);
		}

		_ensurePublication(fileEntry, fileVersion);

		_resourceAttributes.setPublishedVersion(fileEntry, version);

		_prune(fileEntry, version);

		return _toExeResource(fileEntry, true);
	}

	/**
	 * Checks that the current user may preview a revision and returns its
	 * signed content path.
	 */
	public String preview(
			long fileEntryId, String version, long nowEpochSeconds)
		throws IOException, PortalException {

		FileEntry fileEntry = _getFileEntry(fileEntryId, ActionKeys.UPDATE);

		if (Validator.isNull(version)) {
			version = fileEntry.getVersion();
		}

		_ensurePublication(fileEntry, fileEntry.getFileVersion(version));

		return getContentPath(
			fileEntry.getCompanyId(), fileEntryId, version, nowEpochSeconds);
	}

	public ExeResource unpublish(long fileEntryId) throws PortalException {
		FileEntry fileEntry = _getFileEntry(fileEntryId, ActionKeys.UPDATE);

		_resourceAttributes.setPublishedVersion(fileEntry, "");

		return _toExeResource(fileEntry, true);
	}

	/**
	 * Stores a new revision of a package. The update is rejected unless
	 * <code>expectedVersion</code> is still the latest revision, so a stale
	 * editor session can never overwrite someone else's changes. The published
	 * revision does not change until the resource is published again.
	 */
	public ExeResource updatePackage(
			long fileEntryId, String expectedVersion, String fileName,
			InputStream inputStream)
		throws IOException, PortalException {

		FileEntry fileEntry = _getFileEntry(fileEntryId, ActionKeys.UPDATE);

		sanitizeFileName(fileName);

		String className = ExeResourceService.class.getName();
		String key = String.valueOf(fileEntryId);
		String owner = UUID.randomUUID(
		).toString();

		try {
			_lockManager.lock(
				_getPermissionChecker().getUserId(), className, key, owner,
				false, _LOCK_EXPIRATION_TIME);
		}
		catch (DuplicateLockException duplicateLockException) {
			throw new ResourceConflictException(
				"another-save-of-this-resource-is-in-progress",
				fileEntry.getVersion());
		}

		try {
			fileEntry = _dlAppService.getFileEntry(fileEntryId);

			if (!Objects.equals(fileEntry.getVersion(), expectedVersion)) {
				throw new ResourceConflictException(
					"the-resource-was-changed-by-someone-else",
					fileEntry.getVersion());
			}

			return _updatePackage(fileEntry, inputStream);
		}
		finally {
			_lockManager.unlock(className, key, owner);
		}
	}

	/**
	 * Returns the file name a package is stored under, or throws when it is
	 * not an <code>.elpx</code> file name.
	 */
	protected static String sanitizeFileName(String fileName)
		throws ElpxException {

		String name = StringUtil.trim(fileName);

		if (name == null) {
			name = "";
		}

		name = name.substring(
			Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);

		name = name.replaceAll("[\\p{Cntrl}\"<>|?*:]", "");

		if (!StringUtil.endsWith(name, _EXTENSION) ||
			(name.length() == _EXTENSION.length())) {

			throw new ElpxException("only-elpx-files-are-supported");
		}

		if (name.length() > _MAX_FILE_NAME_LENGTH) {
			name =
				name.substring(0, _MAX_FILE_NAME_LENGTH - _EXTENSION.length()) +
					_EXTENSION;
		}

		return name;
	}

	@Activate
	@Modified
	protected void activate(Map<String, Object> properties) {
		_exeLearningConfiguration = ConfigurableUtil.createConfigurable(
			ExeLearningConfiguration.class, properties);

		if (_publicationStore == null) {
			_publicationStore = new PublicationStore(
				Paths.get(
					PropsUtil.get(PropsKeys.LIFERAY_HOME), "data",
					"exelearning", "publications"));
		}
	}

	private FileEntry _addFileEntry(
			long groupId, Folder folder, String sourceFileName,
			ElpxPackage elpxPackage, Path file)
		throws PortalException {

		String baseName = sourceFileName.substring(
			0, sourceFileName.length() - _EXTENSION.length());

		String title = _truncate(elpxPackage.getTitle(), _MAX_TITLE_LENGTH);

		if (Validator.isNull(title)) {
			title = baseName;
		}

		for (int i = 1;; i++) {
			String suffix = (i == 1) ? "" : " (" + i + ")";

			try {
				return _dlAppService.addFileEntry(
					null, groupId, folder.getFolderId(),
					baseName + suffix + _EXTENSION, MIME_TYPE, title + suffix,
					null,
					_truncate(
						elpxPackage.getDescription(), _MAX_DESCRIPTION_LENGTH),
					_CHANGE_LOG_UPLOAD, file.toFile(), null, null, null,
					_createServiceContext(groupId));
			}
			catch (DuplicateFileEntryException duplicateFileEntryException) {
				if (i >= _MAX_NAME_ATTEMPTS) {
					throw duplicateFileEntryException;
				}
			}
		}
	}

	private Path _copy(InputStream inputStream)
		throws IOException, PortalException {

		long maxUploadSize = _exeLearningConfiguration.maxUploadSize();

		Path file = Files.createTempFile("exelearning-", _EXTENSION);

		try (OutputStream outputStream = Files.newOutputStream(file)) {
			byte[] buffer = new byte[8192];
			long total = 0;
			int read;

			while ((read = inputStream.read(buffer)) != -1) {
				total += read;

				if (total > maxUploadSize) {
					throw new ElpxException(
						"the-file-exceeds-the-maximum-upload-size-x",
						maxUploadSize);
				}

				outputStream.write(buffer, 0, read);
			}

			return file;
		}
		catch (IOException | PortalException exception) {
			Files.deleteIfExists(file);

			throw exception;
		}
	}

	private ServiceContext _createServiceContext(long groupId)
		throws PortalException {

		PermissionChecker permissionChecker = _getPermissionChecker();

		Group group = _groupLocalService.getGroup(groupId);

		ServiceContext serviceContext = new ServiceContext();

		serviceContext.setAddGroupPermissions(true);
		serviceContext.setAddGuestPermissions(
			group.getType() == GroupConstants.TYPE_SITE_OPEN);
		serviceContext.setCompanyId(permissionChecker.getCompanyId());
		serviceContext.setScopeGroupId(groupId);
		serviceContext.setUserId(permissionChecker.getUserId());
		serviceContext.setWorkflowAction(WorkflowConstants.ACTION_PUBLISH);

		return serviceContext;
	}

	private Path _ensurePublication(
			FileEntry fileEntry, FileVersion fileVersion)
		throws IOException, PortalException {

		long companyId = fileEntry.getCompanyId();
		long fileEntryId = fileEntry.getFileEntryId();
		String version = fileVersion.getVersion();

		Path path = _publicationStore.getPath(companyId, fileEntryId, version);

		if (_publicationStore.isPublished(path)) {
			return path;
		}

		String lockKey = path.toString();

		Object lock = _publicationLocks.computeIfAbsent(
			lockKey, key -> new Object());

		synchronized (lock) {
			if (_publicationStore.isPublished(path)) {
				return path;
			}

			Path file = Files.createTempFile("exelearning-", _EXTENSION);

			try {
				try (InputStream inputStream = fileVersion.getContentStream(
						false)) {

					Files.copy(
						inputStream, file, StandardCopyOption.REPLACE_EXISTING);
				}

				ElpxPackage elpxPackage = ElpxArchive.inspect(
					file, _exeLearningConfiguration);

				if (!elpxPackage.isPublishable()) {
					throw new ElpxException("the-package-has-no-html-export");
				}

				return _publicationStore.publish(
					companyId, fileEntryId, version, file,
					_exeLearningConfiguration);
			}
			finally {
				Files.deleteIfExists(file);

				_publicationLocks.remove(lockKey);
			}
		}
	}

	private Folder _fetchFolder(long groupId) throws PortalException {
		try {
			return _dlAppLocalService.getFolder(
				groupId, DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
				FOLDER_NAME);
		}
		catch (NoSuchFolderException noSuchFolderException) {
			if (_log.isDebugEnabled()) {
				_log.debug(noSuchFolderException);
			}

			return null;
		}
	}

	/**
	 * Returns a file entry of the eXeLearning folder after checking an action.
	 * Entries the user cannot view are reported as missing, so identifiers
	 * cannot be probed.
	 */
	private FileEntry _getFileEntry(long fileEntryId, String actionId)
		throws PortalException {

		FileEntry fileEntry;

		try {
			fileEntry = _dlAppService.getFileEntry(fileEntryId);
		}
		catch (PrincipalException principalException) {
			throw new NoSuchFileEntryException(
				"No file entry " + fileEntryId, principalException);
		}

		Folder folder = _fetchFolder(fileEntry.getGroupId());

		if ((folder == null) ||
			(fileEntry.getFolderId() != folder.getFolderId())) {

			throw new NoSuchFileEntryException(
				"File entry " + fileEntryId +
					" is not an eXeLearning resource");
		}

		if (!actionId.equals(ActionKeys.VIEW)) {
			_fileEntryModelResourcePermission.check(
				_getPermissionChecker(), fileEntry, actionId);
		}

		return fileEntry;
	}

	private Folder _getOrAddFolder(long groupId) throws PortalException {
		Folder folder = _fetchFolder(groupId);

		if (folder != null) {
			return folder;
		}

		ServiceContext serviceContext = _createServiceContext(groupId);

		// Site members may view the packages; adding them takes an explicit
		// Documents and Media permission instead of the folder defaults

		String[] guestActionIds = new String[0];
		String[] viewActionIds = {ActionKeys.VIEW};

		if (serviceContext.isAddGuestPermissions()) {
			guestActionIds = viewActionIds;
		}

		serviceContext.setAddGroupPermissions(false);
		serviceContext.setAddGuestPermissions(false);
		serviceContext.setModelPermissions(
			ModelPermissionsFactory.create(
				viewActionIds, guestActionIds, DLFolder.class.getName()));

		try {
			return _dlAppLocalService.addFolder(
				null, _getPermissionChecker().getUserId(), groupId,
				DLFolderConstants.DEFAULT_PARENT_FOLDER_ID, FOLDER_NAME,
				_FOLDER_DESCRIPTION, serviceContext);
		}
		catch (DuplicateFolderNameException duplicateFolderNameException) {

			// Created concurrently by another request

			if (_log.isDebugEnabled()) {
				_log.debug(duplicateFolderNameException);
			}

			return _dlAppLocalService.getFolder(
				groupId, DLFolderConstants.DEFAULT_PARENT_FOLDER_ID,
				FOLDER_NAME);
		}
	}

	private PermissionChecker _getPermissionChecker()
		throws PrincipalException {

		PermissionChecker permissionChecker =
			PermissionThreadLocal.getPermissionChecker();

		if (permissionChecker == null) {
			throw new PrincipalException("No permission checker");
		}

		return permissionChecker;
	}

	private boolean _matches(ExeResource exeResource, String query) {
		if (Validator.isNull(query)) {
			return true;
		}

		for (String value :
				Arrays.asList(
					exeResource.getTitle(), exeResource.getDescription(),
					exeResource.getFileName(),
					exeResource.getAttribute(ResourceAttributes.AUTHOR))) {

			if (StringUtil.toLowerCase(
					value, Locale.ROOT
				).contains(
					query
				)) {

				return true;
			}
		}

		return false;
	}

	private void _prune(FileEntry fileEntry, String publishedVersion)
		throws IOException {

		Set<String> keep = new HashSet<>(
			Arrays.asList(publishedVersion, fileEntry.getVersion()));

		_publicationStore.prune(
			fileEntry.getCompanyId(), fileEntry.getFileEntryId(), keep);
	}

	private Path _stage(Path file, ElpxPackage elpxPackage)
		throws IOException, PortalException {

		if (!elpxPackage.isPublishable()) {
			return null;
		}

		return _publicationStore.stage(file, _exeLearningConfiguration);
	}

	private ExeResource _toExeResource(FileEntry fileEntry, boolean versions)
		throws PortalException {

		PermissionChecker permissionChecker = _getPermissionChecker();

		boolean canUpdate = _fileEntryModelResourcePermission.contains(
			permissionChecker, fileEntry, ActionKeys.UPDATE);

		List<FileVersion> fileVersions = Collections.emptyList();

		if (versions && canUpdate) {
			fileVersions = new ArrayList<>(
				fileEntry.getFileVersions(WorkflowConstants.STATUS_ANY));
		}

		return new ExeResource(
			fileEntry, _resourceAttributes.get(fileEntry), canUpdate,
			_fileEntryModelResourcePermission.contains(
				permissionChecker, fileEntry, ActionKeys.DELETE),
			_fileEntryModelResourcePermission.contains(
				permissionChecker, fileEntry, ActionKeys.PERMISSIONS),
			fileVersions);
	}

	private String _truncate(String value, int length) {
		if ((value == null) || (value.length() <= length)) {
			return value;
		}

		return value.substring(0, length);
	}

	private ExeResource _updatePackage(
			FileEntry fileEntry, InputStream inputStream)
		throws IOException, PortalException {

		Path file = _copy(inputStream);

		try {
			ElpxPackage elpxPackage = ElpxArchive.inspect(
				file, _exeLearningConfiguration);

			Path staging = _stage(file, elpxPackage);

			try {
				String description = elpxPackage.getDescription();

				if (Validator.isNull(description)) {
					description = fileEntry.getDescription();
				}

				FileEntry updatedFileEntry = _dlAppService.updateFileEntry(
					fileEntry.getFileEntryId(), fileEntry.getFileName(),
					MIME_TYPE, fileEntry.getTitle(), null,
					_truncate(description, _MAX_DESCRIPTION_LENGTH),
					_CHANGE_LOG_UPDATE, DLVersionNumberIncrease.MINOR,
					file.toFile(), fileEntry.getDisplayDate(),
					fileEntry.getExpirationDate(), fileEntry.getReviewDate(),
					_createServiceContext(fileEntry.getGroupId()));

				_resourceAttributes.setMetadata(updatedFileEntry, elpxPackage);

				if (staging != null) {
					_publicationStore.commit(
						staging, updatedFileEntry.getCompanyId(),
						updatedFileEntry.getFileEntryId(),
						updatedFileEntry.getVersion());
				}

				_prune(
					updatedFileEntry,
					_resourceAttributes.getPublishedVersion(updatedFileEntry));

				return _toExeResource(updatedFileEntry, true);
			}
			finally {
				_publicationStore.discard(staging);
			}
		}
		finally {
			Files.deleteIfExists(file);
		}
	}

	private static final String _CHANGE_LOG_UPDATE = "eXeLearning update";

	private static final String _CHANGE_LOG_UPLOAD = "eXeLearning upload";

	private static final String _EXTENSION = ".elpx";

	private static final String _FOLDER_DESCRIPTION =
		"eXeLearning packages managed by the eXeLearning integration";

	private static final long _LOCK_EXPIRATION_TIME = 5 * 60 * 1000;

	private static final Log _log = LogFactoryUtil.getLog(
		ExeResourceService.class);

	private static final int _MAX_DESCRIPTION_LENGTH = 2000;

	private static final int _MAX_FILE_NAME_LENGTH = 200;

	private static final int _MAX_NAME_ATTEMPTS = 50;

	private static final int _MAX_TITLE_LENGTH = 200;

	@Reference
	private CompanyLocalService _companyLocalService;

	@Reference
	private DLAppLocalService _dlAppLocalService;

	@Reference
	private DLAppService _dlAppService;

	private volatile ExeLearningConfiguration _exeLearningConfiguration;

	@Reference(
		target = "(model.class.name=com.liferay.portal.kernel.repository.model.FileEntry)"
	)
	private ModelResourcePermission<FileEntry>
		_fileEntryModelResourcePermission;

	@Reference(
		target = "(model.class.name=com.liferay.portal.kernel.repository.model.Folder)"
	)
	private ModelResourcePermission<Folder> _folderModelResourcePermission;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private LockManager _lockManager;

	@Reference
	private Portal _portal;

	@Reference(target = "(resource.name=com.liferay.document.library)")
	private PortletResourcePermission _portletResourcePermission;

	private final Map<String, Object> _publicationLocks =
		new ConcurrentHashMap<>();
	private volatile PublicationStore _publicationStore;
	private final ResourceAttributes _resourceAttributes =
		new ResourceAttributes();
	private final Map<Long, byte[]> _tokenKeys = new ConcurrentHashMap<>();

}