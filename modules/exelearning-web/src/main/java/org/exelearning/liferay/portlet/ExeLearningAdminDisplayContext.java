/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.portlet;

import com.liferay.frontend.taglib.clay.servlet.taglib.util.CreationMenu;
import com.liferay.frontend.taglib.clay.servlet.taglib.util.CreationMenuBuilder;
import com.liferay.frontend.taglib.clay.servlet.taglib.util.ViewTypeItemList;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.repository.model.FileEntry;

import java.io.IOException;

import java.nio.file.Path;

import java.util.List;

import javax.portlet.PortletURL;

import org.exelearning.liferay.publication.PublicationStore;
import org.exelearning.liferay.resource.ExeResource;
import org.exelearning.liferay.resource.ExeResourceService;

/**
 * Data for the administration screens, following the display context pattern
 * of Liferay's own administration applications.
 *
 * @author eXeLearning contributors
 */
public class ExeLearningAdminDisplayContext {

	public static final String DISPLAY_STYLE_DESCRIPTIVE = "descriptive";

	public static final String DISPLAY_STYLE_ICON = "icon";

	public ExeLearningAdminDisplayContext(
		ExeResourceService exeResourceService, long groupId, String keywords,
		String displayStyle, boolean editorEnabled) {

		_exeResourceService = exeResourceService;
		_groupId = groupId;
		_keywords = keywords;
		_editorEnabled = editorEnabled;

		if (DISPLAY_STYLE_DESCRIPTIVE.equals(displayStyle)) {
			_displayStyle = DISPLAY_STYLE_DESCRIPTIVE;
		}
		else {
			_displayStyle = DISPLAY_STYLE_ICON;
		}
	}

	public boolean canAddResource() throws PortalException {
		return _exeResourceService.canAddResource(_groupId);
	}

	/**
	 * Returns the management toolbar's creation menu, or <code>null</code>
	 * when the user may not add resources so the button is not shown.
	 */
	public CreationMenu getCreationMenu(String addURL, String label)
		throws PortalException {

		if (!canAddResource()) {
			return null;
		}

		return CreationMenuBuilder.addPrimaryDropdownItem(
			dropdownItem -> {
				dropdownItem.setHref(addURL);
				dropdownItem.setLabel(label);
			}
		).build();
	}

	/**
	 * Returns the list display style: Documents and Media like cards
	 * (<code>icon</code>, the default) or a descriptive list.
	 */
	public String getDisplayStyle() {
		return _displayStyle;
	}

	public String getKeywords() {
		return _keywords;
	}

	/**
	 * Returns the signed URL to preview a revision. Editors may preview any
	 * revision; other users only the published one.
	 */
	public String getPreviewURL(
			ExeResource exeResource, String version, long nowEpochSeconds)
		throws IOException, PortalException {

		if (exeResource.canUpdate()) {
			return _exeResourceService.preview(
				exeResource.getFileEntryId(), version, nowEpochSeconds);
		}

		if (!exeResource.isPublished()) {
			return null;
		}

		FileEntry fileEntry = exeResource.getFileEntry();

		return _exeResourceService.getContentPath(
			fileEntry.getCompanyId(), exeResource.getFileEntryId(),
			exeResource.getPublishedVersion(), nowEpochSeconds);
	}

	public ExeResource getResource(long fileEntryId) throws PortalException {
		return _exeResourceService.getResource(fileEntryId);
	}

	public List<ExeResource> getResources() throws PortalException {
		if (_resources == null) {
			_resources = _exeResourceService.getResources(_groupId, _keywords);
		}

		return _resources;
	}

	/**
	 * Returns a signed URL of the <code>screenshot.png</code> that
	 * eXeLearning stores in the package, or <code>null</code> when the
	 * package has none or the user may not see it. Editors see the latest
	 * revision; other users the published one.
	 */
	public String getThumbnailURL(ExeResource exeResource, long nowEpochSeconds)
		throws IOException, PortalException {

		String version = exeResource.getPublishedVersion();

		if (exeResource.canUpdate()) {
			version = exeResource.getVersion();
		}

		if (!exeResource.isPublishable() || version.isEmpty()) {
			return null;
		}

		FileEntry fileEntry = exeResource.getFileEntry();

		Path publication = _exeResourceService.getPublication(
			fileEntry.getCompanyId(), exeResource.getFileEntryId(), version);

		PublicationStore publicationStore =
			_exeResourceService.getPublicationStore();

		if (publicationStore.resolve(publication, _SCREENSHOT) == null) {
			return null;
		}

		String contentPath = _exeResourceService.getContentPath(
			fileEntry.getCompanyId(), exeResource.getFileEntryId(), version,
			nowEpochSeconds);

		return contentPath.substring(0, contentPath.lastIndexOf('/') + 1) +
			_SCREENSHOT;
	}

	public ViewTypeItemList getViewTypeItems(PortletURL portletURL) {
		ViewTypeItemList viewTypeItemList = new ViewTypeItemList(
			portletURL, _displayStyle);

		viewTypeItemList.addCardViewTypeItem();
		viewTypeItemList.addListViewTypeItem();

		return viewTypeItemList;
	}

	/**
	 * Returns the Clay label display type of a resource's publication status.
	 */
	public String getStatusDisplayType(ExeResource exeResource) {
		if (!exeResource.isPublished()) {
			return "secondary";
		}

		if (_isLatestPublished(exeResource)) {
			return "success";
		}

		return "warning";
	}

	/**
	 * Returns the language key of a resource's publication status.
	 */
	public String getStatusKey(ExeResource exeResource) {
		if (!exeResource.isPublishable()) {
			return "no-html-export";
		}

		if (!exeResource.isPublished()) {
			return "not-published";
		}

		if (_isLatestPublished(exeResource)) {
			return "published";
		}

		return "newer-version-available";
	}

	public boolean isEditorEnabled() {
		return _editorEnabled;
	}

	/**
	 * Returns whether the latest revision can be published by the current
	 * user, i.e. it has an HTML export and is not already published.
	 */
	public boolean isPublishable(ExeResource exeResource) {
		if (exeResource.canUpdate() && exeResource.isPublishable() &&
			!_isLatestPublished(exeResource)) {

			return true;
		}

		return false;
	}

	private boolean _isLatestPublished(ExeResource exeResource) {
		String version = exeResource.getVersion();

		return version.equals(exeResource.getPublishedVersion());
	}

	private static final String _SCREENSHOT = "screenshot.png";

	private final String _displayStyle;
	private final boolean _editorEnabled;
	private final ExeResourceService _exeResourceService;
	private final long _groupId;
	private final String _keywords;
	private List<ExeResource> _resources;

}