/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.test;

import com.liferay.portal.json.JSONArrayImpl;
import com.liferay.portal.json.JSONObjectImpl;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;

import java.util.Collection;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * JSON factory backed by Liferay's real JSON object implementation, without
 * the portal services that <code>JSONFactoryImpl</code> needs.
 *
 * @author eXeLearning contributors
 */
public class TestJSON {

	public static JSONFactory factory() {
		JSONFactory jsonFactory = Mockito.mock(JSONFactory.class);

		Mockito.when(
			jsonFactory.createJSONObject()
		).thenAnswer(
			invocation -> new JSONObjectImpl()
		);

		Mockito.when(
			jsonFactory.createJSONArray()
		).thenAnswer(
			invocation -> new JSONArrayImpl()
		);

		Mockito.when(
			jsonFactory.createJSONArray(ArgumentMatchers.<Collection<?>>any())
		).thenAnswer(
			invocation -> {
				JSONArray jsonArray = new JSONArrayImpl();

				for (Object value : invocation.<Collection<?>>getArgument(0)) {
					jsonArray.put(value);
				}

				return jsonArray;
			}
		);

		return jsonFactory;
	}

}