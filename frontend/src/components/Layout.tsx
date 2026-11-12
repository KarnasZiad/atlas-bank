import { NavLink, Outlet } from 'react-router';
import { useAuth } from 'react-oidc-context';
import { useRequiredSession } from '../auth/useSession';

interface NavItem {
  to: string;
  label: string;
  end?: boolean;
}

const CUSTOMER_NAV: NavItem[] = [
  { to: '/client', label: 'Mes comptes', end: true },
  { to: '/client/virement', label: 'Faire un virement' },
  { to: '/client/operations', label: 'Mes opérations' },
  { to: '/client/notifications', label: 'Notifications' },
  { to: '/client/profil', label: 'Mon profil' },
];

const STAFF_NAV: NavItem[] = [
  { to: '/agence', label: 'Tableau de bord', end: true },
  { to: '/agence/clients', label: 'Clients' },
  { to: '/agence/guichet', label: 'Guichet espèces' },
  { to: '/agence/virement', label: 'Virement' },
  { to: '/agence/operations', label: 'Opérations' },
];

const ADMIN_NAV: NavItem[] = [{ to: '/agence/controle', label: 'Contrôle des livres' }];

/**
 * Le cadre commun : la même application sert deux publics. Le menu dépend du rôle lu dans le jeton
 * (affichage seulement : le serveur refuserait de toute façon une action interdite).
 */
export function Layout() {
  const auth = useAuth();
  const session = useRequiredSession();
  const items = session.isStaff ? [...STAFF_NAV, ...(session.isAdmin ? ADMIN_NAV : [])] : CUSTOMER_NAV;
  const roleLabel = session.isAdmin ? 'Administrateur' : session.isStaff ? 'Agent' : 'Client';

  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand-mark" aria-hidden="true">A</span>
          <div>
            <strong>Atlas Bank</strong>
            <span>{session.isStaff ? 'Back-office agence' : 'Banque en ligne'}</span>
          </div>
        </div>
        <nav className="nav" aria-label="Navigation principale">
          {items.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.end} className={({ isActive }) => `nav-link${isActive ? ' active' : ''}`}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <div className="user">
          <div>
            <strong>{session.displayName}</strong>
            <span>{roleLabel} · {session.username}</span>
          </div>
          <button
            type="button"
            className="btn btn-small btn-ghost"
            onClick={() => void auth.signoutRedirect({ id_token_hint: auth.user?.id_token })}
          >
            Se déconnecter
          </button>
        </div>
      </aside>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}
