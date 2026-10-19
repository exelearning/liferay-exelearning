# Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `make up` times out waiting for the portal | First boot can take several minutes. Check `make logs`; give Docker at least 6 GB of memory; lower `LIFERAY_JVM_OPTS` if the host is short of memory. |
| `Unable to write <jar>` in the log | A JAR copied into `deploy` is not writable by the `liferay` user. Use `make deploy`, which streams it as that user. |
| `make health` fails on *REST API* or *editor* | The bundles are not active. Run `make deploy` (`FORCE=1` to recopy) and look for `STARTED org.exelearning.liferay.*`. |
| Editor shows *Timed out waiting for EXELEARNING_READY* | The editor bundle is missing or the editor was disabled in System Settings. Check `/o/exelearning-editor/index.html`. |
| Uploads larger than 100 MB fail in the form | Liferay's *Upload Servlet Request* limit applies to form uploads; raise it together with `maxUploadSize`. |
| *The package has no HTML export* | The `.elpx` only contains `content.xml`. Open it with *Edit with eXeLearning* and save it to generate the export. |
| *Legacy eXeLearning project* | `.elp` projects from eXeLearning 2/3 are not supported. Open and save them with eXeLearning 4. |
| Widget says the resource is not available | It is not published, belongs to another site, or the user lacks *View* permission. |
| Published content stops working after a long time on one page | Content URLs expire after `contentURLLifetimeMinutes`; reloading the page issues new ones. |
| Site members cannot add resources | Adding needs the *eXeLearning* application permission plus Documents and Media *Add Document* (see installation). |
| Browser console: `Cannot read properties of null (reading 'addEventListener')` on admin lists with more than 20 items | Liferay 7.4 GA132 paginator bug; the native Bookmarks application shows the same error. Pagination still works. |
| Console: `Failed to initialize Teacher Mode` inside published content | Printed by eXeLearning's export script when it runs in the sandboxed (opaque) origin; harmless. |
| `make seed` fails creating pages | The development stack needs feature flag `LPS-178052`, set in `docker-compose.yml`; recreate the container with `make upd`. |
