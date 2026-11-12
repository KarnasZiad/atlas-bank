import { Link } from 'react-router';
import { messageFor } from '../api/problem';
import type { PaymentOrder } from '../api/types';
import { useOrder } from '../hooks/useOrder';
import { formatDateTime } from '../lib/format';
import { orderStatusLabel, orderStatusTone, transactionKindLabel } from '../lib/labels';
import { Badge, Iban, Money } from './ui';

const EXPLANATION: Record<PaymentOrder['status'], string> = {
  AWAITING_APPROVAL:
    "L'opération dépasse ce qu'un agent peut faire seul : elle attend l'approbation d'un autre employé (contrôle 4 yeux). Rien n'a été envoyé au grand livre.",
  PENDING: "L'ordre est enregistré et transmis au grand livre. L'exécution prend normalement moins d'une seconde.",
  COMPLETED: 'Le grand livre a exécuté l’opération : débit et crédit ont été écrits ensemble.',
  REJECTED: "L'opération n'a pas été exécutée. Aucun argent n'a bougé.",
};

/** Suit un ordre juste après sa création (202 Accepted) jusqu'à son statut final. */
export function OrderTracker({ initial, replayed, detailPath }: {
  initial: PaymentOrder;
  replayed: boolean;
  detailPath: string;
}) {
  const { data: order = initial } = useOrder(initial.id, initial);
  return (
    <div className={`tracker tracker-${orderStatusTone[order.status]}`} aria-live="polite">
      <div className="tracker-head">
        <div>
          <p className="tracker-kind">{transactionKindLabel[order.kind]} · <span className="mono">{order.reference}</span></p>
          <p className="tracker-amount"><Money amount={order.amount} currency={order.currency} /></p>
        </div>
        <Badge tone={orderStatusTone[order.status]}>
          {order.status === 'PENDING' && <span className="spinner spinner-small" aria-hidden="true" />}
          {orderStatusLabel[order.status]}
        </Badge>
      </div>
      <p>{EXPLANATION[order.status]}</p>
      {order.status === 'REJECTED' && order.rejectionCode && (
        <p className="tracker-reason">Motif : {messageFor(order.rejectionCode) ?? order.rejectionCode}</p>
      )}
      {replayed && (
        <p className="tracker-note">
          Cette demande avait déjà été reçue (même clé d'idempotence) : le serveur a renvoyé l'ordre d'origine, rien n'a été payé deux fois.
        </p>
      )}
      <dl className="tracker-details">
        {order.sourceAccountNumber && <><dt>Débité</dt><dd><Iban value={order.sourceAccountNumber} /></dd></>}
        {order.targetAccountNumber && <><dt>Crédité</dt><dd><Iban value={order.targetAccountNumber} /></dd></>}
        <dt>Demandé le</dt><dd>{formatDateTime(order.requestedAt)}</dd>
      </dl>
      <Link to={detailPath} className="link">Voir le détail de l'opération</Link>
    </div>
  );
}
