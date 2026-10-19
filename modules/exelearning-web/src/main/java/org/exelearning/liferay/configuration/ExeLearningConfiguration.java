/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.configuration;

import aQute.bnd.annotation.metatype.Meta;

import com.liferay.portal.configuration.metatype.annotations.ExtendedObjectClassDefinition;

/**
 * System settings of the eXeLearning integration. Limits apply to every
 * uploaded or edited package before anything is stored or published.
 *
 * @author eXeLearning contributors
 */
@ExtendedObjectClassDefinition(
	category = "exelearning", scope = ExtendedObjectClassDefinition.Scope.SYSTEM
)
@Meta.OCD(
	id = "org.exelearning.liferay.configuration.ExeLearningConfiguration",
	localization = "content/Language", name = "exelearning-configuration-name"
)
public interface ExeLearningConfiguration {

	@Meta.AD(
		deflt = "", description = "allowed-editor-parent-origins-help",
		name = "allowed-editor-parent-origins", required = false
	)
	public String[] allowedEditorParentOrigins();

	@Meta.AD(
		deflt = "480", description = "content-url-lifetime-minutes-help",
		min = "5", name = "content-url-lifetime-minutes", required = false
	)
	public int contentURLLifetimeMinutes();

	@Meta.AD(
		deflt = "true", description = "editor-enabled-help",
		name = "editor-enabled", required = false
	)
	public boolean editorEnabled();

	@Meta.AD(
		deflt = "200", description = "max-compression-ratio-help", min = "1",
		name = "max-compression-ratio", required = false
	)
	public int maxCompressionRatio();

	@Meta.AD(
		deflt = "10000", description = "max-entries-help", min = "1",
		name = "max-entries", required = false
	)
	public int maxEntries();

	@Meta.AD(
		deflt = "1073741824", description = "max-extracted-size-help",
		min = "1", name = "max-extracted-size", required = false
	)
	public long maxExtractedSize();

	@Meta.AD(
		deflt = "268435456", description = "max-upload-size-help", min = "1",
		name = "max-upload-size", required = false
	)
	public long maxUploadSize();

}