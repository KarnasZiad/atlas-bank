import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { customersApi, transactionsApi } from '../../api/endpoints';
import { useRequiredSession } from '../../auth/useSession';
import { ErrorAlert } from '../../components/Feedback';
import { Card, PageHeader } from '../../components/ui';

/**
 * L'accueil du personnel : ce qui attend une action. Les compteurs viennent de la pagination
 * (totalElements) d'une requête de taille 1 : on ne télécharge pas les listes pour les compter.
 */
export function StaffHomePage() {
  const session = useRequiredSession();
  const approvals = useQuery({
    queryKey: ['orders', 'awaiting-count'],
    queryFn: () => transactionsApi.search({ status: 'AWAITING_APPROVAL', size: 1 }),
    refetchInterval: 15_000,
  });
  const kyc = useQuery({
    queryKey: ['customers', 'pending-count'],
    queryFn: () => customersApi.search({ status: 'PENDING_VERIFICATION', size: 1 }),
    refetchInterval: 15_000,
  });

  return (
    <>
      <PageHeader title={`Bonjour ${session.displayName}`} subtitle="Ce qui attend un second regard aujourd'hui." />
      <ErrorAlert error={approvals.error ?? kyc.error} />
      <div className="kpi-grid">
        <Link to="/agence/clients?status=PENDING_VERIFICATION" className="kpi-card">
          <span className="kpi-label">Dossiers KYC à valider</span>
          <span className="kpi">{kyc.data?.page.totalElements ?? '…'}</span>
          <span className="sub">Enrôlés par un collègue : vous ne pouvez pas valider les vôtres.</span>
        </Link>
        <Link to="/agence/operations?status=AWAITING_APPROVAL" className="kpi-card">
          <span className="kpi-label">Ordres à approuver</span>
          <span className="kpi">{approvals.data?.page.totalElements ?? '…'}</span>
          <span className="sub">Au-delà de 10 000 MAD, ou 50 000 MAD cumulés par agent et par jour.</span>
        </Link>
      </div>
      <div className="grid-2">
        <Card title="Accès rapide">
          <div className="quick-links">
            <Link to="/agence/clients/nouveau" className="btn btn-primary">Enrôler un client</Link>
            <Link to="/agence/guichet" className="btn btn-secondary">Versement / retrait</Link>
            <Link to="/agence/virement" className="btn btn-secondary">Virement pour un client</Link>
            {session.isAdmin && <Link to="/agence/controle" className="btn btn-secondary">Contrôler les livres</Link>}
          </div>
        </Card>
        <Card title="Rappel : le contrôle 4 yeux">
          <p>Une action sensible est préparée par une personne et validée par une autre :</p>
          <ul className="plain">
            <li>le KYC d'un client enrôlé par un collègue ;</li>
            <li>les ordres d'un collègue au-delà de ses limites ;</li>
            <li>la levée d'une suspension ou d'un gel, par un administrateur différent.</li>
          </ul>
          <p className="hint">Le serveur compare votre identifiant immuable (sub), pas votre login.</p>
        </Card>
      </div>
    </>
  );
}
