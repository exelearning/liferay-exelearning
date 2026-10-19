<%--
SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
SPDX-License-Identifier: AGPL-3.0-or-later
--%>

<%@ include file="/init.jsp" %>

<%
ExeLearningAdminDisplayContext exeLearningAdminDisplayContext = (ExeLearningAdminDisplayContext)request.getAttribute(ExeLearningAdminPortlet.DISPLAY_CONTEXT);

List<ExeResource> exeResources = exeLearningAdminDisplayContext.getResources();

String displayStyle = exeLearningAdminDisplayContext.getDisplayStyle();
%>

<portlet:renderURL var="viewURL" />

<portlet:renderURL var="addURL">
	<portlet:param name="mvcPath" value="/admin/edit_resource.jsp" />
	<portlet:param name="redirect" value="<%= currentURL %>" />
</portlet:renderURL>

<clay:management-toolbar
	clearResultsURL="<%= viewURL %>"
	creationMenu='<%= exeLearningAdminDisplayContext.getCreationMenu(addURL, LanguageUtil.get(request, "add")) %>'
	itemsTotal="<%= exeResources.size() %>"
	searchActionURL="<%= viewURL %>"
	searchContainerId="resources"
	searchFormName="searchFm"
	searchValue="<%= Validator.isNull(exeLearningAdminDisplayContext.getKeywords()) ? null : exeLearningAdminDisplayContext.getKeywords() %>"
	selectable="<%= false %>"
	showSearch="<%= true %>"
	viewTypeItems="<%= exeLearningAdminDisplayContext.getViewTypeItems(renderResponse.createRenderURL()) %>"
/>

<clay:container-fluid
	cssClass="container-view exelearning-admin"
>
	<liferay-ui:search-container
		emptyResultsMessage="no-exelearning-resources-were-found"
		id="resources"
		total="<%= exeResources.size() %>"
	>
		<liferay-ui:search-container-results
			results="<%= ListUtil.subList(exeResources, searchContainer.getStart(), searchContainer.getEnd()) %>"
		/>

		<liferay-ui:search-container-row
			className="org.exelearning.liferay.resource.ExeResource"
			keyProperty="fileEntryId"
			modelVar="exeResource"
		>
			<portlet:renderURL var="rowURL">
				<portlet:param name="mvcPath" value="/admin/view_resource.jsp" />
				<portlet:param name="fileEntryId" value="<%= String.valueOf(exeResource.getFileEntryId()) %>" />
				<portlet:param name="redirect" value="<%= currentURL %>" />
			</portlet:renderURL>

			<c:choose>
				<c:when test="<%= displayStyle.equals(ExeLearningAdminDisplayContext.DISPLAY_STYLE_ICON) %>">

					<%
					row.setCssClass("card-page-item card-page-item-asset");

					String thumbnailURL = exeLearningAdminDisplayContext.getThumbnailURL(exeResource, System.currentTimeMillis() / 1000);
					%>

					<liferay-ui:search-container-column-text>
						<div class="card card-type-asset entry-card exe-resource-card">
							<div class="aspect-ratio card-item-first">
								<a href="<%= rowURL %>">
									<c:choose>
										<c:when test="<%= thumbnailURL != null %>">
											<img alt="" class="aspect-ratio-item-center-middle aspect-ratio-item-fluid" loading="lazy" src="<%= HtmlUtil.escapeAttribute(thumbnailURL) %>" />
										</c:when>
										<c:otherwise>
											<div class="aspect-ratio-item-center-middle card-type-asset-icon">
												<clay:icon
													symbol="books"
												/>
											</div>
										</c:otherwise>
									</c:choose>
								</a>
							</div>

							<div class="card-body">
								<div class="card-row">
									<div class="autofit-col autofit-col-expand">
										<p class="card-title text-truncate" title="<%= HtmlUtil.escapeAttribute(exeResource.getTitle()) %>">
											<aui:a href="<%= rowURL %>"><%= HtmlUtil.escape(exeResource.getTitle()) %></aui:a>
										</p>

										<p class="card-subtitle text-truncate">
											<liferay-ui:message arguments="<%= exeResource.getVersion() %>" key="version-x" />

											<c:if test="<%= Validator.isNotNull(exeResource.getAuthor()) %>">
												&middot; <%= HtmlUtil.escape(exeResource.getAuthor()) %>
											</c:if>
										</p>

										<div class="card-detail">
											<clay:label
												displayType="<%= exeLearningAdminDisplayContext.getStatusDisplayType(exeResource) %>"
												label="<%= LanguageUtil.get(request, exeLearningAdminDisplayContext.getStatusKey(exeResource)) %>"
											/>
										</div>
									</div>

									<div class="autofit-col">

										<%
										request.setAttribute(ExeResource.class.getName(), exeResource);
										%>

										<liferay-util:include page="/admin/resource_action.jsp" servletContext="<%= application %>" />

										<%
										request.removeAttribute(ExeResource.class.getName());
										%>

									</div>
								</div>
							</div>
						</div>
					</liferay-ui:search-container-column-text>
				</c:when>
				<c:otherwise>
					<liferay-ui:search-container-column-icon
						icon="books"
					/>

					<liferay-ui:search-container-column-text
						colspan="<%= 2 %>"
					>
						<h2 class="h5">
							<aui:a href="<%= rowURL %>"><%= HtmlUtil.escape(exeResource.getTitle()) %></aui:a>
						</h2>

						<span class="text-default">
							<liferay-ui:message arguments="<%= exeResource.getVersion() %>" key="version-x" />

							<c:if test="<%= Validator.isNotNull(exeResource.getAuthor()) %>">
								&middot; <%= HtmlUtil.escape(exeResource.getAuthor()) %>
							</c:if>
						</span>
						<span class="text-default">
							<clay:label
								displayType="<%= exeLearningAdminDisplayContext.getStatusDisplayType(exeResource) %>"
								label="<%= LanguageUtil.get(request, exeLearningAdminDisplayContext.getStatusKey(exeResource)) %>"
							/>
						</span>
					</liferay-ui:search-container-column-text>

					<liferay-ui:search-container-column-jsp
						path="/admin/resource_action.jsp"
					/>
				</c:otherwise>
			</c:choose>
		</liferay-ui:search-container-row>

		<liferay-ui:search-iterator
			displayStyle="<%= displayStyle %>"
			markupView="lexicon"
		/>
	</liferay-ui:search-container>
</clay:container-fluid>