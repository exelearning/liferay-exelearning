/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.rest;

import com.liferay.portal.kernel.exception.NoSuchModelException;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.util.ResourceBundleUtil;

import java.util.Locale;
import java.util.ResourceBundle;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;

import org.exelearning.liferay.elpx.ElpxException;
import org.exelearning.liferay.resource.ResourceConflictException;

/**
 * Converts exceptions into JSON errors with a stable <code>code</code> (the
 * language key) and a localized <code>message</code>. Unexpected errors are
 * logged and reported without internal details.
 *
 * @author eXeLearning contributors
 */
public class ExeLearningExceptionMapper implements ExceptionMapper<Exception> {

	public ExeLearningExceptionMapper(
		JSONFactory jsonFactory, Language language) {

		_jsonFactory = jsonFactory;
		_language = language;
	}

	@Override
	public Response toResponse(Exception exception) {
		if (exception instanceof WebApplicationException) {
			WebApplicationException webApplicationException =
				(WebApplicationException)exception;

			Response response = webApplicationException.getResponse();

			return _error(
				response.getStatus(), "http-" + response.getStatus(),
				"the-request-could-not-be-processed");
		}

		if (exception instanceof ElpxException) {
			ElpxException elpxException = (ElpxException)exception;

			return _error(
				Response.Status.BAD_REQUEST.getStatusCode(),
				elpxException.getKey(), elpxException.getKey(),
				elpxException.getArguments());
		}

		if (exception instanceof ResourceConflictException) {
			ResourceConflictException resourceConflictException =
				(ResourceConflictException)exception;

			return _error(
				Response.Status.CONFLICT.getStatusCode(),
				resourceConflictException.getKey(),
				resourceConflictException.getKey(),
				resourceConflictException.getCurrentVersion());
		}

		if (exception instanceof NoSuchModelException) {
			return _error(
				Response.Status.NOT_FOUND.getStatusCode(), "not-found",
				"the-resource-could-not-be-found");
		}

		if (exception instanceof PrincipalException) {
			return _error(
				Response.Status.FORBIDDEN.getStatusCode(), "forbidden",
				"you-do-not-have-the-required-permissions");
		}

		_log.error(exception);

		return _error(
			Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
			"unexpected-error", "an-unexpected-error-occurred");
	}

	private Response _error(
		int status, String code, String key, Object... arguments) {

		ResourceBundle resourceBundle = ResourceBundleUtil.getBundle(
			"content.Language", _getLocale(), getClass());

		JSONObject jsonObject = _jsonFactory.createJSONObject();

		jsonObject.put(
			"code", code
		).put(
			"message", _language.format(resourceBundle, key, arguments)
		);

		return Response.status(
			status
		).entity(
			jsonObject.toString()
		).type(
			MediaType.APPLICATION_JSON_TYPE.withCharset("UTF-8")
		).build();
	}

	private Locale _getLocale() {
		PermissionChecker permissionChecker =
			PermissionThreadLocal.getPermissionChecker();

		if (permissionChecker != null) {
			User user = permissionChecker.getUser();

			if (user != null) {
				return user.getLocale();
			}
		}

		return Locale.getDefault();
	}

	private static final Log _log = LogFactoryUtil.getLog(
		ExeLearningExceptionMapper.class);

	private final JSONFactory _jsonFactory;
	private final Language _language;

}