import { Navigate, Link } from 'react-router';
import { useAuth } from 'react-oidc-context';
import { homePathOf } from '../auth/session';
import { useSession } from '../auth/useSession';
import { Loading } from '../components/Feedback';

/** Page publique. Une fois connecté, chacun est envoyé vers son espace selon son rôle. */
export function LandingPage() {
  const auth = useAuth();
  const session = useSession();

  if (auth.isLoading || auth.activeNavigator) {
    return <Loading label="Connexion en cours…" />;
  }
  if (session) {
    return <Navigate to={homePathOf(session)} replace />;
  }

  return (
    <div className="landing">
      <div className="landing-card">
        <span className="brand-mark brand-mark-large" aria-hidden="true">A</span>
        <h1>Atlas Bank</h1>
        <p className="lede">Votre banque en ligne : comptes, virements et alertes, en français, anglais ou arabe.</p>
        {auth.error && (
          <div className="alert alert-error" role="alert">
            La connexion a échoué : {auth.error.message}. Vérifiez que Keycloak est démarré (port 8180).
          </div>
        )}
        <button type="button" className="btn btn-primary btn-large" onClick={() => void auth.signinRedirect()}>
          Se connecter
        </button>
        <p className="landing-help">
          Vous êtes client ? Votre accès est créé par votre agence après la vérification de votre dossier : vous
          recevez un e-mail pour choisir votre mot de passe. Le personnel se connecte avec un second facteur (code OTP).
        </p>
      </div>
    </div>
  );
}

export function NoAccessPage() {
  const auth = useAuth();
  return (
    <div className="landing">
      <div className="landing-card">
        <h1>Aucun accès</h1>
        <p>Votre compte de connexion n'a aucun rôle Atlas Bank. Contactez votre agence.</p>
        <button type="button" className="btn btn-secondary" onClick={() => void auth.signoutRedirect()}>Se déconnecter</button>
      </div>
    </div>
  );
}

export function NotFoundPage() {
  return (
    <div className="landing">
      <div className="landing-card">
        <h1>Page introuvable</h1>
        <Link to="/" className="btn btn-secondary">Retour à l'accueil</Link>
      </div>
    </div>
  );
}
