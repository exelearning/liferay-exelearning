<%--
SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
SPDX-License-Identifier: AGPL-3.0-or-later
--%>

<%@ include file="/init.jsp" %>

<%
ExeLearningAdminDisplayContext exeLearningAdminDisplayContext = (ExeLearningAdminDisplayContext)request.getAttribute(ExeLearningAdminPortlet.DISPLAY_CONTEXT);

long fileEntryId = ParamUtil.getLong(request, "fileEntryId");
String redirect = ParamUtil.getString(request, "redirect");

ExeResource exeResource = (fileEntryId > 0) ? exeLearningAdminDisplayContext.getResource(fileEntryId) : null;

portletDisplay.setShowBackIcon(true);
portletDisplay.setURLBack(redirect);

renderResponse.setTitle((exeResource == null) ? LanguageUtil.get(request, "new-exelearning-resource") : LanguageUtil.format(request, "upload-a-new-version-of-x", exeResource.getTitle(), false));
%>

<portlet:actionURL name="/exelearning/edit_resource" var="editResourceURL">
	<portlet:param name="<%= Constants.CMD %>" value="<%= (exeResource == null) ? Constants.ADD : Constants.UPDATE %>" />

	<%-- MVCPortlet copies action parameters into the next render, so a new
		resource must not carry an empty fileEntryId over the stored one --%>

	<c:if test="<%= exeResource != null %>">
		<portlet:param name="fileEntryId" value="<%= String.valueOf(fileEntryId) %>" />
		<portlet:param name="version" value="<%= exeResource.getVersion() %>" />
	</c:if>

	<portlet:param name="backURL" value="<%= redirect %>" />
</portlet:actionURL>

<liferay-frontend:edit-form
	action="<%= editResourceURL %>"
	enctype="multipart/form-data"
	method="post"
	name="fm"
>
	<liferay-frontend:edit-form-body>
		<%@ include file="/admin/errors.jspf" %>

		<c:if test="<%= exeResource != null %>">
			<p class="text-secondary">
				<liferay-ui:message arguments="<%= exeResource.getVersion() %>" key="the-new-file-replaces-version-x-as-the-latest-version" />
			</p>
		</c:if>

		<div class="form-group">
			<label class="control-label" for="<portlet:namespace />file">
				<liferay-ui:message key="elpx-file" />
			</label>

			<input accept=".elpx" class="form-control" id="<portlet:namespace />file" name="<portlet:namespace />file" required type="file" />

			<p class="form-text">
				<liferay-ui:message key="elpx-file-help" />
			</p>
		</div>
	</liferay-frontend:edit-form-body>

	<liferay-frontend:edit-form-footer>
		<aui:button type="submit" value="upload" />

		<aui:button href="<%= redirect %>" type="cancel" />
	</liferay-frontend:edit-form-footer>
</liferay-frontend:edit-form>