import { useMemo } from 'react';
import { useAuth } from 'react-oidc-context';
import { sessionFromAccessToken, type Session } from './session';

/** La session de l'utilisateur connecté, ou null. Recalculée quand le jeton est renouvelé. */
export function useSession(): Session | null {
  const auth = useAuth();
  const token = auth.user?.access_token;
  return useMemo(() => (token ? sessionFromAccessToken(token) : null), [token]);
}

/** Pour les pages protégées : la session existe forcément (garantie par RequireRole). */
export function useRequiredSession(): Session {
  const session = useSession();
  if (!session) {
    throw new Error('Cette page exige un utilisateur connecté');
  }
  return session;
}
