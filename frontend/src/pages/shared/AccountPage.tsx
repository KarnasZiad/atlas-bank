import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router';
import { accountsApi } from '../../api/endpoints';
import type { Account } from '../../api/types';
import { useRequiredSession } from '../../auth/useSession';
import { Empty, ErrorAlert, Loading, Notice } from '../../components/Feedback';
import { ReasonAction } from '../../components/ReasonAction';
import { Badge, Card, Detail, Iban, Money, PageHeader, Pagination } from '../../components/ui';
import { formatDate, formatDateTime, formatIban } from '../../lib/format';
import { accountStatusLabel, accountStatusTone, accountTypeLabel, transactionKindLabel } from '../../lib/labels';

/**
 * Un compte et son relevé (les écritures du grand livre). Partagé par le client et l'agent :
 * le serveur répond 404 si un client demande le compte de quelqu'un d'autre.
 */
export function AccountPage() {
  const { accountId = '' } = useParams();
  const session = useRequiredSession();
  const base = session.isStaff ? '/agence' : '/client';
  const account = useQuery({ queryKey: ['account', accountId], queryFn: () => accountsApi.get(accountId) });

  if (account.isPending) {
    return <Loading />;
  }
  if (account.isError) {
    return <ErrorAlert error={account.error} />;
  }
  const value = account.data;
  return (
    <>
      <PageHeader
        title={accountTypeLabel[value.type]}
        subtitle={<Iban value={value.accountNumber} />}
        actions={
          value.status === 'ACTIVE' ? (
            <Link to={`${base}/virement?source=${value.accountNumber}`} className="btn btn-primary">Virement depuis ce compte</Link>
          ) : undefined
        }
      />
      <div className="grid-2">
        <Card title="Soldes">
          <div className="balances">
            <div>
              <span className="kpi-label">Solde</span>
              <span className="kpi"><Money amount={value.balance} currency={value.currency} /></span>
            </div>
            <div>
              <span className="kpi-label">Disponible (découvert compris)</span>
              <span className="kpi kpi-secondary"><Money amount={value.availableBalance} currency={value.currency} /></span>
            </div>
          </div>
          <dl className="details">
            <Detail label="Statut"><Badge tone={accountStatusTone[value.status]}>{accountStatusLabel[value.status]}</Badge></Detail>
            <Detail label="Découvert autorisé"><Money amount={value.overdraftLimit} currency={value.currency} /></Detail>
            <Detail label="Plafond de débit par jour"><Money amount={value.dailyDebitLimit} currency={value.currency} /></Detail>
            <Detail label="Ouvert le">{formatDate(value.openedAt)}</Detail>
            {value.closedAt && <Detail label="Clôturé le">{formatDate(value.closedAt)}</Detail>}
            {session.isStaff && value.statusReason && <Detail label="Motif interne">{value.statusReason}</Detail>}
          </dl>
          {value.status === 'FROZEN' && !session.isStaff && (
            <Notice tone="warning">Ce compte est gelé : il peut recevoir de l'argent mais aucun débit n'est possible. Contactez votre agence.</Notice>
          )}
        </Card>
        {session.isStaff && <StaffAccountActions account={value} />}
      </div>
      <Statement account={value} />
    </>
  );
}

function StaffAccountActions({ account }: { account: Account }) {
  const session = useRequiredSession();
  const queryClient = useQueryClient();
  const refresh = (updated: Account) => {
    queryClient.setQueryData(['account', account.id], updated);
    void queryClient.invalidateQueries({ queryKey: ['accounts'] });
  };
  const freeze = useMutation({ mutationFn: (reason: string) => accountsApi.freeze(account.id, reason), onSuccess: refresh });
  const unfreeze = useMutation({ mutationFn: () => accountsApi.unfreeze(account.id), onSuccess: refresh });
  const close = useMutation({ mutationFn: (reason: string) => accountsApi.close(account.id, reason), onSuccess: refresh });

  return (
    <Card title="Actions de l'agence">
      <div className="stack">
        {account.status === 'ACTIVE' && (
          <ReasonAction label="Geler les débits" confirmLabel="Confirmer le gel" danger pending={freeze.isPending} onConfirm={(reason) => freeze.mutate(reason)} />
        )}
        {account.status === 'FROZEN' && (
          session.isAdmin ? (
            <button type="button" className="btn btn-secondary" disabled={unfreeze.isPending} onClick={() => unfreeze.mutate()}>
              Lever le gel
            </button>
          ) : (
            <Notice>Seul un administrateur, autre que l'agent qui a gelé le compte, peut lever le gel.</Notice>
          )
        )}
        {account.status !== 'CLOSED' && (
          <ReasonAction
            label="Clôturer le compte"
            confirmLabel="Confirmer la clôture"
            danger
            disabled={account.balance !== 0}
            pending={close.isPending}
            onConfirm={(reason) => close.mutate(reason)}
          />
        )}
        {account.status !== 'CLOSED' && account.balance !== 0 && <p className="hint">La clôture exige un solde nul.</p>}
        <ErrorAlert error={freeze.error ?? unfreeze.error ?? close.error} />
      </div>
    </Card>
  );
}

function Statement({ account }: { account: Account }) {
  const [page, setPage] = useState(0);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const entries = useQuery({
    queryKey: ['entries', account.id, { page, from, to }],
    queryFn: () => accountsApi.statement(account.id, { page, from: from || undefined, to: to || undefined, sort: 'bookedAt,desc' }),
  });

  return (
    <Card
      title="Relevé"
      actions={
        <div className="filters">
          <label className="field field-inline"><span className="field-label">Du</span>
            <input type="date" value={from} onChange={(event) => { setFrom(event.target.value); setPage(0); }} />
          </label>
          <label className="field field-inline"><span className="field-label">Au</span>
            <input type="date" value={to} onChange={(event) => { setTo(event.target.value); setPage(0); }} />
          </label>
        </div>
      }
    >
      <p className="hint">Chaque ligne est une écriture du grand livre, jamais modifiée ni supprimée. Sans dates : les 30 derniers jours.</p>
      {entries.isPending && <Loading />}
      <ErrorAlert error={entries.error} />
      {entries.data && entries.data.content.length === 0 && <Empty>Aucune écriture sur cette période.</Empty>}
      {entries.data && entries.data.content.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead>
              <tr><th>Date</th><th>Opération</th><th>Contrepartie</th><th className="num">Montant</th><th className="num">Solde après</th></tr>
            </thead>
            <tbody>
              {entries.data.content.map((entry) => (
                <tr key={entry.id}>
                  <td>{formatDateTime(entry.bookedAt)}</td>
                  <td>
                    {transactionKindLabel[entry.kind]}
                    {entry.description && <span className="sub">{entry.description}</span>}
                    <span className="sub mono">{entry.transactionReference}</span>
                  </td>
                  <td className="mono">{entry.counterpartyAccountNumber ? formatIban(entry.counterpartyAccountNumber) : 'Espèces'}</td>
                  <td className="num">
                    <Money amount={entry.direction === 'DEBIT' ? -entry.amount : entry.amount} currency={entry.currency} signed />
                  </td>
                  <td className="num"><Money amount={entry.balanceAfter} currency={entry.currency} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {entries.data && <Pagination page={entries.data.page} onChange={setPage} />}
    </Card>
  );
}
