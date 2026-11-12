import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode, type ReactNode } from 'react';
import { createRoot } from 'react-dom/client';
import { AuthProvider } from 'react-oidc-context';
import { BrowserRouter, useNavigate } from 'react-router';
import { ApiError } from './api/problem';
import { App } from './App';
import { oidcSettings } from './auth/oidc';
import './styles.css';

/**
 * React Query : le cache des données lues sur l'API. Il évite de recharger ce qu'on a déjà, recharge
 * quand c'est nécessaire, et gère les états « chargement / erreur / données » de chaque requête.
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 10_000,
      // Une erreur métier (403, 404, 422…) ne se corrige pas en réessayant : on ne réessaie que le réseau et le 503.
      retry: (failures, error) => failures < 2 && error instanceof ApiError && error.isRetryable,
    },
  },
});

/**
 * Le fournisseur d'authentification, placé DANS le routeur : au retour de Keycloak, on renvoie
 * l'utilisateur vers la page qu'il voulait voir (returnTo), sans laisser le "code" dans l'URL.
 */
function AuthWithRouter({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  return (
    <AuthProvider
      {...oidcSettings}
      onSigninCallback={(user) => {
        const state = user?.state as { returnTo?: string } | undefined;
        navigate(state?.returnTo ?? '/', { replace: true });
      }}
    >
      {children}
    </AuthProvider>
  );
}

createRoot(document.getElementById('root') as HTMLElement).render(
  <StrictMode>
    <BrowserRouter>
      <AuthWithRouter>
        <QueryClientProvider client={queryClient}>
          <App />
        </QueryClientProvider>
      </AuthWithRouter>
    </BrowserRouter>
  </StrictMode>,
);
