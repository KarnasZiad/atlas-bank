import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router';
import { accountsApi, customersApi, notificationsApi } from '../../api/endpoints';
import type { AccountType, Customer } from '../../api/types';
import { useRequiredSession } from '../../auth/useSession';
import { ContactForm } from '../../components/ContactForm';
import { Empty, ErrorAlert, Loading, Notice } from '../../components/Feedback';
import { ReasonAction } from '../../components/ReasonAction';
import { Badge, Card, Detail, Iban, Money, PageHeader } from '../../components/ui';
import { formatDate, formatDateTime } from '../../lib/format';
import {
  accountStatusLabel,
  accountStatusTone,
  accountTypeLabel,
  customerStatusLabel,
  customerStatusTone,
  languageLabel,
  notificationTypeLabel,
} from '../../lib/labels';

/** La fiche client du back-office : dossier, cycle de vie KYC, comptes, notifications, audit. */
export function CustomerPage() {
  const { customerId = '' } = useParams();
  const session = useRequiredSession();
  const customer = useQuery({ queryKey: ['customer', customerId], queryFn: () => customersApi.get(customerId) });
  const [editing, setEditing] = useState(false);

  if (customer.isPending) {
    return <Loading />;
  }
  if (customer.isError) {
    return <ErrorAlert error={customer.error} />;
  }
  const value = customer.data;
  return (
    <>
      <PageHeader
        title={`${value.firstName} ${value.lastName}`}
        subtitle={<><Badge tone={customerStatusTone[value.status]}>{customerStatusLabel[value.status]}</Badge> <Link className="link" to="/agence/clients">← Tous les clients</Link></>}
      />
      <div className="grid-2">
        <Card title="Dossier" actions={value.status !== 'CLOSED' && (
          <button type="button" className="btn btn-small btn-ghost" onClick={() => setEditing(!editing)}>
            {editing ? 'Fermer' : 'Modifier les coordonnées'}
          </button>
        )}>
          {editing ? (
            <ContactForm customer={value} onDone={() => setEditing(false)} />
          ) : (
            <dl className="details">
              <Detail label="E-mail">{value.email}</Detail>
              <Detail label="Téléphone">{value.phoneNumber}</Detail>
              <Detail label="Adresse">{value.address.line}, {value.address.postalCode} {value.address.city} ({value.address.country})</Detail>
              <Detail label="Date de naissance">{formatDate(value.dateOfBirth)}</Detail>
              <Detail label="CIN"><span className="mono">{value.nationalId}</span></Detail>
              <Detail label="Langue">{languageLabel[value.preferredLanguage] ?? value.preferredLanguage}</Detail>
              <Detail label="Enrôlé par">{value.registeredBy ?? '—'} le {formatDate(value.createdAt)}</Detail>
              <Detail label="KYC validé par">{value.verifiedBy ? `${value.verifiedBy} le ${formatDateTime(value.verifiedAt)}` : '—'}</Detail>
              <Detail label="Accès en ligne">{value.identityProvisioned ? 'Compte de connexion créé' : 'Pas encore créé'}</Detail>
              {value.statusReason && <Detail label="Motif interne">{value.statusReason}</Detail>}
            </dl>
          )}
        </Card>
        <LifecycleCard customer={value} />
      </div>
      <CustomerAccounts customer={value} />
      <div className="grid-2">
        <CustomerNotifications customerId={value.id} />
        {session.isAdmin && <CustomerHistory customerId={value.id} />}
      </div>
    </>
  );
}

/**
 * Le cycle de vie du dossier. Les boutons proposés dépendent du statut et du rôle ; le serveur
 * vérifie de toute façon chaque transition (machine à états de l'agrégat Customer).
 */
function LifecycleCard({ customer }: { customer: Customer }) {
  const session = useRequiredSession();
  const queryClient = useQueryClient();
  const onSuccess = (updated: Customer) => {
    queryClient.setQueryData(['customer', customer.id], updated);
    void queryClient.invalidateQueries({ queryKey: ['customers'] });
    // La suspension gèle les comptes (via Kafka, quelques instants plus tard).
    window.setTimeout(() => void queryClient.invalidateQueries({ queryKey: ['accounts'] }), 1_500);
  };
  const verify = useMutation({ mutationFn: () => customersApi.verify(customer.id), onSuccess });
  const suspend = useMutation({ mutationFn: (reason: string) => customersApi.suspend(customer.id, reason), onSuccess });
  const reinstate = useMutation({ mutationFn: () => customersApi.reinstate(customer.id), onSuccess });
  const close = useMutation({ mutationFn: (reason: string) => customersApi.close(customer.id, reason), onSuccess });

  // Aide à l'écran seulement : le serveur compare le "sub" (immuable), ici on n'a que le login.
  const iRegisteredIt = customer.registeredBy === session.username;

  return (
    <Card title="Cycle de vie">
      <div className="stack">
        {customer.status === 'PENDING_VERIFICATION' && (
          <>
            <p>Le dossier attend le contrôle KYC : identité, pièces et coordonnées doivent être vérifiées par un second agent.</p>
            {iRegisteredIt && (
              <Notice tone="warning">
                Vous avez enrôlé ce client. Contrôle 4 yeux : un collègue doit valider le dossier.
              </Notice>
            )}
            <button type="button" className="btn btn-primary" disabled={verify.isPending} onClick={() => verify.mutate()}>
              Valider le KYC
            </button>
            {iRegisteredIt && <p className="hint">Le bouton reste actif pour que vous voyiez le serveur refuser (403 SELF_APPROVAL_FORBIDDEN).</p>}
          </>
        )}
        {customer.status === 'ACTIVE' && (
          <>
            <p>Client actif : il peut se connecter et ses comptes fonctionnent.</p>
            <ReasonAction label="Suspendre" confirmLabel="Confirmer la suspension" danger pending={suspend.isPending} onConfirm={(reason) => suspend.mutate(reason)} />
            <p className="hint">La suspension désactive sa connexion et gèle tous ses comptes.</p>
          </>
        )}
        {customer.status === 'SUSPENDED' && (
          session.isAdmin ? (
            <button type="button" className="btn btn-primary" disabled={reinstate.isPending} onClick={() => reinstate.mutate()}>
              Lever la suspension
            </button>
          ) : (
            <Notice>Seul un administrateur, autre que l'agent qui a suspendu, peut lever la suspension.</Notice>
          )
        )}
        {customer.status !== 'CLOSED' && session.isAdmin && (
          <ReasonAction label="Clôturer la relation" confirmLabel="Clôturer définitivement" danger pending={close.isPending} onConfirm={(reason) => close.mutate(reason)} />
        )}
        {customer.status === 'CLOSED' && <p>Relation clôturée. Les autres services ont effacé ses coordonnées.</p>}
        <ErrorAlert error={verify.error ?? suspend.error ?? reinstate.error ?? close.error} />
      </div>
    </Card>
  );
}

const OFFERED: Record<'CURRENT' | 'SAVINGS', string[]> = { CURRENT: ['MAD', 'EUR', 'USD'], SAVINGS: ['MAD', 'EUR'] };

function CustomerAccounts({ customer }: { customer: Customer }) {
  const queryClient = useQueryClient();
  const accounts = useQuery({
    queryKey: ['accounts', { customerId: customer.id }],
    queryFn: () => accountsApi.list({ customerId: customer.id }),
  });
  const [type, setType] = useState<'CURRENT' | 'SAVINGS'>('CURRENT');
  const [currency, setCurrency] = useState('MAD');
  const open = useMutation({
    mutationFn: () => accountsApi.open(customer.id, type as AccountType, currency),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['accounts'] }),
  });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    open.mutate();
  };

  return (
    <Card title="Comptes">
      {accounts.isPending && <Loading />}
      <ErrorAlert error={accounts.error} />
      {accounts.data?.content.length === 0 && <Empty>Aucun compte.</Empty>}
      {accounts.data && accounts.data.content.length > 0 && (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Compte</th><th>IBAN</th><th>Statut</th><th className="num">Solde</th></tr></thead>
            <tbody>
              {accounts.data.content.map((account) => (
                <tr key={account.id}>
                  <td><Link className="link" to={`/agence/comptes/${account.id}`}>{accountTypeLabel[account.type]}</Link></td>
                  <td><Iban value={account.accountNumber} /></td>
                  <td><Badge tone={accountStatusTone[account.status]}>{accountStatusLabel[account.status]}</Badge></td>
                  <td className="num"><Money amount={account.balance} currency={account.currency} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {customer.status === 'ACTIVE' ? (
        <form className="filters open-account" onSubmit={submit}>
          <select aria-label="Produit" value={type} onChange={(event) => {
            const next = event.target.value as 'CURRENT' | 'SAVINGS';
            setType(next);
            if (!OFFERED[next].includes(currency)) {
              setCurrency('MAD');
            }
          }}>
            <option value="CURRENT">Compte courant</option>
            <option value="SAVINGS">Compte épargne</option>
          </select>
          <select aria-label="Devise" value={currency} onChange={(event) => setCurrency(event.target.value)}>
            {OFFERED[type].map((code) => <option key={code}>{code}</option>)}
          </select>
          <button type="submit" className="btn btn-primary" disabled={open.isPending}>Ouvrir le compte</button>
        </form>
      ) : (
        <p className="hint">Un compte ne peut être ouvert que pour un client actif (KYC validé).</p>
      )}
      <ErrorAlert error={open.error} />
    </Card>
  );
}

function CustomerNotifications({ customerId }: { customerId: string }) {
  const notifications = useQuery({
    queryKey: ['notifications', { customerId }],
    queryFn: () => notificationsApi.list({ customerId, size: 5, sort: 'sentAt,desc' }),
  });
  return (
    <Card title="Derniers e-mails envoyés">
      <ErrorAlert error={notifications.error} />
      {notifications.data?.content.length === 0 && <Empty>Aucun e-mail envoyé.</Empty>}
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
  );
}

/** L'historique d'audit (Hibernate Envers) : chaque modification, avec son auteur. Réservé à l'ADMIN. */
function CustomerHistory({ customerId }: { customerId: string }) {
  const history = useQuery({ queryKey: ['history', customerId], queryFn: () => customersApi.history(customerId, { size: 10 }) });
  return (
    <Card title="Historique d'audit">
      <ErrorAlert error={history.error} />
      {history.data && (
        <ol className="timeline">
          {history.data.content.map((revision) => (
            <li key={revision.revision}>
              <strong>{revision.changeType === 'INSERT' ? 'Création' : 'Modification'}</strong> par <code>{revision.changedBy}</code>
              <span className="sub">
                {formatDateTime(revision.changedAt)}
                {revision.status && ` · statut ${customerStatusLabel[revision.status]}`}
                {revision.statusReason && ` · « ${revision.statusReason} »`}
              </span>
            </li>
          ))}
        </ol>
      )}
    </Card>
  );
}
