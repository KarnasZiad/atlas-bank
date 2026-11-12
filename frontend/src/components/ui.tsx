import type { ReactNode } from 'react';
import type { Page } from '../api/types';
import { formatIban, formatMoney } from '../lib/format';
import type { Tone } from '../lib/labels';

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: ReactNode; actions?: ReactNode }) {
  return (
    <header className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="page-subtitle">{subtitle}</p>}
      </div>
      {actions && <div className="page-actions">{actions}</div>}
    </header>
  );
}

export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

export function Money({ amount, currency, signed = false }: { amount: number; currency: string; signed?: boolean }) {
  const negative = amount < 0;
  return (
    <span className={`money${negative ? ' money-negative' : ''}`}>
      {signed && amount > 0 ? '+' : ''}
      {formatMoney(amount, currency)}
    </span>
  );
}

export function Iban({ value }: { value: string | null | undefined }) {
  return <span className="mono">{formatIban(value)}</span>;
}

export function Card({ title, actions, children }: { title?: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <section className="card">
      {(title || actions) && (
        <div className="card-head">
          {title && <h2>{title}</h2>}
          {actions}
        </div>
      )}
      {children}
    </section>
  );
}

/** Une ligne « libellé : valeur » dans une fiche. */
export function Detail({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div className="detail">
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}

export function Field({ label, error, hint, children }: {
  label: string;
  error?: string;
  hint?: ReactNode;
  children: ReactNode;
}) {
  return (
    <label className={`field${error ? ' field-invalid' : ''}`}>
      <span className="field-label">{label}</span>
      {children}
      {hint && !error && <span className="field-hint">{hint}</span>}
      {error && <span className="field-error">{error}</span>}
    </label>
  );
}

/** Navigation dans une liste paginée (la page 0 est la première, comme côté Spring). */
export function Pagination<T>({ page, onChange }: { page: Page<T>['page']; onChange: (page: number) => void }) {
  if (page.totalPages <= 1) {
    return null;
  }
  return (
    <nav className="pagination" aria-label="Pagination">
      <button type="button" className="btn btn-small btn-ghost" disabled={page.number === 0} onClick={() => onChange(page.number - 1)}>
        Précédent
      </button>
      <span>
        Page {page.number + 1} / {page.totalPages} · {page.totalElements} éléments
      </span>
      <button
        type="button"
        className="btn btn-small btn-ghost"
        disabled={page.number + 1 >= page.totalPages}
        onClick={() => onChange(page.number + 1)}
      >
        Suivant
      </button>
    </nav>
  );
}
