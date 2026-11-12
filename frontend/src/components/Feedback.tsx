import type { ReactNode } from 'react';
import { useAuth } from 'react-oidc-context';
import { describeError } from '../api/problem';

export function Loading({ label = 'Chargement…' }: { label?: string }) {
  return (
    <div className="loading" role="status">
      <span className="spinner" aria-hidden="true" />
      {label}
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <p className="empty">{children}</p>;
}

/**
 * Affiche une erreur de l'API : le message traduit, le code métier stable et le numéro de trace
 * (traceId) à communiquer au support. Jamais de détail technique : le serveur n'en envoie pas.
 */
export function ErrorAlert({ error, onRetry, retryLabel = 'Réessayer' }: {
  error: unknown;
  onRetry?: () => void;
  retryLabel?: string;
}) {
  const auth = useAuth();
  if (!error) {
    return null;
  }
  const described = describeError(error);
  const sessionExpired = described.code === 'INVALID_TOKEN' || described.code === 'HTTP_401';
  return (
    <div className="alert alert-error" role="alert">
      <p>{described.message}</p>
      <p className="alert-meta">
        {described.code && <span>Code : <code>{described.code}</code></span>}
        {described.traceId && <span>Trace : <code>{described.traceId}</code></span>}
      </p>
      <div className="alert-actions">
        {sessionExpired && (
          <button type="button" className="btn btn-small" onClick={() => void auth.signinRedirect()}>
            Se reconnecter
          </button>
        )}
        {onRetry && described.retryable && (
          <button type="button" className="btn btn-small" onClick={onRetry}>{retryLabel}</button>
        )}
      </div>
    </div>
  );
}

export function Notice({ tone = 'info', children }: { tone?: 'info' | 'success' | 'warning'; children: ReactNode }) {
  return <div className={`alert alert-${tone}`}>{children}</div>;
}
