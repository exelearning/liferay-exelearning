/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.portlet;

import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.util.GetterUtil;

import java.util.regex.Pattern;

import javax.portlet.PortletPreferences;

import org.exelearning.liferay.content.ContentServlet;
import org.exelearning.liferay.resource.ExeResource;
import org.exelearning.liferay.resource.ExeResourceService;

/**
 * Resolves what one viewer widget shows from its preferences. The resource is
 * shown only if it belongs to the widget's site, is published and the current
 * user can view it; otherwise the widget renders a neutral message.
 *
 * @author eXeLearning contributors
 */
public class ViewerDisplayContext {

	public static final String DEFAULT_HEIGHT = "600px";

	public static final String PREFERENCE_ALLOW_FULLSCREEN = "allowFullscreen";

	public static final String PREFERENCE_FILE_ENTRY_ID = "fileEntryId";

	public static final String PREFERENCE_HEIGHT = "height";

	public static final String PREFERENCE_SHOW_DOWNLOAD = "showDownload";

	public static final String PREFERENCE_SHOW_TITLE = "showTitle";

	public static final String PREFERENCE_TEACHER_MODE = "teacherMode";

	public ViewerDisplayContext(
			PortletPreferences portletPreferences,
			ExeResourceService exeResourceService, long scopeGroupId,
			long nowEpochSeconds)
		throws PortalException {

		_portletPreferences = portletPreferences;

		_fileEntryId = GetterUtil.getLong(
			portletPreferences.getValue(PREFERENCE_FILE_ENTRY_ID, null));

		ExeResource exeResource = null;
		String contentURL = null;
		String packageURL = null;

		if (_fileEntryId > 0) {
			exeResource = exeResourceService.getPublishedResource(_fileEntryId);

			if ((exeResource != null) &&
				(exeResource.getGroupId() != scopeGroupId)) {

				exeResource = null;
			}

			if (exeResource != null) {
				contentURL = exeResourceService.getContentPath(
					exeResource.getFileEntry(
					).getCompanyId(),
					_fileEntryId, exeResource.getPublishedVersion(),
					nowEpochSeconds);

				packageURL = contentURL.substring(
					0, contentURL.lastIndexOf('/'));

				if (_isBoolean(PREFERENCE_TEACHER_MODE, false) &&
					exeResource.canUpdate()) {

					contentURL += "?exe-teacher=1";
				}
			}
		}

		_exeResource = exeResource;
		_contentURL = contentURL;
		_packageURL = packageURL;
	}

	public String getContentURL() {
		return _contentURL;
	}

	public ExeResource getExeResource() {
		return _exeResource;
	}

	public long getFileEntryId() {
		return _fileEntryId;
	}

	/**
	 * Returns a CSS height such as <code>600px</code> or <code>80vh</code>.
	 * Anything else falls back to the default so preferences can never inject
	 * CSS.
	 */
	public String getHeight() {
		String height = _portletPreferences.getValue(
			PREFERENCE_HEIGHT, DEFAULT_HEIGHT);

		if ((height != null) &&
			_heightPattern.matcher(
				height.trim()
			).matches()) {

			return height.trim();
		}

		return DEFAULT_HEIGHT;
	}

	/**
	 * Returns the signed URL of the published package, valid as long as the
	 * content URL.
	 */
	public String getPackageURL() {
		return _packageURL;
	}

	public String getSandbox() {
		return ContentServlet.SANDBOX;
	}

	public boolean isAllowFullscreen() {
		return _isBoolean(PREFERENCE_ALLOW_FULLSCREEN, true);
	}

	public boolean isConfigured() {
		return _fileEntryId > 0;
	}

	public boolean isShowDownload() {
		return _isBoolean(PREFERENCE_SHOW_DOWNLOAD, false);
	}

	public boolean isShowTitle() {
		return _isBoolean(PREFERENCE_SHOW_TITLE, true);
	}

	public boolean isTeacherMode() {
		return _isBoolean(PREFERENCE_TEACHER_MODE, false);
	}

	public boolean isVisible() {
		return _exeResource != null;
	}

	private boolean _isBoolean(String name, boolean defaultValue) {
		return GetterUtil.getBoolean(
			_portletPreferences.getValue(name, null), defaultValue);
	}

	private static final Pattern _heightPattern = Pattern.compile(
		"\\d{2,4}(px|vh)");

	private final String _contentURL;
	private final ExeResource _exeResource;
	private final long _fileEntryId;
	private final String _packageURL;
	private final PortletPreferences _portletPreferences;

}