<%--
SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
SPDX-License-Identifier: AGPL-3.0-or-later
--%>

<%@ include file="/init.jsp" %>

<%
ExeLearningAdminDisplayContext exeLearningAdminDisplayContext = (ExeLearningAdminDisplayContext)request.getAttribute(ExeLearningAdminPortlet.DISPLAY_CONTEXT);

ResultRow row = (ResultRow)request.getAttribute(WebKeys.SEARCH_CONTAINER_RESULT_ROW);

ExeResource exeResource = (ExeResource)request.getAttribute(ExeResource.class.getName());

if ((exeResource == null) && (row != null)) {
	exeResource = (ExeResource)row.getObject();
}

String listURL = (String)request.getAttribute("exelearning.listURL");

if (listURL == null) {
	listURL = currentURL;
}
%>

<liferay-ui:icon-menu
	direction="left-side"
	icon="<%= StringPool.BLANK %>"
	markupView="lexicon"
	message="<%= StringPool.BLANK %>"
	showWhenSingleIcon="<%= true %>"
>
	<portlet:renderURL var="viewResourceURL">
		<portlet:param name="mvcPath" value="/admin/view_resource.jsp" />
		<portlet:param name="fileEntryId" value="<%= String.valueOf(exeResource.getFileEntryId()) %>" />
		<portlet:param name="redirect" value="<%= listURL %>" />
	</portlet:renderURL>

	<liferay-ui:icon
		message="view"
		url="<%= viewResourceURL %>"
	/>

	<c:if test="<%= exeResource.canUpdate() %>">
		<c:if test="<%= exeLearningAdminDisplayContext.isEditorEnabled() %>">
			<portlet:renderURL var="editContentURL">
				<portlet:param name="mvcPath" value="/admin/edit_content.jsp" />
				<portlet:param name="fileEntryId" value="<%= String.valueOf(exeResource.getFileEntryId()) %>" />
				<portlet:param name="redirect" value="<%= currentURL %>" />
			</portlet:renderURL>

			<liferay-ui:icon
				message="edit-with-exelearning"
				url="<%= editContentURL %>"
			/>
		</c:if>

		<portlet:renderURL var="updatePackageURL">
			<portlet:param name="mvcPath" value="/admin/edit_resource.jsp" />
			<portlet:param name="fileEntryId" value="<%= String.valueOf(exeResource.getFileEntryId()) %>" />
			<portlet:param name="redirect" value="<%= currentURL %>" />
		</portlet:renderURL>

		<liferay-ui:icon
			message="upload-new-version"
			url="<%= updatePackageURL %>"
		/>

		<c:if test="<%= exeLearningAdminDisplayContext.isPublishable(exeResource) %>">
			<portlet:actionURL name="/exelearning/edit_resource" var="publishURL">
				<portlet:param name="<%= Constants.CMD %>" value="<%= EditResourceMVCActionCommand.CMD_PUBLISH %>" />
				<portlet:param name="fileEntryId" value="<%= String.valueOf(exeResource.getFileEntryId()) %>" />
				<portlet:param name="version" value="<%= exeResource.getVersion() %>" />
				<portlet:param name="redirect" value="<%= currentURL %>" />
			</portlet:actionURL>

			<liferay-ui:icon
				message="publish"
				url="<%= publishURL %>"
			/>
		</c:if>

		<c:if test="<%= exeResource.isPublished() %>">
			<portlet:actionURL name="/exelearning/edit_resource" var="unpublishURL">
				<portlet:param name="<%= Constants.CMD %>" value="<%= EditResourceMVCActionCommand.CMD_UNPUBLISH %>" />
				<portlet:param name="fileEntryId" value="<%= String.valueOf(exeResource.getFileEntryId()) %>" />
				<portlet:param name="redirect" value="<%= currentURL %>" />
			</portlet:actionURL>

			<liferay-ui:icon
				message="unpublish"
				url="<%= unpublishURL %>"
			/>
		</c:if>
	</c:if>

	<liferay-ui:icon
		message="download"
		url='<%= PortalUtil.getPathModule() + "/exelearning/v1/resources/" + exeResource.getFileEntryId() + "/package?version=" + exeResource.getVersion() + "&p_auth=" + AuthTokenUtil.getToken(request) %>'
	/>

	<c:if test="<%= exeResource.canPermissions() %>">
		<liferay-security:permissionsURL
			modelResource="<%= DLFileEntry.class.getName() %>"
			modelResourceDescription="<%= HtmlUtil.escape(exeResource.getTitle()) %>"
			resourceGroupId="<%= exeResource.getGroupId() %>"
			resourcePrimKey="<%= String.valueOf(exeResource.getFileEntryId()) %>"
			var="permissionsURL"
			windowState="<%= LiferayWindowState.POP_UP.toString() %>"
		/>

		<liferay-ui:icon
			message="permissions"
			method="get"
			url="<%= permissionsURL %>"
			useDialog="<%= true %>"
		/>
	</c:if>

	<c:if test="<%= exeResource.canDelete() %>">
		<portlet:actionURL name="/exelearning/edit_resource" var="deleteURL">
			<portlet:param name="<%= Constants.CMD %>" value="<%= Constants.DELETE %>" />
			<portlet:param name="fileEntryId" value="<%= String.valueOf(exeResource.getFileEntryId()) %>" />
			<portlet:param name="redirect" value="<%= listURL %>" />
		</portlet:actionURL>

		<liferay-ui:icon-delete
			url="<%= deleteURL %>"
		/>
	</c:if>
</liferay-ui:icon-menu>