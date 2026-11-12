import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { customersApi } from '../api/endpoints';
import { describeError } from '../api/problem';
import type { Customer } from '../api/types';
import { ErrorAlert, Notice } from './Feedback';
import { Field } from './ui';

/**
 * Changement de téléphone et d'adresse (client ou agent). Le serveur prévient le client par e-mail, à
 * son adresse qui, elle, n'a pas changé : un changement de téléphone inattendu est souvent le début
 * d'une prise de contrôle de compte.
 */
export function ContactForm({ customer, onDone }: { customer: Customer; onDone?: () => void }) {
  const queryClient = useQueryClient();
  const [phoneNumber, setPhoneNumber] = useState(customer.phoneNumber);
  const [address, setAddress] = useState(customer.address);
  const save = useMutation({
    mutationFn: () => customersApi.updateContact(customer.id, { phoneNumber, address }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['customer', customer.id], updated);
      void queryClient.invalidateQueries({ queryKey: ['me'] });
      onDone?.();
    },
  });
  const errors = describeError(save.error).fieldErrors;

  const submit = (event: FormEvent) => {
    event.preventDefault();
    save.mutate();
  };

  return (
    <form className="form" onSubmit={submit}>
      <Field label="Téléphone" error={errors.phoneNumber} hint="Format national (06…) ou international (+212…).">
        <input required value={phoneNumber} onChange={(event) => setPhoneNumber(event.target.value)} />
      </Field>
      <Field label="Adresse" error={errors['address.line']}>
        <input required value={address.line} onChange={(event) => setAddress({ ...address, line: event.target.value })} />
      </Field>
      <div className="form-row">
        <Field label="Ville" error={errors['address.city']}>
          <input required value={address.city} onChange={(event) => setAddress({ ...address, city: event.target.value })} />
        </Field>
        <Field label="Code postal" error={errors['address.postalCode']}>
          <input required value={address.postalCode} onChange={(event) => setAddress({ ...address, postalCode: event.target.value })} />
        </Field>
        <Field label="Pays" error={errors['address.country']}>
          <input required maxLength={2} value={address.country} onChange={(event) => setAddress({ ...address, country: event.target.value.toUpperCase() })} />
        </Field>
      </div>
      <ErrorAlert error={save.error} />
      {save.isSuccess && <Notice tone="success">Coordonnées enregistrées. Un e-mail de sécurité a été envoyé au client.</Notice>}
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={save.isPending}>
          {save.isPending ? 'Enregistrement…' : 'Enregistrer'}
        </button>
      </div>
    </form>
  );
}
