<%--
SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
SPDX-License-Identifier: AGPL-3.0-or-later
--%>

<%@ include file="/init.jsp" %>

<%
ViewerDisplayContext viewerDisplayContext = (ViewerDisplayContext)request.getAttribute(ViewerDisplayContext.class.getName());

ExeResource exeResource = viewerDisplayContext.getExeResource();
%>

<c:choose>
	<c:when test="<%= viewerDisplayContext.isVisible() %>">
		<exelearning-viewer class="exe-viewer" data-resource-id="<%= viewerDisplayContext.getFileEntryId() %>">
			<div class="exe-viewer-header">
				<c:if test="<%= viewerDisplayContext.isShowTitle() %>">
					<h2 class="exe-viewer-title"><%= HtmlUtil.escape(exeResource.getTitle()) %></h2>
				</c:if>

				<div class="btn-group">
					<c:if test="<%= viewerDisplayContext.isAllowFullscreen() %>">
						<button aria-pressed="false" class="btn btn-secondary btn-sm" data-exe-fullscreen="true" type="button">
							<liferay-ui:message key="full-screen" />
						</button>
					</c:if>

					<c:if test="<%= viewerDisplayContext.isShowDownload() %>">
						<a class="btn btn-secondary btn-sm" download="<%= HtmlUtil.escapeAttribute(exeResource.getFileName()) %>" href="<%= HtmlUtil.escapeHREF(viewerDisplayContext.getPackageURL()) %>">
							<liferay-ui:message key="download" />
						</a>
					</c:if>
				</div>
			</div>

			<iframe allow="fullscreen; autoplay; encrypted-media; picture-in-picture" class="exe-viewer-frame" referrerpolicy="strict-origin-when-cross-origin" sandbox="<%= viewerDisplayContext.getSandbox() %>" src="<%= HtmlUtil.escapeAttribute(viewerDisplayContext.getContentURL()) %>" style="--exe-viewer-height: <%= viewerDisplayContext.getHeight() %>;" title="<%= HtmlUtil.escapeAttribute(exeResource.getTitle()) %>"></iframe>
		</exelearning-viewer>
	</c:when>
	<c:when test="<%= viewerDisplayContext.isConfigured() %>">
		<div class="alert alert-info">
			<liferay-ui:message key="this-exelearning-resource-is-not-available" />
		</div>
	</c:when>
	<c:when test="<%= portletDisplay.isShowConfigurationIcon() %>">
		<div class="alert alert-info">
			<liferay-ui:message key="please-configure-this-widget-to-select-an-exelearning-resource" />
		</div>
	</c:when>
</c:choose>