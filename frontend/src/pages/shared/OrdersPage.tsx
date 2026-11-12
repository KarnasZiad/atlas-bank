import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { transactionsApi } from '../../api/endpoints';
import { messageFor } from '../../api/problem';
import type { OrderStatus, TransactionKind } from '../../api/types';
import { useRequiredSession } from '../../auth/useSession';
import { Empty, ErrorAlert, Loading } from '../../components/Feedback';
import { Badge, Card, Iban, Money, PageHeader, Pagination } from '../../components/ui';
import { formatDateTime } from '../../lib/format';
import { orderStatusLabel, orderStatusTone, transactionKindLabel } from '../../lib/labels';

/**
 * L'historique des ordres de paiement. Un client ne reçoit que ceux qu'il a passés (filtré par le
 * serveur) ; le personnel voit tout, et peut filtrer « En attente d'approbation ».
 * Les filtres sont dans l'URL (?status=…) : on peut copier le lien ou revenir en arrière.
 */
export function OrdersPage() {
  const session = useRequiredSession();
  const base = session.isStaff ? '/agence' : '/client';
  const [params, setParams] = useSearchParams();
  const status = (params.get('status') ?? '') as OrderStatus | '';
  const kind = (params.get('kind') ?? '') as TransactionKind | '';
  const accountNumber = params.get('accountNumber') ?? '';
  const page = Number(params.get('page') ?? 0);

  const orders = useQuery({
    queryKey: ['orders', { status, kind, accountNumber, page }],
    queryFn: () => transactionsApi.search({ status, kind, accountNumber: accountNumber || undefined, page, sort: 'requestedAt,desc' }),
    refetchInterval: 10_000,
  });

  const update = (name: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) {
      next.set(name, value);
    } else {
      next.delete(name);
    }
    if (name !== 'page') {
      next.delete('page');
    }
    setParams(next);
  };

  return (
    <>
      <PageHeader
        title={session.isStaff ? 'Opérations' : 'Mes opérations'}
        subtitle="Virements, versements et retraits, du plus récent au plus ancien."
      />
      <Card
        actions={
          <div className="filters">
            <select aria-label="Statut" value={status} onChange={(event) => update('status', event.target.value)}>
              <option value="">Tous les statuts</option>
              {(Object.keys(orderStatusLabel) as OrderStatus[]).map((value) => (
                <option key={value} value={value}>{orderStatusLabel[value]}</option>
              ))}
            </select>
            <select aria-label="Type" value={kind} onChange={(event) => update('kind', event.target.value)}>
              <option value="">Tous les types</option>
              {(Object.keys(transactionKindLabel) as TransactionKind[]).map((value) => (
                <option key={value} value={value}>{transactionKindLabel[value]}</option>
              ))}
            </select>
            {session.isStaff && (
              <input
                aria-label="Compte"
                placeholder="Filtrer par IBAN"
                defaultValue={accountNumber}
                className="mono"
                onKeyDown={(event) => { if (event.key === 'Enter') update('accountNumber', event.currentTarget.value.replace(/\s+/g, '').toUpperCase()); }}
              />
            )}
          </div>
        }
      >
        {orders.isPending && <Loading />}
        <ErrorAlert error={orders.error} />
        {orders.data && orders.data.content.length === 0 && <Empty>Aucune opération ne correspond.</Empty>}
        {orders.data && orders.data.content.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Date</th><th>Opération</th><th>Comptes</th>
                  {session.isStaff && <th>Saisi par</th>}
                  <th className="num">Montant</th><th>Statut</th>
                </tr>
              </thead>
              <tbody>
                {orders.data.content.map((order) => (
                  <tr key={order.id}>
                    <td>{formatDateTime(order.requestedAt)}</td>
                    <td>
                      <Link className="link" to={`${base}/operations/${order.id}`}>{transactionKindLabel[order.kind]}</Link>
                      <span className="sub mono">{order.reference}</span>
                    </td>
                    <td>
                      {order.sourceAccountNumber && <span className="sub">De <Iban value={order.sourceAccountNumber} /></span>}
                      {order.targetAccountNumber && <span className="sub">Vers <Iban value={order.targetAccountNumber} /></span>}
                    </td>
                    {session.isStaff && <td>{order.initiatedBy}</td>}
                    <td className="num"><Money amount={order.amount} currency={order.currency} /></td>
                    <td>
                      <Badge tone={orderStatusTone[order.status]}>{orderStatusLabel[order.status]}</Badge>
                      {order.rejectionCode && <span className="sub">{messageFor(order.rejectionCode) ?? order.rejectionCode}</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {orders.data && <Pagination page={orders.data.page} onChange={(next) => update('page', String(next))} />}
      </Card>
    </>
  );
}
