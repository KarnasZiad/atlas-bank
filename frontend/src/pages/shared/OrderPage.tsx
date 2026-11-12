import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { transactionsApi } from '../../api/endpoints';
import { messageFor } from '../../api/problem';
import type { PaymentOrder } from '../../api/types';
import { useRequiredSession } from '../../auth/useSession';
import { ErrorAlert, Loading, Notice } from '../../components/Feedback';
import { Badge, Card, Detail, Iban, Money, PageHeader } from '../../components/ui';
import { useOrder } from '../../hooks/useOrder';
import { formatDateTime } from '../../lib/format';
import { orderStatusLabel, orderStatusTone, transactionKindLabel } from '../../lib/labels';

/** Le détail d'un ordre, suivi en direct, avec l'approbation 4 yeux pour le personnel. */
export function OrderPage() {
  const { orderId } = useParams();
  const session = useRequiredSession();
  const base = session.isStaff ? '/agence' : '/client';
  const order = useOrder(orderId);

  if (order.isPending) {
    return <Loading />;
  }
  if (order.isError) {
    return <ErrorAlert error={order.error} />;
  }
  const value = order.data;
  return (
    <>
      <PageHeader
        title={`${transactionKindLabel[value.kind]} ${value.reference}`}
        subtitle={<Link className="link" to={`${base}/operations`}>← Toutes les opérations</Link>}
      />
      <div className="grid-2">
        <Card title="Détail">
          <dl className="details">
            <Detail label="Statut"><Badge tone={orderStatusTone[value.status]}>{orderStatusLabel[value.status]}</Badge></Detail>
            {value.rejectionCode && <Detail label="Motif du refus">{messageFor(value.rejectionCode) ?? value.rejectionCode} <code>{value.rejectionCode}</code></Detail>}
            <Detail label="Montant"><Money amount={value.amount} currency={value.currency} /></Detail>
            {value.sourceAccountNumber && <Detail label="Compte débité"><Iban value={value.sourceAccountNumber} /></Detail>}
            {value.targetAccountNumber && <Detail label="Compte crédité"><Iban value={value.targetAccountNumber} /></Detail>}
            {value.description && <Detail label="Libellé">{value.description}</Detail>}
            <Detail label="Saisi par">{value.initiatedBy} ({value.initiatorType === 'EMPLOYEE' ? 'agent' : 'client'})</Detail>
            <Detail label="Demandé le">{formatDateTime(value.requestedAt)}</Detail>
            {value.approvalRequired && <Detail label="Second contrôle">{value.reviewedBy ? `${value.reviewedBy}, le ${formatDateTime(value.reviewedAt)}` : 'En attente'}</Detail>}
            {value.completedAt && <Detail label="Terminé le">{formatDateTime(value.completedAt)}</Detail>}
          </dl>
        </Card>
        {session.isStaff && value.status === 'AWAITING_APPROVAL' && <ApprovalCard order={value} />}
        {value.status === 'PENDING' && (
          <Notice><span className="spinner spinner-small" aria-hidden="true" /> En cours d'exécution par le grand livre… cette page se met à jour toute seule.</Notice>
        )}
      </div>
    </>
  );
}

/**
 * Contrôle 4 yeux : un ordre d'agent au-delà des limites attend un second employé.
 * Le bouton est désactivé pour l'agent qui a saisi l'ordre : c'est une AIDE à l'écran. La vraie
 * protection est côté serveur (domaine, contrainte SQL et grand livre comparent le "sub").
 */
function ApprovalCard({ order }: { order: PaymentOrder }) {
  const session = useRequiredSession();
  const queryClient = useQueryClient();
  const isMaker = order.initiatedBy === session.username;
  const onSuccess = (updated: PaymentOrder) => {
    queryClient.setQueryData(['order', order.id], updated);
    void queryClient.invalidateQueries({ queryKey: ['orders'] });
  };
  const approve = useMutation({ mutationFn: () => transactionsApi.approve(order.id), onSuccess });
  const refuse = useMutation({ mutationFn: () => transactionsApi.refuse(order.id), onSuccess });

  return (
    <Card title="Approbation (4 yeux)">
      <p>
        Cet ordre dépasse ce qu'un agent peut faire seul. Il n'a pas encore été envoyé au grand livre.
        Une approbation ne vaut que pour aujourd'hui : non relu ce soir, il sera annulé.
      </p>
      {isMaker && (
        <Notice tone="warning">Vous avez saisi cet ordre : un collègue doit l'approuver. Vous pouvez seulement le retirer (refuser).</Notice>
      )}
      <div className="form-actions">
        <button type="button" className="btn btn-primary" disabled={isMaker || approve.isPending} onClick={() => approve.mutate()}>
          Approuver
        </button>
        <button type="button" className="btn btn-danger" disabled={refuse.isPending} onClick={() => refuse.mutate()}>
          {isMaker ? 'Retirer mon ordre' : 'Refuser'}
        </button>
      </div>
      <ErrorAlert error={approve.error ?? refuse.error} />
    </Card>
  );
}
