/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.portlet;

import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ResourceBundle;

/**
 * Language keys used by the frontend custom elements. They are rendered as a
 * JSON dictionary so the browser code uses Liferay's localization.
 *
 * @author eXeLearning contributors
 */
public class FrontendStrings {

	public static final List<String> keys = Collections.unmodifiableList(
		Arrays.asList(
			"close", "discard-unsaved-changes", "edit-x",
			"editor-could-not-be-loaded", "exelearning-editor",
			"loading-editor", "resource-changed-conflict", "save",
			"saved-as-version-x", "saving", "the-resource-could-not-be-saved"));

	/**
	 * Returns the dictionary as JSON that is safe to embed in a
	 * <code>script</code> element.
	 */
	public static String toJSON(
		JSONFactory jsonFactory, Language language,
		ResourceBundle resourceBundle) {

		JSONObject jsonObject = jsonFactory.createJSONObject();

		for (String key : keys) {
			jsonObject.put(key, language.get(resourceBundle, key));
		}

		String json = jsonObject.toString();

		return json.replace("<", "\\u003c");
	}

}