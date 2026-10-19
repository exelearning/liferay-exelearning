/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.rest;

import com.liferay.document.library.kernel.exception.NoSuchFileEntryException;
import com.liferay.portal.json.JSONObjectImpl;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.User;
import com.liferay.portal.kernel.security.auth.PrincipalException;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;

import java.text.MessageFormat;

import java.util.Locale;
import java.util.ResourceBundle;

import javax.ws.rs.NotAllowedException;
import javax.ws.rs.core.Response;

import org.exelearning.liferay.elpx.ElpxException;
import org.exelearning.liferay.resource.ResourceConflictException;
import org.exelearning.liferay.test.TestJSON;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/**
 * @author eXeLearning contributors
 */
public class ExeLearningExceptionMapperTest {

	@Before
	public void setUp() {
		Language language = Mockito.mock(Language.class);

		Mockito.when(
			language.format(
				ArgumentMatchers.any(ResourceBundle.class),
				ArgumentMatchers.anyString(),
				ArgumentMatchers.any(Object[].class))
		).thenAnswer(
			invocation -> {
				ResourceBundle resourceBundle = invocation.getArgument(0);

				return MessageFormat.format(
					resourceBundle.getString(invocation.getArgument(1)),
					(Object[])invocation.getArgument(2));
			}
		);

		_exceptionMapper = new ExeLearningExceptionMapper(
			TestJSON.factory(), language);
	}

	@After
	public void tearDown() {
		PermissionThreadLocal.setPermissionChecker(null);
	}

	@Test
	public void testLocalizesForTheCurrentUser() throws Exception {
		User user = Mockito.mock(User.class);

		Mockito.when(
			user.getLocale()
		).thenReturn(
			new Locale("es")
		);

		PermissionChecker permissionChecker = Mockito.mock(
			PermissionChecker.class);

		Mockito.when(
			permissionChecker.getUser()
		).thenReturn(
			user
		);

		PermissionThreadLocal.setPermissionChecker(permissionChecker);

		Assert.assertEquals(
			"No tienes los permisos necesarios.",
			_message(_exceptionMapper.toResponse(new PrincipalException())));

		Mockito.when(
			permissionChecker.getUser()
		).thenReturn(
			null
		);

		Assert.assertEquals(
			403,
			_exceptionMapper.toResponse(
				new PrincipalException()
			).getStatus());
	}

	@Test
	public void testMapsExceptions() throws Exception {
		Response response = _exceptionMapper.toResponse(
			new ElpxException("the-package-has-too-many-files-x", 10));

		Assert.assertEquals(400, response.getStatus());
		Assert.assertEquals(
			"the-package-has-too-many-files-x",
			_json(
				response
			).getString(
				"code"
			));
		Assert.assertEquals(
			"The package has more than 10 files.", _message(response));

		response = _exceptionMapper.toResponse(
			new ResourceConflictException(
				"the-resource-was-changed-by-someone-else", "1.3"));

		Assert.assertEquals(409, response.getStatus());
		Assert.assertTrue(
			_message(
				response
			).contains(
				"1.3"
			));

		response = _exceptionMapper.toResponse(new NoSuchFileEntryException());

		Assert.assertEquals(404, response.getStatus());
		Assert.assertEquals(
			"not-found",
			_json(
				response
			).getString(
				"code"
			));

		Assert.assertEquals(
			403,
			_exceptionMapper.toResponse(
				new PrincipalException()
			).getStatus());
		Assert.assertEquals(
			405,
			_exceptionMapper.toResponse(
				new NotAllowedException("GET")
			).getStatus());

		response = _exceptionMapper.toResponse(
			new IllegalStateException("secret internal detail"));

		Assert.assertEquals(500, response.getStatus());
		Assert.assertFalse(
			response.getEntity(
			).toString(
			).contains(
				"secret"
			));
	}

	private JSONObject _json(Response response) throws Exception {
		return new JSONObjectImpl((String)response.getEntity());
	}

	private String _message(Response response) throws Exception {
		return _json(
			response
		).getString(
			"message"
		);
	}

	private ExeLearningExceptionMapper _exceptionMapper;

}