import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router';
import { customersApi } from '../../api/endpoints';
import type { CustomerStatus } from '../../api/types';
import { Empty, ErrorAlert, Loading } from '../../components/Feedback';
import { Badge, Card, PageHeader, Pagination } from '../../components/ui';
import { formatDate } from '../../lib/format';
import { customerStatusLabel, customerStatusTone } from '../../lib/labels';

/** L'annuaire des clients : recherche par nom ou e-mail, filtre par statut, pagination côté serveur. */
export function CustomersPage() {
  const [params, setParams] = useSearchParams();
  const query = params.get('query') ?? '';
  const status = (params.get('status') ?? '') as CustomerStatus | '';
  const page = Number(params.get('page') ?? 0);
  const [draft, setDraft] = useState(query);

  const customers = useQuery({
    queryKey: ['customers', { query, status, page }],
    queryFn: () => customersApi.search({ query, status, page }),
  });

  const update = (changes: Record<string, string>) => {
    const next = new URLSearchParams(params);
    for (const [name, value] of Object.entries(changes)) {
      if (value) {
        next.set(name, value);
      } else {
        next.delete(name);
      }
    }
    setParams(next);
  };

  const search = (event: FormEvent) => {
    event.preventDefault();
    update({ query: draft.trim(), page: '' });
  };

  return (
    <>
      <PageHeader
        title="Clients"
        subtitle="Les dossiers clients de la banque."
        actions={<Link to="/agence/clients/nouveau" className="btn btn-primary">Enrôler un client</Link>}
      />
      <Card
        actions={
          <form className="filters" onSubmit={search}>
            <input aria-label="Recherche" placeholder="Nom, prénom ou e-mail" value={draft} onChange={(event) => setDraft(event.target.value)} />
            <select aria-label="Statut" value={status} onChange={(event) => update({ status: event.target.value, page: '' })}>
              <option value="">Tous les statuts</option>
              {(Object.keys(customerStatusLabel) as CustomerStatus[]).map((value) => (
                <option key={value} value={value}>{customerStatusLabel[value]}</option>
              ))}
            </select>
            <button type="submit" className="btn btn-secondary">Rechercher</button>
          </form>
        }
      >
        {customers.isPending && <Loading />}
        <ErrorAlert error={customers.error} />
        {customers.data?.content.length === 0 && <Empty>Aucun client ne correspond.</Empty>}
        {customers.data && customers.data.content.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead><tr><th>Client</th><th>E-mail</th><th>Statut</th><th>Enrôlé le</th></tr></thead>
              <tbody>
                {customers.data.content.map((customer) => (
                  <tr key={customer.id}>
                    <td><Link className="link" to={`/agence/clients/${customer.id}`}>{customer.fullName}</Link></td>
                    <td>{customer.email}</td>
                    <td><Badge tone={customerStatusTone[customer.status]}>{customerStatusLabel[customer.status]}</Badge></td>
                    <td>{formatDate(customer.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {customers.data && <Pagination page={customers.data.page} onChange={(next) => update({ page: String(next) })} />}
      </Card>
    </>
  );
}
