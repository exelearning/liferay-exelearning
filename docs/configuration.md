# Configuration

*Control Panel* › *System Settings* › *eXeLearning* (configuration PID
`org.exelearning.liferay.configuration.ExeLearningConfiguration`, also settable with an
`osgi/configs/org.exelearning.liferay.configuration.ExeLearningConfiguration.config` file).

| Setting | Default | Description |
| --- | --- | --- |
| `maxUploadSize` | `268435456` (256 MB) | Maximum size of an uploaded or saved `.elpx`. Liferay's own upload limit (*System Settings* › *Upload Servlet Request*, 100 MB by default) also applies to form uploads. |
| `maxExtractedSize` | `1073741824` (1 GB) | Maximum total size of a package's files once extracted. |
| `maxEntries` | `10000` | Maximum number of files in a package. |
| `maxCompressionRatio` | `200` | Packages that expand to more than 10 MB and more than this many times their size are rejected. |
| `contentURLLifetimeMinutes` | `480` | Lifetime of signed content URLs (rounded up to the hour). Pages get fresh URLs on every load. |
| `editorEnabled` | `true` | Shows *Edit with eXeLearning* and serves `/o/exelearning-editor`. |
| `allowedEditorParentOrigins` | empty | Extra origins (`https://host:port`) allowed to frame the editor and exchange messages with it, for setups that serve the editor from another host. |

Example `.config` file:

```properties
contentURLLifetimeMinutes=I"240"
maxUploadSize=L"104857600"
editorEnabled=B"true"
```

## Widget preferences

Set per widget instance in its *Configuration*: resource, height (`600px` or `80vh`), show title,
allow full screen, show download button, and *Teacher mode toggle for editors* (adds eXeLearning's
`?exe-teacher=1` for users who can edit the resource; presentation only, not access control).

## Development stack (`.env`)

`LIFERAY_PORT`, `LIFERAY_ADMIN_PASSWORD`, `LIFERAY_JVM_OPTS`, `LIFERAY_DEBUG` (JPDA on
`127.0.0.1:8000`), `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `SEED_USER_PASSWORD`.
`docker-compose.yml` also enables, for development only, feature flag `LPS-178052` (headless page
creation used by `make seed`) and disables stranger e-mail verification for the demo users.
