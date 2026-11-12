import { currentAccessToken } from '../auth/oidc';
import { config } from '../config';
import { ApiError, type ProblemDetail } from './problem';

export type QueryValue = string | number | boolean | null | undefined;

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT';
  query?: Record<string, QueryValue>;
  body?: unknown;
  headers?: Record<string, string>;
}

export interface ApiResponse<T> {
  status: number;
  data: T;
  headers: Headers;
}

function urlOf(path: string, query?: Record<string, QueryValue>): string {
  const url = new URL(path, config.apiUrl);
  for (const [name, value] of Object.entries(query ?? {})) {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(name, String(value));
    }
  }
  return url.toString();
}

/**
 * Le seul endroit qui parle HTTP. Chaque appel :
 *  - passe par la gateway (config.apiUrl), jamais directement par un service ;
 *  - porte le jeton d'accès dans l'en-tête Authorization: Bearer … ;
 *  - transforme toute réponse en échec en ApiError, à partir du corps RFC 9457.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<ApiResponse<T>> {
  const headers: Record<string, string> = { Accept: 'application/json', ...options.headers };
  const token = currentAccessToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  let response: Response;
  try {
    response = await fetch(urlOf(path, options.query), {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      // L'API est sans état : aucun cookie n'est envoyé (la gateway les supprime de toute façon).
      credentials: 'omit',
    });
  } catch {
    throw new ApiError(0, { code: 'NETWORK_ERROR' });
  }

  const isJson = (response.headers.get('Content-Type') ?? '').includes('json');
  const payload: unknown = isJson ? await response.json() : null;

  if (!response.ok) {
    const problem = (payload as ProblemDetail | null) ?? {};
    if (response.status === 429 && !problem.code) {
      problem.code = 'TOO_MANY_REQUESTS';
    }
    throw new ApiError(response.status, problem);
  }
  return { status: response.status, data: payload as T, headers: response.headers };
}

export async function get<T>(path: string, query?: Record<string, QueryValue>): Promise<T> {
  return (await request<T>(path, { query })).data;
}

export async function post<T>(path: string, body?: unknown, headers?: Record<string, string>): Promise<T> {
  return (await request<T>(path, { method: 'POST', body, headers })).data;
}

export async function put<T>(path: string, body: unknown): Promise<T> {
  return (await request<T>(path, { method: 'PUT', body })).data;
}
