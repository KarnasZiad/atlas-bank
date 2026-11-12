import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { accountsApi, transactionsApi } from '../../api/endpoints';
import { describeError } from '../../api/problem';
import { useRequiredSession } from '../../auth/useSession';
import { ErrorAlert, Notice } from '../../components/Feedback';
import { OrderTracker } from '../../components/OrderTracker';
import { Card, Field, PageHeader } from '../../components/ui';
import { useOrderSubmission } from '../../hooks/useOrderSubmission';
import { formatIban, formatMoney, normalizeIban } from '../../lib/format';
import { accountTypeLabel } from '../../lib/labels';

/**
 * Virement. Le client choisit un de SES comptes comme source ; l'agent peut saisir n'importe quel
 * compte (au-delà de 10 000 MAD, son ordre attendra l'approbation d'un collègue).
 */
export function TransferPage() {
  const session = useRequiredSession();
  const [params] = useSearchParams();
  const base = session.isStaff ? '/agence' : '/client';

  // Le client ne voit que ses comptes : le serveur filtre lui-même selon le jeton.
  const ownAccounts = useQuery({
    queryKey: ['accounts', 'mine', 'active'],
    queryFn: () => accountsApi.list({ status: 'ACTIVE' }),
    enabled: session.isCustomer && !session.isStaff,
  });

  const [source, setSource] = useState(params.get('source') ?? '');
  const [target, setTarget] = useState('');
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [staffCurrency, setStaffCurrency] = useState('MAD');

  const accounts = ownAccounts.data?.content ?? [];
  const sourceAccount = accounts.find((account) => account.accountNumber === source);
  // Le client vire dans la devise de son compte ; l'agent la choisit (le grand livre refuse une devise différente du compte).
  const currency = session.isStaff ? staffCurrency : sourceAccount?.currency ?? 'MAD';

  const order = useOrderSubmission((key) =>
    transactionsApi.transfer(
      {
        sourceAccountNumber: normalizeIban(source),
        targetAccountNumber: normalizeIban(target),
        amount: Number(amount),
        currency,
        description: description.trim() || undefined,
      },
      key,
    ),
  );
  const fieldErrors = describeError(order.error).fieldErrors;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    void order.submit();
  };

  const startOver = () => {
    order.reset();
    setAmount('');
    setDescription('');
  };

  return (
    <>
      <PageHeader
        title="Faire un virement"
        subtitle={session.isStaff
          ? "Virement saisi par l'agence pour un client. Au-delà de 10 000 MAD (ou 50 000 MAD cumulés aujourd'hui), un collègue devra l'approuver."
          : 'De votre compte vers un autre compte Atlas Bank.'}
      />
      <div className="grid-2">
        <Card title="Ordre de virement">
          {order.submission ? (
            <div className="stack">
              <OrderTracker
                initial={order.submission.order}
                replayed={order.submission.replayed}
                detailPath={`${base}/operations/${order.submission.order.id}`}
              />
              <button type="button" className="btn btn-secondary" onClick={startOver}>Nouveau virement</button>
            </div>
          ) : (
            <form className="form" onSubmit={submit}>
              {session.isStaff ? (
                <Field label="Compte à débiter (IBAN)" error={fieldErrors.sourceAccountNumber}>
                  <input required value={source} onChange={(event) => setSource(event.target.value)} placeholder="MA64 …" className="mono" />
                </Field>
              ) : (
                <Field label="Compte à débiter" error={fieldErrors.sourceAccountNumber}>
                  <select required value={source} onChange={(event) => setSource(event.target.value)}>
                    <option value="">Choisir un compte…</option>
                    {accounts.map((account) => (
                      <option key={account.id} value={account.accountNumber}>
                        {accountTypeLabel[account.type]} · {formatIban(account.accountNumber)} · disponible {formatMoney(account.availableBalance, account.currency)}
                      </option>
                    ))}
                  </select>
                </Field>
              )}
              <Field
                label="Compte bénéficiaire (IBAN)"
                error={fieldErrors.targetAccountNumber}
                hint="28 caractères pour un IBAN marocain. La clé de contrôle est vérifiée par le serveur."
              >
                <input required value={target} onChange={(event) => setTarget(event.target.value)} placeholder="MA64 …" className="mono" list="own-accounts" />
              </Field>
              {accounts.length > 1 && (
                <datalist id="own-accounts">
                  {accounts.filter((account) => account.accountNumber !== source).map((account) => (
                    <option key={account.id} value={account.accountNumber}>{accountTypeLabel[account.type]}</option>
                  ))}
                </datalist>
              )}
              <div className="form-row">
                <Field label={`Montant (${currency})`} error={fieldErrors.amount}>
                  <input required type="number" inputMode="decimal" min="0.01" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} />
                </Field>
                {session.isStaff && (
                  <Field label="Devise" error={fieldErrors.currency}>
                    <select value={staffCurrency} onChange={(event) => setStaffCurrency(event.target.value)}>
                      <option>MAD</option><option>EUR</option><option>USD</option>
                    </select>
                  </Field>
                )}
              </div>
              <Field label="Libellé (facultatif)" error={fieldErrors.description}>
                <input maxLength={140} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="Loyer de novembre" />
              </Field>
              <ErrorAlert error={order.error} onRetry={() => void order.submit()} retryLabel="Réessayer avec la même clé" />
              <div className="form-actions">
                <button type="submit" className="btn btn-primary" disabled={order.pending}>
                  {order.pending ? 'Envoi…' : 'Valider le virement'}
                </button>
              </div>
            </form>
          )}
        </Card>
        <Card title="Comment ça marche">
          <ol className="explain">
            <li>Votre ordre part avec une <b>clé d'idempotence</b> unique : <code className="key">{order.currentKey}</code></li>
            <li>Le service des opérations l'enregistre et répond <b>202 Accepted</b> : « reçu, pas encore exécuté ».</li>
            <li>Le grand livre l'exécute via Kafka : il vérifie la provision et le plafond, puis débite et crédite <b>ensemble</b>.</li>
            <li>Si le réseau coupe, « Réessayer » renvoie la <b>même clé</b> : le serveur reconnaît la demande et ne paie jamais deux fois.</li>
          </ol>
          {!session.isStaff && <Notice>Vous recevrez un e-mail dès que le virement sera exécuté.</Notice>}
        </Card>
      </div>
    </>
  );
}
