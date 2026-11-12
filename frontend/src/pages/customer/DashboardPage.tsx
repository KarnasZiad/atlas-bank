import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { accountsApi, customersApi, notificationsApi, transactionsApi } from '../../api/endpoints';
import type { Account } from '../../api/types';
import { Empty, ErrorAlert, Loading } from '../../components/Feedback';
import { Badge, Card, Iban, Money, PageHeader } from '../../components/ui';
import { formatDateTime, formatMoney } from '../../lib/format';
import {
  accountStatusLabel,
  accountStatusTone,
  accountTypeLabel,
  notificationTypeLabel,
  orderStatusLabel,
  orderStatusTone,
  transactionKindLabel,
} from '../../lib/labels';

/**
 * L'accueil du client. Quatre requêtes partent en parallèle, vers trois services différents
 * (customer, account, transaction, notification) : React Query gère le chargement de chacune.
 */
export function DashboardPage() {
  const me = useQuery({ queryKey: ['me'], queryFn: customersApi.me });
  const accounts = useQuery({ queryKey: ['accounts', 'mine'], queryFn: () => accountsApi.list() });
  const orders = useQuery({ queryKey: ['orders', 'recent'], queryFn: () => transactionsApi.search({ size: 5, sort: 'requestedAt,desc' }) });
  const notifications = useQuery({ queryKey: ['notifications', 'recent'], queryFn: () => notificationsApi.list({ size: 4, sort: 'sentAt,desc' }) });

  const totals = totalsByCurrency(accounts.data?.content ?? []);

  return (
    <>
      <PageHeader
        title={me.data ? `Bonjour ${me.data.firstName}` : 'Bonjour'}
        subtitle={totals.length > 0 ? `Avoirs : ${totals.join(' · ')}` : undefined}
        actions={<Link to="/client/virement" className="btn btn-primary">Faire un virement</Link>}
      />
      <ErrorAlert error={me.error} />

      <section className="accounts-grid" aria-label="Mes comptes">
        {accounts.isPending && <Loading />}
        <ErrorAlert error={accounts.error} />
        {accounts.data?.content.length === 0 && <Empty>Vous n'avez pas encore de compte. Votre agence peut en ouvrir un.</Empty>}
        {accounts.data?.content.map((account) => (
          <Link key={account.id} to={`/client/comptes/${account.id}`} className="account-card">
            <div className="account-card-head">
              <span>{accountTypeLabel[account.type]}</span>
              {account.status !== 'ACTIVE' && <Badge tone={accountStatusTone[account.status]}>{accountStatusLabel[account.status]}</Badge>}
            </div>
            <span className="account-card-balance"><Money amount={account.balance} currency={account.currency} /></span>
            <span className="account-card-meta"><Iban value={account.accountNumber} /></span>
            <span className="account-card-meta">Disponible : {formatMoney(account.availableBalance, account.currency)}</span>
          </Link>
        ))}
      </section>

      <div className="grid-2">
        <Card title="Dernières opérations" actions={<Link className="link" to="/client/operations">Tout voir</Link>}>
          <ErrorAlert error={orders.error} />
          {orders.data?.content.length === 0 && <Empty>Aucune opération pour le moment.</Empty>}
          <ul className="list">
            {orders.data?.content.map((order) => (
              <li key={order.id}>
                <Link to={`/client/operations/${order.id}`} className="list-row">
                  <span>
                    {transactionKindLabel[order.kind]}
                    <span className="sub">{formatDateTime(order.requestedAt)}</span>
                  </span>
                  <span className="list-row-end">
                    <Money amount={order.amount} currency={order.currency} />
                    <Badge tone={orderStatusTone[order.status]}>{orderStatusLabel[order.status]}</Badge>
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        </Card>
        <Card title="Notifications" actions={<Link className="link" to="/client/notifications">Tout voir</Link>}>
          <ErrorAlert error={notifications.error} />
          {notifications.data?.content.length === 0 && <Empty>Aucune notification.</Empty>}
          <ul className="list">
            {notifications.data?.content.map((notification) => (
              <li key={notification.id} className="list-row">
                <span>
                  {notification.subject}
                  <span className="sub">{notificationTypeLabel[notification.type]} · {formatDateTime(notification.sentAt)}</span>
                </span>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </>
  );
}

/** Additionner des montants n'a de sens que dans la même devise : un total par devise. */
function totalsByCurrency(accounts: Account[]): string[] {
  const totals = new Map<string, number>();
  for (const account of accounts) {
    if (account.status !== 'CLOSED') {
      totals.set(account.currency, (totals.get(account.currency) ?? 0) + account.balance);
    }
  }
  return [...totals].map(([currency, total]) => formatMoney(total, currency));
}
