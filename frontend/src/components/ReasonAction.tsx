import { useState, type FormEvent } from 'react';

/**
 * Bouton d'une action sensible qui exige un motif (suspension, gel, clôture). Le motif est une note
 * interne de la banque : il est gardé dans l'audit et n'est jamais montré au client.
 */
export function ReasonAction({ label, confirmLabel, danger = false, disabled = false, pending, onConfirm }: {
  label: string;
  confirmLabel: string;
  danger?: boolean;
  disabled?: boolean;
  pending: boolean;
  onConfirm: (reason: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');

  if (!open) {
    return (
      <button type="button" className={`btn ${danger ? 'btn-danger' : 'btn-secondary'}`} disabled={disabled} onClick={() => setOpen(true)}>
        {label}
      </button>
    );
  }

  const submit = (event: FormEvent) => {
    event.preventDefault();
    onConfirm(reason.trim());
  };

  return (
    <form className="reason-form" onSubmit={submit}>
      <label className="field">
        <span className="field-label">Motif (note interne, jamais montrée au client)</span>
        <textarea required maxLength={500} rows={2} value={reason} onChange={(event) => setReason(event.target.value)} />
      </label>
      <div className="form-actions">
        <button type="submit" className={`btn ${danger ? 'btn-danger' : 'btn-primary'}`} disabled={pending || !reason.trim()}>
          {pending ? 'Envoi…' : confirmLabel}
        </button>
        <button type="button" className="btn btn-ghost" onClick={() => setOpen(false)}>Annuler</button>
      </div>
    </form>
  );
}
