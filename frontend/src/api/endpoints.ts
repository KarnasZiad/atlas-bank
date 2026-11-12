import { get, post, put, request } from './client';
import type {
  Account,
  AccountStatus,
  AccountType,
  Address,
  CashRequest,
  Customer,
  CustomerRevision,
  CustomerStatus,
  CustomerSummary,
  LedgerEntry,
  Notification,
  OrderStatus,
  OrderSubmission,
  Page,
  PaymentOrder,
  ReconciliationReport,
  RegisterCustomer,
  TransactionKind,
  TransferRequest,
} from './types';

/** Pagination envoyée telle quelle à Spring Data : ?page=0&size=20&sort=champ,desc */
export interface Paging {
  page?: number;
  size?: number;
  sort?: string;
}

// --- /api/v1/customers (customer-service) --------------------------------------------------------

export const customersApi = {
  search: (filters: { query?: string; status?: CustomerStatus | '' } & Paging) =>
    get<Page<CustomerSummary>>('/api/v1/customers', { sort: 'lastName', ...filters }),
  get: (id: string) => get<Customer>(`/api/v1/customers/${id}`),
  me: () => get<Customer>('/api/v1/customers/me'),
  register: (customer: RegisterCustomer) => post<Customer>('/api/v1/customers', customer),
  updateContact: (id: string, contact: { phoneNumber: string; address: Address }) =>
    put<Customer>(`/api/v1/customers/${id}/contact`, contact),
  verify: (id: string) => post<Customer>(`/api/v1/customers/${id}/verification`),
  suspend: (id: string, reason: string) => post<Customer>(`/api/v1/customers/${id}/suspension`, { reason }),
  reinstate: (id: string) => post<Customer>(`/api/v1/customers/${id}/reinstatement`),
  close: (id: string, reason: string) => post<Customer>(`/api/v1/customers/${id}/closure`, { reason }),
  history: (id: string, paging: Paging = {}) =>
    get<Page<CustomerRevision>>(`/api/v1/customers/${id}/history`, { ...paging }),
};

// --- /api/v1/accounts (account-service) ----------------------------------------------------------

export const accountsApi = {
  /** Pour un client, le serveur ne renvoie QUE ses comptes, quel que soit le filtre envoyé. */
  list: (filters: { customerId?: string; status?: AccountStatus | ''; type?: AccountType | '' } & Paging = {}) =>
    get<Page<Account>>('/api/v1/accounts', { size: 50, ...filters }),
  get: (id: string) => get<Account>(`/api/v1/accounts/${id}`),
  statement: (id: string, filters: { from?: string; to?: string } & Paging = {}) =>
    get<Page<LedgerEntry>>(`/api/v1/accounts/${id}/entries`, { size: 20, ...filters }),
  open: (customerId: string, type: AccountType, currency: string) =>
    post<Account>('/api/v1/accounts', { customerId, type, currency }),
  freeze: (id: string, reason: string) => post<Account>(`/api/v1/accounts/${id}/freeze`, { reason }),
  unfreeze: (id: string) => post<Account>(`/api/v1/accounts/${id}/unfreeze`),
  close: (id: string, reason: string) => post<Account>(`/api/v1/accounts/${id}/closure`, { reason }),
  reconciliation: () => get<ReconciliationReport>('/api/v1/accounts/reconciliation'),
};

// --- /api/v1/transactions (transaction-service) --------------------------------------------------

/**
 * Prise d'ordre : réponse 202 Accepted. L'en-tête Idempotency-Key est obligatoire ; l'en-tête de
 * réponse Idempotent-Replayed dit si le serveur a renvoyé un ordre déjà existant au lieu d'en créer un.
 */
async function submitOrder(path: string, body: unknown, idempotencyKey: string): Promise<OrderSubmission> {
  const response = await request<PaymentOrder>(path, {
    method: 'POST',
    body,
    headers: { 'Idempotency-Key': idempotencyKey },
  });
  return { order: response.data, replayed: response.headers.get('Idempotent-Replayed') === 'true' };
}

export const transactionsApi = {
  transfer: (order: TransferRequest, idempotencyKey: string) =>
    submitOrder('/api/v1/transactions/transfers', order, idempotencyKey),
  deposit: (order: CashRequest, idempotencyKey: string) =>
    submitOrder('/api/v1/transactions/deposits', order, idempotencyKey),
  withdraw: (order: CashRequest, idempotencyKey: string) =>
    submitOrder('/api/v1/transactions/withdrawals', order, idempotencyKey),
  get: (id: string) => get<PaymentOrder>(`/api/v1/transactions/${id}`),
  search: (
    filters: { status?: OrderStatus | ''; kind?: TransactionKind | ''; accountNumber?: string; from?: string; to?: string } & Paging,
  ) => get<Page<PaymentOrder>>('/api/v1/transactions', { size: 20, ...filters }),
  approve: (id: string) => post<PaymentOrder>(`/api/v1/transactions/${id}/approval`),
  refuse: (id: string) => post<PaymentOrder>(`/api/v1/transactions/${id}/refusal`),
};

// --- /api/v1/notifications (notification-service) ------------------------------------------------

export const notificationsApi = {
  list: (filters: { customerId?: string } & Paging = {}) =>
    get<Page<Notification>>('/api/v1/notifications', { size: 20, ...filters }),
};
