import { useState, type FormEvent } from 'react';
import { transactionsApi } from '../../api/endpoints';
import { describeError } from '../../api/problem';
import { ErrorAlert } from '../../components/Feedback';
import { OrderTracker } from '../../components/OrderTracker';
import { Card, Field, PageHeader } from '../../components/ui';
import { useOrderSubmission } from '../../hooks/useOrderSubmission';
import { normalizeIban } from '../../lib/format';

type Operation = 'deposit' | 'withdrawal';

/**
 * Le guichet espèces, réservé aux agents. En partie double, la contrepartie de chaque opération est la
 * caisse de l'agence : un versement fait entrer des billets dans la caisse, un retrait en fait sortir.
 */
export function CashPage() {
  const [operation, setOperation] = useState<Operation>('deposit');
  const [accountNumber, setAccountNumber] = useState('');
  const [amount, setAmount] = useState('');
  const [currency, setCurrency] = useState('MAD');
  const [description, setDescription] = useState('');

  const order = useOrderSubmission((key) => {
    const body = {
      accountNumber: normalizeIban(accountNumber),
      amount: Number(amount),
      currency,
      description: description.trim() || undefined,
    };
    return operation === 'deposit' ? transactionsApi.deposit(body, key) : transactionsApi.withdraw(body, key);
  });
  const errors = describeError(order.error).fieldErrors;

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
      <PageHeader title="Guichet espèces" subtitle="Versements et retraits au comptoir de l'agence." />
      <div className="grid-2">
        <Card>
          <div className="tabs" role="tablist">
            <button type="button" role="tab" aria-selected={operation === 'deposit'} className={`tab${operation === 'deposit' ? ' active' : ''}`}
              onClick={() => { setOperation('deposit'); order.reset(); }}>
              Versement
            </button>
            <button type="button" role="tab" aria-selected={operation === 'withdrawal'} className={`tab${operation === 'withdrawal' ? ' active' : ''}`}
              onClick={() => { setOperation('withdrawal'); order.reset(); }}>
              Retrait
            </button>
          </div>
          {order.submission ? (
            <div className="stack">
              <OrderTracker initial={order.submission.order} replayed={order.submission.replayed} detailPath={`/agence/operations/${order.submission.order.id}`} />
              <button type="button" className="btn btn-secondary" onClick={startOver}>Nouvelle opération</button>
            </div>
          ) : (
            <form className="form" onSubmit={submit}>
              <Field label="Compte du client (IBAN)" error={errors.accountNumber}>
                <input required value={accountNumber} onChange={(event) => setAccountNumber(event.target.value)} className="mono" placeholder="MA64 …" />
              </Field>
              <div className="form-row">
                <Field label="Montant" error={errors.amount}>
                  <input required type="number" min="0.01" step="0.01" value={amount} onChange={(event) => setAmount(event.target.value)} />
                </Field>
                <Field label="Devise" error={errors.currency}>
                  <select value={currency} onChange={(event) => setCurrency(event.target.value)}>
                    <option>MAD</option><option>EUR</option><option>USD</option>
                  </select>
                </Field>
              </div>
              <Field label="Libellé (facultatif)" error={errors.description}>
                <input maxLength={140} value={description} onChange={(event) => setDescription(event.target.value)} />
              </Field>
              <ErrorAlert error={order.error} onRetry={() => void order.submit()} retryLabel="Réessayer avec la même clé" />
              <div className="form-actions">
                <button type="submit" className="btn btn-primary" disabled={order.pending}>
                  {order.pending ? 'Envoi…' : operation === 'deposit' ? 'Enregistrer le versement' : 'Enregistrer le retrait'}
                </button>
              </div>
            </form>
          )}
        </Card>
        <Card title="Les écritures produites">
          <div className="table-wrap">
            <table>
              <thead><tr><th>Compte</th><th>Débit</th><th>Crédit</th></tr></thead>
              <tbody>
                {operation === 'deposit' ? (
                  <>
                    <tr><td>Caisse de l'agence</td><td className="num">{amount || '—'}</td><td /></tr>
                    <tr><td>Compte du client</td><td /><td className="num">{amount || '—'}</td></tr>
                  </>
                ) : (
                  <>
                    <tr><td>Compte du client</td><td className="num">{amount || '—'}</td><td /></tr>
                    <tr><td>Caisse de l'agence</td><td /><td className="num">{amount || '—'}</td></tr>
                  </>
                )}
              </tbody>
            </table>
          </div>
          <p className="hint">
            {operation === 'deposit'
              ? 'Les billets entrent dans la caisse et le compte du client est crédité du même montant.'
              : "Les billets sortent de la caisse : impossible d'en rendre plus qu'elle n'en contient (INSUFFICIENT_CASH)."}
          </p>
          <p className="hint">Au-delà de 10 000 MAD, ou de 50 000 MAD cumulés aujourd'hui, l'opération attend l'approbation d'un collègue.</p>
        </Card>
      </div>
    </>
  );
}
