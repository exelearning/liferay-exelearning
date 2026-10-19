<%--
SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
SPDX-License-Identifier: AGPL-3.0-or-later
--%>

<%@ include file="/init.jsp" %>

<liferay-portlet:actionURL portletConfiguration="<%= true %>" var="configurationActionURL" />

<liferay-portlet:renderURL portletConfiguration="<%= true %>" var="configurationRenderURL" />

<%
List<ExeResource> exeResources = (List<ExeResource>)request.getAttribute(ExeResource.class.getName());

long fileEntryId = GetterUtil.getLong(portletPreferences.getValue(ViewerDisplayContext.PREFERENCE_FILE_ENTRY_ID, null));
%>

<liferay-frontend:edit-form
	action="<%= configurationActionURL %>"
	method="post"
	name="fm"
>
	<aui:input name="<%= Constants.CMD %>" type="hidden" value="<%= Constants.UPDATE %>" />
	<aui:input name="redirect" type="hidden" value="<%= configurationRenderURL %>" />

	<liferay-frontend:edit-form-body>
		<aui:select label="exelearning-resource" name="preferences--fileEntryId--">
			<aui:option label="none" value="0" />

			<%
			for (ExeResource exeResource : exeResources) {
				String label = exeResource.getTitle();

				if (!exeResource.isPublished()) {
					label += " (" + LanguageUtil.get(request, "not-published") + ")";
				}
			%>

				<aui:option label="<%= label %>" localizeLabel="<%= false %>" selected="<%= exeResource.getFileEntryId() == fileEntryId %>" value="<%= exeResource.getFileEntryId() %>" />

			<%
			}
			%>

		</aui:select>

		<aui:input helpMessage="height-help" label="height" name="preferences--height--" type="text" value="<%= portletPreferences.getValue(ViewerDisplayContext.PREFERENCE_HEIGHT, ViewerDisplayContext.DEFAULT_HEIGHT) %>" />

		<aui:input label="show-title" name="preferences--showTitle--" type="checkbox" value="<%= GetterUtil.getBoolean(portletPreferences.getValue(ViewerDisplayContext.PREFERENCE_SHOW_TITLE, null), true) %>" />

		<aui:input label="allow-full-screen" name="preferences--allowFullscreen--" type="checkbox" value="<%= GetterUtil.getBoolean(portletPreferences.getValue(ViewerDisplayContext.PREFERENCE_ALLOW_FULLSCREEN, null), true) %>" />

		<aui:input label="show-download-button" name="preferences--showDownload--" type="checkbox" value="<%= GetterUtil.getBoolean(portletPreferences.getValue(ViewerDisplayContext.PREFERENCE_SHOW_DOWNLOAD, null)) %>" />

		<aui:input helpMessage="teacher-mode-help" label="teacher-mode-toggle-for-editors" name="preferences--teacherMode--" type="checkbox" value="<%= GetterUtil.getBoolean(portletPreferences.getValue(ViewerDisplayContext.PREFERENCE_TEACHER_MODE, null)) %>" />
	</liferay-frontend:edit-form-body>

	<liferay-frontend:edit-form-footer>
		<aui:button type="submit" />
	</liferay-frontend:edit-form-footer>
</liferay-frontend:edit-form>