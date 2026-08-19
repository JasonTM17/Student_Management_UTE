export type JsonObject = Record<string, unknown>;

export interface ApiClientOptions {
  baseUrl?: string;
  getAccessToken?: () => string | undefined;
}

export interface ApiClient {
  readonly baseUrl: string;
  setAccessToken(token: string | undefined): void;
  clearAccessToken(): void;
  request<TResponse>(path: string, init?: RequestInit): Promise<TResponse>;
  get<TResponse>(path: string, init?: RequestInit): Promise<TResponse>;
  post<TResponse>(path: string, body?: unknown, init?: RequestInit): Promise<TResponse>;
  put<TResponse>(path: string, body?: unknown, init?: RequestInit): Promise<TResponse>;
  patch<TResponse>(path: string, body?: unknown, init?: RequestInit): Promise<TResponse>;
  delete<TResponse>(path: string, init?: RequestInit): Promise<TResponse>;
}

export class ApiClientError extends Error {
  readonly status: number;
  readonly code?: string;
  readonly requestId: string;

  constructor(message: string, status: number, requestId: string, code?: string) {
    super(message);
    this.name = 'ApiClientError';
    this.status = status;
    this.requestId = requestId;
    this.code = code;
  }
}

export const DEFAULT_API_BASE_URL = 'http://127.0.0.1:4010/api/v1';

const configuredApiBaseUrl = process.env.EXPO_PUBLIC_API_URL;

function normalizeBaseUrl(value: string) {
  return value.replace(/\/+$/, '');
}

function normalizePath(path: string) {
  return path.startsWith('/') ? path : `/${path}`;
}

function createRequestId() {
  return `mobile-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
}

async function readResponseBody(response: Response) {
  if (response.status === 204) {
    return undefined;
  }

  const contentType = response.headers.get('content-type') ?? '';
  if (contentType.includes('application/json')) {
    return response.json();
  }

  const text = await response.text();
  return text || undefined;
}

function getErrorDetails(body: unknown) {
  if (!body || typeof body !== 'object') {
    return { message: 'The API request failed', code: undefined };
  }

  const payload = body as JsonObject;
  return {
    message:
      typeof payload.message === 'string'
        ? payload.message
        : 'The API request failed',
    code: typeof payload.code === 'string' ? payload.code : undefined,
  };
}

export function createApiClient(options: ApiClientOptions = {}): ApiClient {
  const baseUrl = normalizeBaseUrl(
    options.baseUrl ?? configuredApiBaseUrl ?? DEFAULT_API_BASE_URL,
  );
  let accessToken: string | undefined;
  const getAccessToken = options.getAccessToken ?? (() => accessToken);

  const client: ApiClient = {
    baseUrl,

    setAccessToken(token) {
      accessToken = token;
    },

    clearAccessToken() {
      accessToken = undefined;
    },

    async request<TResponse>(path, init = {}) {
      const requestId = createRequestId();
      const headers = new Headers(init.headers);
      const token = getAccessToken();

      headers.set('Accept', 'application/json');
      headers.set('X-Request-Id', requestId);
      if (init.body && !headers.has('Content-Type')) {
        headers.set('Content-Type', 'application/json');
      }
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }

      const response = await fetch(`${baseUrl}${normalizePath(path)}`, {
        ...init,
        headers,
      });
      const body = await readResponseBody(response);

      if (!response.ok) {
        const details = getErrorDetails(body);
        throw new ApiClientError(
          details.message,
          response.status,
          requestId,
          details.code,
        );
      }

      return body as TResponse;
    },

    get<TResponse>(path, init) {
      return client.request<TResponse>(path, { ...init, method: 'GET' });
    },

    post<TResponse>(path, body, init) {
      return client.request<TResponse>(path, {
        ...init,
        method: 'POST',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    },

    put<TResponse>(path, body, init) {
      return client.request<TResponse>(path, {
        ...init,
        method: 'PUT',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    },

    patch<TResponse>(path, body, init) {
      return client.request<TResponse>(path, {
        ...init,
        method: 'PATCH',
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    },

    delete<TResponse>(path, init) {
      return client.request<TResponse>(path, { ...init, method: 'DELETE' });
    },
  };

  return client;
}

export const apiClient = createApiClient();

export const apiRoutes = {
  auth: {
    login: '/auth/login',
    refresh: '/auth/refresh',
    logout: '/auth/logout',
    me: '/auth/me',
  },
  identity: '/me',
  health: '/health/liveness',
  contract: '/contract',
  notifications: '/notifications/my',
  thesis: {
    topics: '/thesis/topics',
    assistantChat: '/thesis/assistant/chat',
  },
} as const;

export const campusApi = {
  health: () => apiClient.get<JsonObject>(apiRoutes.health),
  contract: () => apiClient.get<JsonObject>(apiRoutes.contract),
  me: () => apiClient.get<JsonObject>(apiRoutes.identity),
  login: (email: string, password: string) =>
    apiClient.post<JsonObject>(apiRoutes.auth.login, { email, password }),
  refresh: () => apiClient.post<JsonObject>(apiRoutes.auth.refresh, {}),
  logout: () => apiClient.post<void>(apiRoutes.auth.logout, {}),
  notifications: () => apiClient.get<JsonObject>(apiRoutes.notifications),
  thesisTopics: () => apiClient.get<JsonObject>(apiRoutes.thesis.topics),
  assistantChat: (message: string) =>
    apiClient.post<JsonObject>(apiRoutes.thesis.assistantChat, { message }),
};

