/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.rest;

import com.liferay.portal.kernel.exception.NoSuchGroupException;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.json.JSONArray;
import com.liferay.portal.kernel.json.JSONFactory;
import com.liferay.portal.kernel.json.JSONObject;
import com.liferay.portal.kernel.language.Language;
import com.liferay.portal.kernel.model.Group;
import com.liferay.portal.kernel.repository.model.FileVersion;
import com.liferay.portal.kernel.security.permission.PermissionChecker;
import com.liferay.portal.kernel.security.permission.PermissionThreadLocal;
import com.liferay.portal.kernel.service.GroupLocalService;
import com.liferay.portal.kernel.util.Validator;

import java.io.IOException;
import java.io.InputStream;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import org.exelearning.liferay.content.ContentDispositions;
import org.exelearning.liferay.resource.ExeResource;
import org.exelearning.liferay.resource.ExeResourceService;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;

/**
 * REST API of the integration at <code>/o/exelearning/v1</code>. Browser
 * sessions must send Liferay's CSRF token (<code>x-csrf-token</code>); Basic
 * authentication is accepted for automation. Authorization is enforced by
 * {@link ExeResourceService} and Documents and Media on every call.
 *
 * @author eXeLearning contributors
 */
@Component(
	property = {
		JaxrsWhiteboardConstants.JAX_RS_APPLICATION_BASE + "=/exelearning",
		JaxrsWhiteboardConstants.JAX_RS_NAME + "=ExeLearning.Rest",
		"auth.verifier.auth.verifier.PortalSessionAuthVerifier.urls.includes=/*",
		"auth.verifier.guest.allowed=false", "liferay.oauth2=false"
	},
	service = Application.class
)
@Path("/v1")
public class ExeLearningRestApplication extends Application {

	@DELETE
	@Path("/resources/{id}")
	public Response deleteResource(@PathParam("id") long id)
		throws IOException, PortalException {

		_exeResourceService.deleteResource(id);

		return Response.noContent(
		).build();
	}

	@GET
	@Path("/resources/{id}/package")
	@Produces(ExeResourceService.MIME_TYPE)
	public Response getPackage(
			@PathParam("id") long id, @QueryParam("version") String version)
		throws PortalException {

		FileVersion fileVersion = _exeResourceService.getPackage(id, version);

		StreamingOutput streamingOutput = outputStream -> {
			try (InputStream inputStream = fileVersion.getContentStream(true)) {
				inputStream.transferTo(outputStream);
			}
			catch (PortalException portalException) {
				throw new IOException(portalException);
			}
		};

		return Response.ok(
			streamingOutput, ExeResourceService.MIME_TYPE
		).header(
			HttpHeaders.CACHE_CONTROL, "private, no-store"
		).header(
			"Content-Disposition",
			ContentDispositions.attachment(fileVersion.getFileName())
		).header(
			"X-Content-Type-Options", "nosniff"
		).build();
	}

	@GET
	@Path("/resources/{id}")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getResource(@PathParam("id") long id)
		throws PortalException {

		return _json(_exeResourceService.getResource(id));
	}

	@GET
	@Path("/sites/{groupId}/resources")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getResources(
			@PathParam("groupId") long groupId,
			@QueryParam("keywords") String keywords)
		throws PortalException {

		_checkGroup(groupId);

		List<ExeResource> exeResources = _exeResourceService.getResources(
			groupId, keywords);

		JSONArray jsonArray = _jsonFactory.createJSONArray();

		for (ExeResource exeResource : exeResources) {
			jsonArray.put(exeResource.toJSONObject(_jsonFactory));
		}

		JSONObject jsonObject = _jsonFactory.createJSONObject();

		jsonObject.put(
			"canAdd", _exeResourceService.canAddResource(groupId)
		).put(
			"items", jsonArray
		);

		return _json(Response.Status.OK, jsonObject);
	}

	@Override
	public Set<Object> getSingletons() {
		Set<Object> singletons = new HashSet<>();

		singletons.add(this);
		singletons.add(new ExeLearningExceptionMapper(_jsonFactory, _language));

		return singletons;
	}

	@Path("/resources/{id}/preview")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response previewResource(
			@PathParam("id") long id, @QueryParam("version") String version)
		throws IOException, PortalException {

		JSONObject jsonObject = _jsonFactory.createJSONObject();

		jsonObject.put(
			"url",
			_exeResourceService.preview(
				id, version, System.currentTimeMillis() / 1000));

		return _json(Response.Status.OK, jsonObject);
	}

	@Path("/resources/{id}/publish")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response publishResource(
			@PathParam("id") long id, @QueryParam("version") String version)
		throws IOException, PortalException {

		return _json(_exeResourceService.publish(id, version));
	}

	@Path("/resources/{id}/unpublish")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response unpublishResource(@PathParam("id") long id)
		throws PortalException {

		return _json(_exeResourceService.unpublish(id));
	}

	@Consumes(MediaType.WILDCARD)
	@Path("/resources/{id}/package")
	@Produces(MediaType.APPLICATION_JSON)
	@PUT
	public Response updatePackage(
			@PathParam("id") long id, @QueryParam("fileName") String fileName,
			@HeaderParam(HttpHeaders.IF_MATCH) String ifMatch,
			InputStream inputStream)
		throws IOException, PortalException {

		String expectedVersion = parseETag(ifMatch);

		if (Validator.isNull(expectedVersion)) {
			JSONObject jsonObject = _jsonFactory.createJSONObject();

			jsonObject.put(
				"code", "if-match-required"
			).put(
				"message", "If-Match with the edited version is required"
			);

			return _json(_PRECONDITION_REQUIRED, jsonObject);
		}

		return _json(
			_exeResourceService.updatePackage(
				id, expectedVersion, fileName, inputStream));
	}

	@Consumes(MediaType.WILDCARD)
	@Path("/sites/{groupId}/resources")
	@POST
	@Produces(MediaType.APPLICATION_JSON)
	public Response uploadResource(
			@PathParam("groupId") long groupId,
			@QueryParam("fileName") String fileName, InputStream inputStream)
		throws IOException, PortalException {

		_checkGroup(groupId);

		return _json(
			Response.Status.CREATED,
			_exeResourceService.addResource(
				groupId, fileName, inputStream
			).toJSONObject(
				_jsonFactory
			));
	}

	/**
	 * Returns the version of an <code>If-Match</code> header such as
	 * <code>"1.2"</code> or <code>W/"1.2"</code>.
	 */
	protected static String parseETag(String value) {
		if (Validator.isNull(value)) {
			return null;
		}

		String eTag = value.trim();

		if (eTag.startsWith("W/")) {
			eTag = eTag.substring(2);
		}

		if ((eTag.length() >= 2) && eTag.startsWith("\"") &&
			eTag.endsWith("\"")) {

			eTag = eTag.substring(1, eTag.length() - 1);
		}

		return eTag;
	}

	private void _checkGroup(long groupId) throws PortalException {
		PermissionChecker permissionChecker =
			PermissionThreadLocal.getPermissionChecker();

		Group group = _groupLocalService.fetchGroup(groupId);

		if ((group == null) ||
			(group.getCompanyId() != permissionChecker.getCompanyId())) {

			throw new NoSuchGroupException("No group " + groupId);
		}
	}

	private Response _json(ExeResource exeResource) {
		return _json(
			Response.Status.OK, exeResource.toJSONObject(_jsonFactory));
	}

	private Response _json(int status, JSONObject jsonObject) {
		return Response.status(
			status
		).entity(
			jsonObject.toString()
		).header(
			HttpHeaders.CACHE_CONTROL, "private, no-store"
		).type(
			MediaType.APPLICATION_JSON_TYPE.withCharset("UTF-8")
		).build();
	}

	private Response _json(Response.Status status, JSONObject jsonObject) {
		return _json(status.getStatusCode(), jsonObject);
	}

	private static final int _PRECONDITION_REQUIRED = 428;

	@Reference
	private ExeResourceService _exeResourceService;

	@Reference
	private GroupLocalService _groupLocalService;

	@Reference
	private JSONFactory _jsonFactory;

	@Reference
	private Language _language;

}