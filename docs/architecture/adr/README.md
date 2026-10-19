# Architecture decision records

Records follow the eXeLearning core convention (`ADR-<tracking issue>-<NN>-<slug>.md`, see
ADR-2232-01 in the core repository). All records here belong to
[exelearning/exelearning#2575](https://github.com/exelearning/exelearning/issues/2575).

| ID | Decision |
| --- | --- |
| [ADR-2575-01](ADR-2575-01-deliver-as-osgi-modules.md) | Deliver the integration as OSGi modules instead of a client extension |
| [ADR-2575-02](ADR-2575-02-store-packages-in-documents-and-media.md) | Store packages in Documents and Media with hidden Expando metadata, no custom tables |
| [ADR-2575-03](ADR-2575-03-isolate-content-in-an-opaque-origin.md) | Serve published content from an opaque origin behind signed URLs |
| [ADR-2575-04](ADR-2575-04-bundle-a-pinned-static-editor.md) | Bundle a pinned static eXeLearning editor in its own OSGi bundle |
| [ADR-2575-05](ADR-2575-05-reuse-liferay-permissions.md) | Reuse Documents and Media permissions; gate authoring with the application permission |
| [ADR-2575-06](ADR-2575-06-explicit-publication-and-stable-references.md) | Version every save, publish explicitly, reference resources by document ID |

New decisions use the next free sequence of their own tracking issue and start as `Proposed`.
