import { User, WebStorageStateStore, type UserManagerSettings } from 'oidc-client-ts';
import { config } from '../config';

/**
 * Connexion OpenID Connect avec Keycloak, flux "Authorization Code + PKCE".
 *
 * 1. Le navigateur est redirigé vers Keycloak (page de connexion, OTP pour le personnel).
 * 2. Keycloak revient sur redirect_uri avec un "code" à usage unique.
 * 3. oidc-client-ts échange ce code contre les jetons, en prouvant avec PKCE que c'est bien lui qui a
 *    commencé la connexion (un code volé en route est inutilisable sans le "code_verifier").
 *
 * Le client "atlas-web" est public : une application qui tourne dans le navigateur ne peut garder
 * aucun secret, c'est pour cela que PKCE remplace le secret client.
 */
export const oidcSettings: UserManagerSettings = {
  authority: config.oidcAuthority,
  client_id: config.oidcClientId,
  redirect_uri: `${window.location.origin}/`,
  post_logout_redirect_uri: `${window.location.origin}/`,
  response_type: 'code',
  scope: 'openid',
  // Le jeton d'accès vit 5 minutes : il est renouvelé automatiquement avec le refresh token.
  automaticSilentRenew: true,
  // sessionStorage : la session disparaît à la fermeture de l'onglet. Voir le README (compromis BFF).
  userStore: new WebStorageStateStore({ store: window.sessionStorage }),
};

/** Clé sous laquelle oidc-client-ts range l'utilisateur dans le sessionStorage. */
const userStorageKey = `oidc.user:${config.oidcAuthority}:${config.oidcClientId}`;

/**
 * Le jeton d'accès courant, lu au moment de chaque appel : après un renouvellement silencieux, on
 * obtient toujours le jeton le plus récent, même depuis du code qui n'est pas un composant React.
 */
export function currentAccessToken(): string | null {
  const stored = window.sessionStorage.getItem(userStorageKey);
  if (!stored) {
    return null;
  }
  const user = User.fromStorageString(stored);
  return user.expired ? null : user.access_token;
}
