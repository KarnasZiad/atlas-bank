import { useEffect, useRef } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from 'react-oidc-context';
import { Loading } from '../components/Feedback';
import { homePathOf, type Role } from './session';
import { useSession } from './useSession';

/**
 * Garde de route : exige une connexion, puis un des rôles demandés.
 * Sans connexion, on part directement chez Keycloak en retenant la page demandée (returnTo).
 */
export function RequireRole({ roles }: { roles: Role[] }) {
  const auth = useAuth();
  const session = useSession();
  const location = useLocation();
  const mustSignIn = !auth.isLoading && !auth.isAuthenticated && !auth.activeNavigator && !auth.error;
  // En développement, React StrictMode exécute les effets deux fois : une seule redirection suffit.
  const redirecting = useRef(false);

  useEffect(() => {
    if (mustSignIn && !redirecting.current) {
      redirecting.current = true;
      void auth.signinRedirect({ state: { returnTo: location.pathname + location.search } });
    }
  }, [mustSignIn, auth, location.pathname, location.search]);

  if (auth.error) {
    return <Navigate to="/" replace />;
  }
  if (!session) {
    return <Loading label="Connexion en cours…" />;
  }
  if (!roles.some((role) => session.roles.includes(role))) {
    // Un client qui tape une adresse du back-office revient sur son propre espace.
    return <Navigate to={homePathOf(session)} replace />;
  }
  return <Outlet />;
}
