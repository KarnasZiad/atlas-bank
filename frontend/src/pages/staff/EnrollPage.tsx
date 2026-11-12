import { useMutation } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router';
import { customersApi } from '../../api/endpoints';
import { describeError } from '../../api/problem';
import type { RegisterCustomer } from '../../api/types';
import { ErrorAlert } from '../../components/Feedback';
import { Card, Field, PageHeader } from '../../components/ui';

const EMPTY: RegisterCustomer = {
  firstName: '',
  lastName: '',
  email: '',
  phoneNumber: '',
  dateOfBirth: '',
  nationalId: '',
  preferredLanguage: 'fr',
  address: { line: '', city: '', postalCode: '', country: 'MA' },
};

/**
 * Enrôlement (le « maker » du contrôle 4 yeux). L'agent saisit le dossier après avoir vérifié les
 * pièces ; le client est créé « en attente de KYC » et un AUTRE agent devra le valider.
 *
 * Deux niveaux de validation :
 *  - dans le navigateur (required, type="email", pattern…) : confort, retour immédiat ;
 *  - sur le serveur (@Valid, règles du domaine) : la seule qui compte. Ses erreurs reviennent par champ
 *    (VALIDATION_FAILED) ou par code métier (CIN déjà enregistré, client mineur…).
 */
export function EnrollPage() {
  const navigate = useNavigate();
  const [form, setForm] = useState<RegisterCustomer>(EMPTY);
  const register = useMutation({
    mutationFn: () => customersApi.register(form),
    onSuccess: (customer) => navigate(`/agence/clients/${customer.id}`),
  });
  const errors = describeError(register.error).fieldErrors;

  const set = (field: keyof Omit<RegisterCustomer, 'address'>) => (value: string) => setForm({ ...form, [field]: value });
  const setAddress = (field: keyof RegisterCustomer['address']) => (value: string) =>
    setForm({ ...form, address: { ...form.address, [field]: value } });

  const submit = (event: FormEvent) => {
    event.preventDefault();
    register.mutate();
  };

  return (
    <>
      <PageHeader title="Enrôler un client" subtitle="Saisissez le dossier après avoir contrôlé la CIN et le justificatif de domicile." />
      <Card>
        <form className="form" onSubmit={submit}>
          <fieldset>
            <legend>Identité</legend>
            <div className="form-row">
              <Field label="Prénom" error={errors.firstName}>
                <input required maxLength={100} value={form.firstName} onChange={(event) => set('firstName')(event.target.value)} />
              </Field>
              <Field label="Nom" error={errors.lastName}>
                <input required maxLength={100} value={form.lastName} onChange={(event) => set('lastName')(event.target.value)} />
              </Field>
            </div>
            <div className="form-row">
              <Field label="Date de naissance" error={errors.dateOfBirth} hint="18 ans minimum.">
                <input required type="date" value={form.dateOfBirth} onChange={(event) => set('dateOfBirth')(event.target.value)} />
              </Field>
              <Field label="Numéro de CIN" error={errors.nationalId} hint="1 ou 2 lettres puis 3 à 6 chiffres. Chiffré par le serveur.">
                <input required pattern="[A-Za-z]{1,2}[0-9]{3,6}" value={form.nationalId} onChange={(event) => set('nationalId')(event.target.value)} className="mono" />
              </Field>
            </div>
          </fieldset>
          <fieldset>
            <legend>Coordonnées</legend>
            <div className="form-row">
              <Field label="E-mail" error={errors.email} hint="Sert d'identifiant de connexion.">
                <input required type="email" maxLength={254} value={form.email} onChange={(event) => set('email')(event.target.value)} />
              </Field>
              <Field label="Téléphone" error={errors.phoneNumber}>
                <input required value={form.phoneNumber} onChange={(event) => set('phoneNumber')(event.target.value)} placeholder="06 12 34 56 78" />
              </Field>
              <Field label="Langue des e-mails" error={errors.preferredLanguage}>
                <select value={form.preferredLanguage} onChange={(event) => set('preferredLanguage')(event.target.value)}>
                  <option value="fr">Français</option>
                  <option value="ar">العربية</option>
                  <option value="en">English</option>
                </select>
              </Field>
            </div>
            <Field label="Adresse" error={errors['address.line']}>
              <input required maxLength={200} value={form.address.line} onChange={(event) => setAddress('line')(event.target.value)} />
            </Field>
            <div className="form-row">
              <Field label="Ville" error={errors['address.city']}>
                <input required maxLength={100} value={form.address.city} onChange={(event) => setAddress('city')(event.target.value)} />
              </Field>
              <Field label="Code postal" error={errors['address.postalCode']}>
                <input required maxLength={20} value={form.address.postalCode} onChange={(event) => setAddress('postalCode')(event.target.value)} />
              </Field>
              <Field label="Pays" error={errors['address.country']}>
                <input required maxLength={2} value={form.address.country} onChange={(event) => setAddress('country')(event.target.value.toUpperCase())} />
              </Field>
            </div>
          </fieldset>
          <ErrorAlert error={register.error} />
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={register.isPending}>
              {register.isPending ? 'Enregistrement…' : 'Enregistrer le dossier'}
            </button>
          </div>
        </form>
      </Card>
    </>
  );
}
