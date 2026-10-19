---
name: liferay-osgi-module
description: Write or review Java, JSP, bnd, Gradle or Language.properties code in the Liferay OSGi modules (Declarative Services components, portlets, MVC commands, servlets, JAX-RS, configuration), including Liferay Source Formatter rules and the JUnit 4 + Mockito test style.
---

# Liferay OSGi module

Scope: `modules/exelearning-web` (all server code) and `modules/exelearning-editor`
(static editor only, no Java). Liferay Workspace plugin 17.1.11, product
`portal-7.4-ga132` (`gradle.properties`). Gradle always runs through `scripts/gradlew.sh`.
For APIs, check the GA132 sources or https://learn.liferay.com before relying on memory.

## Build facts

- `compileOnly release.portal.api` (version from the workspace product); do not add
  versions or embed portal jars. Javac uses `options.release = 11`: no records, text blocks,
  switch expressions or `instanceof` patterns while that holds, even though the JDK is 21.
- `bnd.bnd`: no `Export-Package` (everything private); `Web-ContextPath: /exelearning-web`.
  `Bundle-Version` of both modules must equal `package.json` `version` for `make package`.
- `processResources` copies `frontend/dist/exelearning.{js,css}` into `META-INF/resources/js`:
  run `make build` (not only Gradle) before deploying. `make deploy` hot-deploys changed jars;
  `make logs` shows OSGi activation errors.

## Components

- `@Component(property = {...}, service = X.class)`: always name the service.
  Dependencies are `@Reference` on `private` fields named `_camelCase`, with `target`
  filters where several implementations exist (`ModelResourcePermission`,
  `PortletResourcePermission`). Prefer injected `Portal`, `Language`, `JSONFactory` over `*Util`.
- Configuration: `configurationPid` + `@Activate @Modified protected void activate(Map<String, Object>)`
  with `ConfigurableUtil.createConfigurable`, stored in a `volatile` field. The interface uses
  `@ExtendedObjectClassDefinition(category = "exelearning", scope = SYSTEM)`, `@Meta.OCD`
  with `localization = "content/Language"`, and `@Meta.AD` names that are language keys.
- Portlets are `MVCPortlet` with `javax.portlet.version=3.0`, `javax.portlet.name` from
  `ExeLearningPortletKeys` and `javax.portlet.resource-bundle=content.Language`. MVC commands
  read `getActionParameters()` via `GetterUtil`, report expected failures with
  `SessionErrors.add` + `hideDefaultErrorMessage`, and re-render the form.
- Servlets use the HTTP whiteboard (`osgi.http.whiteboard.context.path=/`, served under
  `/o/`). REST is one JAX-RS whiteboard `Application` at `/o/exelearning/v1` with
  `auth.verifier.guest.allowed=false` and `liferay.oauth2=false`; browser calls need the
  CSRF token (`tests/integration/api.spec.ts`). Permission checks happen in the service
  layer (`DLAppService`, resource permissions), never only in JSP or JavaScript.

## Source Formatter (verified on this repo)

`make fix` runs `formatSource`; `make lint` runs `checkSourceFormatting` for both modules.
Single module: `scripts/gradlew.sh :modules:exelearning-web:formatSource`. Auto-fixed:
blank lines, diamond operators, ordering of consecutive calls such as `map.put(...)` and
the trailing newline (Java files end without one; do not let an editor add it back).
Fix by hand:

- Private instance fields `_camelCase`; private constants `_UPPER_CASE` for primitives and
  `String`, `_camelCase` for other types (`_log`, `_versionPattern`). Unused fields fail.
- `Long.parseLong`/`Integer.parseInt` -> `GetterUtil.getLong`/`getInteger` (or catch
  `NumberFormatException`). `"literal".equals(x)` -> `x.equals("literal")` or `Objects.equals`.
- Blank line between assigning a variable and using it; do not declare a variable only to
  return it.

Rule docs: https://github.com/liferay/liferay-portal/tree/master/modules/util/source-formatter/src/main/resources/documentation

## JSP and language keys

Every JSP starts with the SPDX `<%-- --%>` header; pages then `<%@ include file="/init.jsp" %>`. Add
taglibs and imports to `init.jsp`. Logic lives in display context classes
(`ExeLearningAdminDisplayContext`, `ViewerDisplayContext`); JSPs read them from request
attributes. Use `<portlet:namespace />` for ids, `<liferay-ui:message key="..."/>`, shared
error blocks in `admin/errors.jspf`. Keys are lower-case hyphenated, `-x` when they take `{0}`,
kept sorted, and present in both `content/Language.properties` and `Language_es.properties`.

## Tests

JUnit 4 + Mockito 5 (`build.gradle`), no OSGi container. Instantiate the component, inject
`@Reference` fields with `TestZips.setField(target, "_field", mock)`, use `LiferayMocks` for
Documents and Media doubles with state and `@Rule TemporaryFolder` for files. Unavoidable
statics: `new PortalUtil().setPortal(mock)`, `new LanguageUtil().setLanguage(mock)`, or
`Mockito.mockStatic(SessionErrors.class)` closed in `@After`; reset `PermissionThreadLocal`.
The `--add-opens` JVM args in `build.gradle` are required by portal kernel utilities.
Coverage gate: JaCoCo LINE 0.90 (`make test-coverage`).
