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

String previewVersion = ParamUtil.getString(request, "version", exeResource.isPublished() ? exeResource.getPublishedVersion() : exeResource.getVersion());

String previewURL = exeResource.isPublishable() ? exeLearningAdminDisplayContext.getPreviewURL(exeResource, previewVersion, System.currentTimeMillis() / 1000) : null;

portletDisplay.setShowBackIcon(true);
portletDisplay.setURLBack(redirect);

renderResponse.setTitle(exeResource.getTitle());

request.setAttribute(ExeResource.class.getName(), exeResource);
request.setAttribute("exelearning.listURL", redirect);
%>

<clay:container-fluid
	cssClass="container-form-lg exelearning-admin"
>
	<%@ include file="/admin/errors.jspf" %>

	<div class="sheet">
		<div class="autofit-row sheet-header">
			<div class="autofit-col autofit-col-expand">
				<h2 class="sheet-title"><%= HtmlUtil.escape(exeResource.getTitle()) %></h2>

				<div>
					<clay:label
						displayType="<%= exeLearningAdminDisplayContext.getStatusDisplayType(exeResource) %>"
						label="<%= LanguageUtil.get(request, exeLearningAdminDisplayContext.getStatusKey(exeResource)) %>"
					/>
				</div>
			</div>

			<div class="autofit-col">
				<liferay-util:include page="/admin/resource_action.jsp" servletContext="<%= application %>" />
			</div>
		</div>

		<div class="sheet-section">
			<dl class="exe-metadata">

				<%
				String[][] metadata = {{"description", exeResource.getDescription()}, {"author", exeResource.getAuthor()}, {"license", exeResource.getLicense()}, {"language", exeResource.getLanguage()}, {"exelearning-version", exeResource.getExeLearningVersion()}, {"file-name", exeResource.getFileName()}};

				for (String[] field : metadata) {
					if (Validator.isNull(field[1])) {
						continue;
					}
				%>

					<dt><liferay-ui:message key="<%= field[0] %>" /></dt>
					<dd><%= HtmlUtil.escape(field[1]) %></dd>

				<%
				}
				%>

				<dt><liferay-ui:message key="published-version" /></dt>
				<dd><%= exeResource.isPublished() ? HtmlUtil.escape(exeResource.getPublishedVersion()) : LanguageUtil.get(request, "not-published") %></dd>
			</dl>
		</div>

		<c:if test="<%= exeResource.canUpdate() %>">
			<div class="sheet-section">
				<h3 class="sheet-subtitle"><liferay-ui:message key="versions" /></h3>

				<liferay-ui:search-container
					id="versions"
					total="<%= exeResource.getFileVersions().size() %>"
				>
					<liferay-ui:search-container-results
						results="<%= exeResource.getFileVersions() %>"
					/>

					<liferay-ui:search-container-row
						className="com.liferay.portal.kernel.repository.model.FileVersion"
						modelVar="fileVersion"
					>
						<liferay-ui:search-container-column-text
							name="version"
							value="<%= fileVersion.getVersion() %>"
						/>

						<liferay-ui:search-container-column-date
							name="modified-date"
							value="<%= fileVersion.getCreateDate() %>"
						/>

						<liferay-ui:search-container-column-text
							name="user"
							value="<%= HtmlUtil.escape(fileVersion.getUserName()) %>"
						/>

						<liferay-ui:search-container-column-text
							name="status"
						>
							<c:if test="<%= Objects.equals(fileVersion.getVersion(), exeResource.getPublishedVersion()) %>">
								<clay:label
									displayType="success"
									label='<%= LanguageUtil.get(request, "published") %>'
								/>
							</c:if>
						</liferay-ui:search-container-column-text>

						<liferay-ui:search-container-column-text>
							<c:if test="<%= exeResource.isPublishable() %>">
								<portlet:renderURL var="previewVersionURL">
									<portlet:param name="mvcPath" value="/admin/view_resource.jsp" />
									<portlet:param name="fileEntryId" value="<%= String.valueOf(fileEntryId) %>" />
									<portlet:param name="version" value="<%= fileVersion.getVersion() %>" />
									<portlet:param name="redirect" value="<%= redirect %>" />
								</portlet:renderURL>

								<clay:link
									href="<%= previewVersionURL %>"
									label="preview"
								/>

								<c:if test="<%= !Objects.equals(fileVersion.getVersion(), exeResource.getPublishedVersion()) %>">
									<portlet:actionURL name="/exelearning/edit_resource" var="publishVersionURL">
										<portlet:param name="<%= Constants.CMD %>" value="<%= EditResourceMVCActionCommand.CMD_PUBLISH %>" />
										<portlet:param name="fileEntryId" value="<%= String.valueOf(fileEntryId) %>" />
										<portlet:param name="version" value="<%= fileVersion.getVersion() %>" />
										<portlet:param name="redirect" value="<%= currentURL %>" />
									</portlet:actionURL>

									&middot;

									<clay:link
										href="<%= publishVersionURL %>"
										label="publish"
									/>
								</c:if>
							</c:if>
						</liferay-ui:search-container-column-text>
					</liferay-ui:search-container-row>

					<liferay-ui:search-iterator
						markupView="lexicon"
						paginate="<%= false %>"
					/>
				</liferay-ui:search-container>
			</div>
		</c:if>

		<c:if test="<%= previewURL != null %>">
			<div class="sheet-section">
				<h3 class="sheet-subtitle"><liferay-ui:message arguments="<%= previewVersion %>" key="preview-of-version-x" /></h3>

				<iframe allow="fullscreen" class="exe-preview-frame" referrerpolicy="strict-origin-when-cross-origin" sandbox="<%= ContentServlet.SANDBOX %>" src="<%= HtmlUtil.escapeAttribute(previewURL) %>" title="<%= HtmlUtil.escapeAttribute(exeResource.getTitle()) %>"></iframe>
			</div>
		</c:if>
	</div>
</clay:container-fluid>