<%--
SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
SPDX-License-Identifier: AGPL-3.0-or-later
--%>

<%@ include file="/init.jsp" %>

<%
ExeLearningAdminDisplayContext exeLearningAdminDisplayContext = (ExeLearningAdminDisplayContext)request.getAttribute(ExeLearningAdminPortlet.DISPLAY_CONTEXT);

long fileEntryId = ParamUtil.getLong(request, "fileEntryId");
String redirect = ParamUtil.getString(request, "redirect");

ExeResource exeResource = exeLearningAdminDisplayContext.getResource(fileEntryId);

portletDisplay.setShowBackIcon(true);
portletDisplay.setURLBack(redirect);

renderResponse.setTitle(LanguageUtil.format(request, "edit-x", exeResource.getTitle(), false));
%>

<clay:container-fluid
	cssClass="exelearning-admin"
>
	<exelearning-editor
		api-base="<%= HtmlUtil.escapeAttribute(GetterUtil.getString(request.getAttribute(ExeLearningAdminPortlet.API_BASE))) %>"
		back-url="<%= HtmlUtil.escapeAttribute(redirect) %>"
		data-i18n="<%= HtmlUtil.escapeAttribute(GetterUtil.getString(request.getAttribute(ExeLearningAdminPortlet.I18N))) %>"
		editor-url="<%= HtmlUtil.escapeAttribute(GetterUtil.getString(request.getAttribute(ExeLearningAdminPortlet.EDITOR_URL))) %>"
		resource-id="<%= exeResource.getFileEntryId() %>"
	>
		<p class="text-secondary"><liferay-ui:message key="loading-editor" /></p>
	</exelearning-editor>
</clay:container-fluid>