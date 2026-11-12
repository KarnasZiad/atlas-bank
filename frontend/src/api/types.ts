/**
 * Les formes JSON renvoyées par les services, recopiées depuis leurs records Java "…View".
 * Les montants (BigDecimal) arrivent en nombres, les dates (Instant) en chaînes ISO 8601,
 * les dates sans heure (LocalDate) en "AAAA-MM-JJ".
 */

/** Forme stable des listes paginées (PagedModel de Spring Data). */
export interface Page<T> {
  content: T[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
}

// --- customer-service ----------------------------------------------------------------------------

export type CustomerStatus = 'PENDING_VERIFICATION' | 'ACTIVE' | 'SUSPENDED' | 'CLOSED';

export interface Address {
  line: string;
  city: string;
  postalCode: string;
  country: string;
}

export interface Customer {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  dateOfBirth: string;
  /** Toujours masqué par le serveur : "******56". */
  nationalId: string;
  address: Address;
  preferredLanguage: string;
  status: CustomerStatus;
  /** Note interne : null quand c'est le client qui lit son dossier. */
  statusReason: string | null;
  identityProvisioned: boolean;
  /** Login de l'agent qui a enrôlé (personnel seulement). */
  registeredBy: string | null;
  verifiedBy: string | null;
  verifiedAt: string | null;
  createdAt: string;
  lastModifiedAt: string;
}

export interface CustomerSummary {
  id: string;
  fullName: string;
  email: string;
  status: CustomerStatus;
  createdAt: string;
}

export interface CustomerRevision {
  revision: number;
  changedAt: string;
  changedBy: string;
  changeType: 'INSERT' | 'UPDATE' | 'DELETE';
  status: CustomerStatus | null;
  statusReason: string | null;
  email: string | null;
  phoneNumber: string | null;
}

export interface RegisterCustomer {
  firstName: string;
  lastName: string;
  email: string;
  phoneNumber: string;
  dateOfBirth: string;
  nationalId: string;
  preferredLanguage: string;
  address: Address;
}

// --- account-service -----------------------------------------------------------------------------

export type AccountType = 'CURRENT' | 'SAVINGS' | 'TELLER_CASH';
export type AccountStatus = 'ACTIVE' | 'FROZEN' | 'CLOSED';
export type TransactionKind = 'DEPOSIT' | 'WITHDRAWAL' | 'TRANSFER';

export interface Account {
  id: string;
  accountNumber: string;
  customerId: string;
  type: AccountType;
  currency: string;
  balance: number;
  /** Solde + découvert autorisé : ce qui peut sortir maintenant. */
  availableBalance: number;
  overdraftLimit: number;
  dailyDebitLimit: number;
  status: AccountStatus;
  statusReason: string | null;
  openedAt: string;
  closedAt: string | null;
}

export interface LedgerEntry {
  id: string;
  transactionId: string;
  transactionReference: string;
  kind: TransactionKind;
  direction: 'DEBIT' | 'CREDIT';
  amount: number;
  currency: string;
  balanceAfter: number;
  counterpartyAccountNumber: string | null;
  description: string | null;
  bookedAt: string;
}

export interface ReconciliationReport {
  checkedAt: string;
  accountsNotMatchingTheirEntries: string[];
  unbalancedTransactions: string[];
}

// --- transaction-service -------------------------------------------------------------------------

export type OrderStatus = 'AWAITING_APPROVAL' | 'PENDING' | 'COMPLETED' | 'REJECTED';

export interface PaymentOrder {
  id: string;
  reference: string;
  kind: TransactionKind;
  status: OrderStatus;
  sourceAccountNumber: string | null;
  targetAccountNumber: string | null;
  amount: number;
  currency: string;
  description: string | null;
  initiatedBy: string;
  initiatorType: 'CUSTOMER' | 'EMPLOYEE';
  rejectionCode: string | null;
  approvalRequired: boolean;
  reviewedBy: string | null;
  reviewedAt: string | null;
  requestedAt: string;
  completedAt: string | null;
}

export interface TransferRequest {
  sourceAccountNumber: string;
  targetAccountNumber: string;
  amount: number;
  currency: string;
  description?: string;
}

export interface CashRequest {
  accountNumber: string;
  amount: number;
  currency: string;
  description?: string;
}

/** Résultat d'une prise d'ordre : l'ordre, et s'il s'agit d'un rejeu (même Idempotency-Key). */
export interface OrderSubmission {
  order: PaymentOrder;
  replayed: boolean;
}

// --- notification-service ------------------------------------------------------------------------

export type NotificationType =
  | 'ACCOUNT_OPENED'
  | 'ACCOUNT_STATUS_CHANGED'
  | 'TRANSFER_SENT'
  | 'TRANSFER_RECEIVED'
  | 'DEPOSIT_RECEIVED'
  | 'WITHDRAWAL_MADE'
  | 'TRANSACTION_REJECTED'
  | 'CONTACT_DETAILS_CHANGED';

export interface Notification {
  id: string;
  customerId: string;
  type: NotificationType;
  channel: 'EMAIL';
  destination: string;
  subject: string;
  body: string;
  sentAt: string;
}
