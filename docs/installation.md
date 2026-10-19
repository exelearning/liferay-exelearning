# Installation

## Requirements

- Liferay Portal CE 7.4 GA132 (tested). Other 7.4 updates and Liferay DXP 7.4/quarterly releases
  use the same APIs but are **not tested**; try them in a staging environment first.
- Java 11 or later on the portal (the Docker image ships Zulu 21).
- Disk space for the published content cache under `${liferay.home}/data/exelearning`
  (roughly the extracted size of the published and latest version of every resource).

## Install

1. Download `liferay-exelearning-<version>.zip` and `liferay-exelearning-<version>.zip.sha256` from
   the [releases](https://github.com/exelearning/liferay-exelearning/releases) and verify it:
   `shasum -a 256 -c liferay-exelearning-<version>.zip.sha256`.
2. Unzip it and copy both bundles into `${liferay.home}/deploy`:
   - `org.exelearning.liferay.editor-<version>.jar` — the static eXeLearning editor;
   - `org.exelearning.liferay.web-<version>.jar` — REST API, servlets, portlets.
3. Wait for `STARTED org.exelearning.liferay.web_<version>` and
   `STARTED org.exelearning.liferay.editor_<version>` in the log. On a cluster, deploy to every node.

No client extension, database script or portal property is required.

## First steps

1. **Permissions.** Site administrators can use everything. For other users, create a site role
   (for example *eXeLearning Author*) with:
   - *eXeLearning* › *Access in Site Administration* and *View* — required to add resources;
   - *Documents and Media* › *Add Document* and *Folders* › *Add Document* — storage;
   - for editors of every resource: *Documents* › *Update* (and *View*).
   Owners can always manage their own resources. Fine-grained access per resource uses the
   *Permissions* action, which edits the Documents and Media permissions of the package.
2. **Upload.** *Site Administration* › *Content & Data* › *eXeLearning* › *New*. Choose an `.elpx`
   saved with eXeLearning 4 or later. Invalid or unsafe packages are rejected with a message.
3. **Publish.** Open the resource and choose *Publish* in its actions menu. Only published versions
   are visible to viewers.
4. **Place it on a page.** Edit a page, add the *eXeLearning* widget (category *Content Management*),
   open its *Configuration*, select the resource and set height, title, full screen, download and
   teacher mode options.
5. **Edit.** *Edit with eXeLearning* opens the bundled editor; *Save* stores a new version. The page
   keeps showing the published version until you publish the new one.

The editor can be disabled in *System Settings* › *eXeLearning*; see
[configuration.md](configuration.md).

## Storage and data

- Packages are Documents and Media files in a per-site folder named *eXeLearning*, with all
  versions. Do not move or rename that folder. Uploading `.elpx` files directly into it with the
  Documents and Media UI bypasses validation until a version is published here, where it is
  validated again.
- Metadata and the published version are hidden custom fields (Expando) of the document.
- Extracted content is a cache in `${liferay.home}/data/exelearning/publications`. It can be deleted
  at any time and is rebuilt from Documents and Media when needed. On a cluster, either share
  `data/exelearning` or let each node rebuild its own cache.

## Upgrade

Deploy the new JAR files over the old ones (same names, newer versions) in `deploy`. Data and pages
are kept; references use the document ID, so pages keep working. Read the `CHANGELOG.md` first.

## Uninstall

1. Remove the widgets from pages (otherwise pages show an unavailable-application placeholder).
2. Undeploy both bundles (Gogo shell `uninstall`, or delete them from `osgi/modules`).
3. The packages stay in Documents and Media (folder *eXeLearning*) with their versions; delete them
   there if you no longer need them. Remove `${liferay.home}/data/exelearning` and, if wanted, the
   hidden custom fields `exelearning*` of *Documents and Media* in *Custom Fields*.
