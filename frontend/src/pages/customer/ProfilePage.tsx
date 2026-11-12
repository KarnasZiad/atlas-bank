import { useQuery } from '@tanstack/react-query';
import { customersApi } from '../../api/endpoints';
import { ContactForm } from '../../components/ContactForm';
import { ErrorAlert, Loading } from '../../components/Feedback';
import { Badge, Card, Detail, PageHeader } from '../../components/ui';
import { formatDate } from '../../lib/format';
import { customerStatusLabel, customerStatusTone, languageLabel } from '../../lib/labels';

/**
 * « Mon profil » : GET /api/v1/customers/me. Le serveur trouve le dossier grâce au claim customer_id
 * du jeton ; le client ne peut pas demander le dossier d'un autre.
 */
export function ProfilePage() {
  const me = useQuery({ queryKey: ['me'], queryFn: customersApi.me });
  if (me.isPending) {
    return <Loading />;
  }
  if (me.isError) {
    return <ErrorAlert error={me.error} />;
  }
  const customer = me.data;
  return (
    <>
      <PageHeader title="Mon profil" subtitle="Vos informations telles que la banque les connaît." />
      <div className="grid-2">
        <Card title="Identité">
          <dl className="details">
            <Detail label="Nom">{customer.firstName} {customer.lastName}</Detail>
            <Detail label="Date de naissance">{formatDate(customer.dateOfBirth)}</Detail>
            <Detail label="CIN">
              <span className="mono">{customer.nationalId}</span>
              <span className="sub">Masqué : le numéro complet est chiffré dans la base de la banque.</span>
            </Detail>
            <Detail label="E-mail">{customer.email}</Detail>
            <Detail label="Langue">{languageLabel[customer.preferredLanguage] ?? customer.preferredLanguage}</Detail>
            <Detail label="Statut"><Badge tone={customerStatusTone[customer.status]}>{customerStatusLabel[customer.status]}</Badge></Detail>
            <Detail label="Client depuis">{formatDate(customer.verifiedAt ?? customer.createdAt)}</Detail>
          </dl>
        </Card>
        <Card title="Coordonnées">
          <ContactForm customer={customer} />
        </Card>
      </div>
    </>
  );
}
