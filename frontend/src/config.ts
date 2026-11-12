/**
 * Configuration lue au moment du build (variables VITE_*). Les valeurs par défaut correspondent à la
 * pile locale lancée par `docker compose up`.
 */
export const config = {
  apiUrl: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  oidcAuthority: import.meta.env.VITE_OIDC_AUTHORITY ?? 'http://localhost:8180/realms/atlas-bank',
  oidcClientId: import.meta.env.VITE_OIDC_CLIENT_ID ?? 'atlas-web',
} as const;
