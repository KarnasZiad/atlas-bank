import { useMutation } from '@tanstack/react-query';
import { accountsApi } from '../../api/endpoints';
import { ErrorAlert, Notice } from '../../components/Feedback';
import { Card, PageHeader } from '../../components/ui';
import { formatDateTime } from '../../lib/format';

/**
 * Contrôle des livres (ADMIN) : chaque solde est-il égal à la somme de ses écritures, et chaque
 * transaction est-elle équilibrée (débits = crédits) ? Le même contrôle tourne toutes les heures.
 */
export function ReconciliationPage() {
  const check = useMutation({ mutationFn: accountsApi.reconciliation });
  const report = check.data;
  return (
    <>
      <PageHeader title="Contrôle des livres" subtitle="La réconciliation prouve que la comptabilité en partie double est juste." />
      <Card>
        <p>
          Le contrôle est calculé par PostgreSQL sur tout le grand livre. Il s'exécute aussi toutes les heures et alimente
          la métrique <code>atlas.ledger.discrepancies</code>, sur laquelle une alerte se déclenche au-dessus de 0.
        </p>
        <div className="form-actions">
          <button type="button" className="btn btn-primary" disabled={check.isPending} onClick={() => check.mutate()}>
            {check.isPending ? 'Contrôle en cours…' : 'Lancer le contrôle'}
          </button>
        </div>
        <ErrorAlert error={check.error} />
        {report && (
          report.accountsNotMatchingTheirEntries.length === 0 && report.unbalancedTransactions.length === 0 ? (
            <Notice tone="success">
              Livres équilibrés au {formatDateTime(report.checkedAt)} : chaque solde correspond à ses écritures et chaque transaction est équilibrée.
            </Notice>
          ) : (
            <div className="alert alert-error" role="alert">
              <p><strong>Incohérence détectée</strong> au {formatDateTime(report.checkedAt)}.</p>
              {report.accountsNotMatchingTheirEntries.length > 0 && (
                <p>Comptes dont le solde ne correspond pas aux écritures : <code>{report.accountsNotMatchingTheirEntries.join(', ')}</code></p>
              )}
              {report.unbalancedTransactions.length > 0 && (
                <p>Transactions déséquilibrées : <code>{report.unbalancedTransactions.join(', ')}</code></p>
              )}
            </div>
          )
        )}
      </Card>
    </>
  );
}
