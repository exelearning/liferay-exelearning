/** Client for the eXeLearning REST API exposed by the OSGi backend. */

export interface ResourceVersion {
    version: string;
    size: number;
    createDate: string;
    userName: string;
    changeLog: string;
}

export interface Resource {
    id: number;
    groupId: number;
    title: string;
    description: string;
    fileName: string;
    size: number;
    version: string;
    modifiedDate: string;
    author: string;
    license: string;
    language: string;
    exelearningVersion: string;
    publishable: boolean;
    publishedVersion: string | null;
    permissions: { update: boolean; delete: boolean };
    versions?: ResourceVersion[];
}

export interface ResourceList {
    items: Resource[];
    canAdd: boolean;
}

export class ApiError extends Error {
    readonly status: number;
    readonly code: string;

    constructor(status: number, code: string, message: string) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.code = code;
    }
}

export interface ApiOptions {
    /** Absolute or root-relative API base, e.g. `/o/exelearning/v1`. */
    base: string;
    /** Liferay CSRF token (`Liferay.authToken`). */
    csrfToken: string;
    fetchImpl?: typeof fetch;
}

const ELPX_TYPE = 'application/octet-stream';

export class ExeApi {
    private readonly base: string;
    private readonly csrfToken: string;
    private readonly fetchImpl: typeof fetch;

    constructor(options: ApiOptions) {
        this.base = options.base.replace(/\/+$/, '');
        this.csrfToken = options.csrfToken;
        this.fetchImpl = options.fetchImpl ?? ((input, init) => fetch(input, init));
    }

    async list(groupId: number, keywords = ''): Promise<ResourceList> {
        const query = keywords ? `?keywords=${encodeURIComponent(keywords)}` : '';
        const result = await this.json<ResourceList>(`/sites/${groupId}/resources${query}`);
        return { canAdd: result.canAdd === true, items: result.items.map(normalize) };
    }

    async get(id: number): Promise<Resource> {
        return normalize(await this.json<Resource>(`/resources/${id}`));
    }

    async upload(groupId: number, file: Blob, fileName: string): Promise<Resource> {
        const resource = await this.json<Resource>(
            `/sites/${groupId}/resources?fileName=${encodeURIComponent(fileName)}`,
            { method: 'POST', headers: { 'Content-Type': ELPX_TYPE }, body: file },
        );
        return normalize(resource);
    }

    /**
     * Stores a new revision. `baseVersion` is sent as `If-Match` so the server
     * rejects the update with 409 when someone else saved in the meantime.
     */
    async saveRevision(
        id: number,
        bytes: Blob | ArrayBuffer,
        fileName: string,
        baseVersion: string,
    ): Promise<Resource> {
        const resource = await this.json<Resource>(
            `/resources/${id}/package?fileName=${encodeURIComponent(fileName)}`,
            {
                method: 'PUT',
                headers: { 'Content-Type': ELPX_TYPE, 'If-Match': `"${baseVersion}"` },
                body: bytes,
            },
        );
        return normalize(resource);
    }

    async download(id: number, version: string): Promise<ArrayBuffer> {
        const response = await this.send(`/resources/${id}/package?version=${encodeURIComponent(version)}`, {
            headers: { Accept: 'application/zip' },
        });
        return response.arrayBuffer();
    }

    /** Download link; `p_auth` authorizes the cookie-authenticated GET in Liferay. */
    packageUrl(id: number, version?: string): string {
        const params = new URLSearchParams();
        if (version) {
            params.set('version', version);
        }
        if (this.csrfToken) {
            params.set('p_auth', this.csrfToken);
        }
        const query = params.toString();
        return `${this.base}/resources/${id}/package${query ? `?${query}` : ''}`;
    }

    async publish(id: number, version: string): Promise<Resource> {
        return normalize(
            await this.json<Resource>(`/resources/${id}/publish?version=${encodeURIComponent(version)}`, {
                method: 'POST',
            }),
        );
    }

    async unpublish(id: number): Promise<Resource> {
        return normalize(await this.json<Resource>(`/resources/${id}/unpublish`, { method: 'POST' }));
    }

    async remove(id: number): Promise<void> {
        await this.send(`/resources/${id}`, { method: 'DELETE' });
    }

    async previewUrl(id: number, version: string): Promise<string> {
        const result = await this.json<{ url: string }>(
            `/resources/${id}/preview?version=${encodeURIComponent(version)}`,
            { method: 'POST' },
        );
        return result.url;
    }

    private async json<T>(path: string, init: RequestInit = {}): Promise<T> {
        const response = await this.send(path, init);
        return (await response.json()) as T;
    }

    private async send(path: string, init: RequestInit = {}): Promise<Response> {
        const headers = new Headers(init.headers);
        headers.set('Accept', headers.get('Accept') ?? 'application/json');
        headers.set('x-csrf-token', this.csrfToken);
        const response = await this.fetchImpl(this.base + path, { ...init, headers, credentials: 'same-origin' });
        if (!response.ok) {
            throw await toError(response);
        }
        return response;
    }
}

/** Liferay's JSON serializes Java longs as strings; restore numbers. */
function normalize(resource: Resource): Resource {
    return {
        ...resource,
        id: Number(resource.id),
        groupId: Number(resource.groupId),
        size: Number(resource.size),
        publishedVersion: resource.publishedVersion || null,
        versions: resource.versions?.map(version => ({ ...version, size: Number(version.size) })),
    };
}

async function toError(response: Response): Promise<ApiError> {
    let code = `http.${response.status}`;
    let message = response.statusText || code;
    try {
        const body: unknown = await response.json();
        if (typeof body === 'object' && body !== null) {
            const fields = body as Record<string, unknown>;
            if (typeof fields.code === 'string') {
                code = fields.code;
            }
            if (typeof fields.message === 'string') {
                message = fields.message;
            }
        }
    } catch {
        // Non-JSON error bodies keep the HTTP status text.
    }
    return new ApiError(response.status, code, message);
}
