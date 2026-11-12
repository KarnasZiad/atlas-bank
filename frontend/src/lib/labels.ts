import type {
  AccountStatus,
  AccountType,
  CustomerStatus,
  NotificationType,
  OrderStatus,
  TransactionKind,
} from '../api/types';

/** Ton visuel d'un badge : la couleur dit l'état d'un coup d'œil. */
export type Tone = 'neutral' | 'positive' | 'warning' | 'negative' | 'info';

export const customerStatusLabel: Record<CustomerStatus, string> = {
  PENDING_VERIFICATION: 'En attente de KYC',
  ACTIVE: 'Actif',
  SUSPENDED: 'Suspendu',
  CLOSED: 'Clôturé',
};

export const customerStatusTone: Record<CustomerStatus, Tone> = {
  PENDING_VERIFICATION: 'warning',
  ACTIVE: 'positive',
  SUSPENDED: 'negative',
  CLOSED: 'neutral',
};

export const accountTypeLabel: Record<AccountType, string> = {
  CURRENT: 'Compte courant',
  SAVINGS: 'Compte épargne',
  TELLER_CASH: "Caisse de l'agence",
};

export const accountStatusLabel: Record<AccountStatus, string> = {
  ACTIVE: 'Actif',
  FROZEN: 'Gelé (débits bloqués)',
  CLOSED: 'Clôturé',
};

export const accountStatusTone: Record<AccountStatus, Tone> = {
  ACTIVE: 'positive',
  FROZEN: 'negative',
  CLOSED: 'neutral',
};

export const orderStatusLabel: Record<OrderStatus, string> = {
  AWAITING_APPROVAL: "En attente d'approbation",
  PENDING: 'En cours',
  COMPLETED: 'Exécuté',
  REJECTED: 'Refusé',
};

export const orderStatusTone: Record<OrderStatus, Tone> = {
  AWAITING_APPROVAL: 'warning',
  PENDING: 'info',
  COMPLETED: 'positive',
  REJECTED: 'negative',
};

export const transactionKindLabel: Record<TransactionKind, string> = {
  TRANSFER: 'Virement',
  DEPOSIT: 'Versement espèces',
  WITHDRAWAL: 'Retrait espèces',
};

export const notificationTypeLabel: Record<NotificationType, string> = {
  ACCOUNT_OPENED: 'Compte ouvert',
  ACCOUNT_STATUS_CHANGED: 'Statut de compte',
  TRANSFER_SENT: 'Virement émis',
  TRANSFER_RECEIVED: 'Virement reçu',
  DEPOSIT_RECEIVED: 'Versement',
  WITHDRAWAL_MADE: 'Retrait',
  TRANSACTION_REJECTED: 'Opération refusée',
  CONTACT_DETAILS_CHANGED: 'Alerte de sécurité',
};

export const languageLabel: Record<string, string> = {
  fr: 'Français',
  en: 'English',
  ar: 'العربية',
};
