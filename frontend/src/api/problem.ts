/**
 * Les erreurs de l'API suivent la RFC 9457 ("Problem Details") : même forme pour tous les services,
 * avec un "code" stable sur lequel l'interface peut s'appuyer, et un "traceId" pour le support.
 */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  traceId?: string;
  timestamp?: string;
  /** Présent pour VALIDATION_FAILED : un message par champ invalide. */
  errors?: { field: string; message: string }[];
}

/** Erreur levée par le client HTTP pour toute réponse en échec (ou réseau coupé : status 0). */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ProblemDetail;

  constructor(status: number, problem: ProblemDetail) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`);
    this.name = 'ApiError';
    this.status = status;
    this.problem = problem;
  }

  get code(): string {
    return this.problem.code ?? `HTTP_${this.status}`;
  }

  /**
   * Réessayer peut réussir : réseau coupé, service indisponible (503, disjoncteur de la gateway),
   * trop de requêtes (429). Pour un ordre de paiement, on réessaie avec LA MÊME clé d'idempotence.
   */
  get isRetryable(): boolean {
    return this.status === 0 || this.status === 429 || this.status === 503;
  }
}

/** Traduction des codes métier en phrases pour l'utilisateur. */
const MESSAGES: Record<string, string> = {
  NETWORK_ERROR: 'Le serveur est injoignable. Vérifiez votre connexion puis réessayez.',
  TOO_MANY_REQUESTS: 'Trop de requêtes en peu de temps. Patientez quelques secondes puis réessayez.',
  SERVICE_UNAVAILABLE: 'Le service est momentanément indisponible. Réessayez dans quelques secondes.',
  INVALID_TOKEN: 'Votre session a expiré. Reconnectez-vous.',
  HTTP_401: 'Votre session a expiré. Reconnectez-vous.',
  ACCESS_DENIED: "Votre profil ne permet pas cette opération.",
  VALIDATION_FAILED: 'Certains champs sont invalides.',
  MALFORMED_REQUEST: 'La requête est mal formée.',
  CONCURRENT_MODIFICATION: "Quelqu'un a modifié cet élément en même temps que vous. Rechargez puis réessayez.",
  DUPLICATE_RESOURCE: 'Cet élément existe déjà ou est en cours de création.',
  INTERNAL_ERROR: 'Une erreur inattendue est survenue. Communiquez le numéro de trace au support.',
  CUSTOMER_PROFILE_NOT_LINKED: "Votre compte de connexion n'est rattaché à aucun dossier client.",
  // Clients
  CUSTOMER_NOT_FOUND: 'Client introuvable.',
  EMAIL_ALREADY_REGISTERED: 'Un client est déjà enregistré avec cette adresse e-mail.',
  NATIONAL_ID_ALREADY_REGISTERED: 'Un client est déjà enregistré avec ce numéro de CIN.',
  INVALID_NATIONAL_ID: 'Le numéro de CIN est invalide (1 ou 2 lettres puis 3 à 6 chiffres).',
  INVALID_PHONE_NUMBER: 'Le numéro de téléphone est invalide.',
  INVALID_COUNTRY_CODE: 'Le code pays est invalide (2 lettres, par exemple MA).',
  UNSUPPORTED_LANGUAGE: 'Cette langue n’est pas proposée (fr, en ou ar).',
  CUSTOMER_UNDERAGE: 'Le client doit avoir au moins 18 ans.',
  INVALID_STATUS_TRANSITION: "Ce changement de statut n'est pas possible dans l'état actuel.",
  SELF_APPROVAL_FORBIDDEN: 'Contrôle 4 yeux : vous ne pouvez pas valider ce que vous avez vous-même saisi. Un collègue doit le faire.',
  SELF_RELEASE_FORBIDDEN: 'Contrôle 4 yeux : une sanction doit être levée par une autre personne que celle qui l’a décidée.',
  CUSTOMER_CLOSED: 'Ce client est clôturé.',
  // Comptes et grand livre
  ACCOUNT_NOT_FOUND: 'Compte introuvable.',
  CUSTOMER_NOT_ELIGIBLE: "Le client n'est pas actif : impossible d'ouvrir ou de débloquer le compte.",
  UNSUPPORTED_CURRENCY: "Cette devise n'est pas proposée pour ce produit.",
  INSUFFICIENT_FUNDS: 'Provision insuffisante (découvert autorisé compris).',
  DAILY_LIMIT_EXCEEDED: 'Le plafond journalier de débit de ce compte est atteint.',
  ACCOUNT_FROZEN: 'Ce compte est gelé : les débits sont bloqués.',
  ACCOUNT_CLOSED: 'Ce compte est clôturé.',
  BALANCE_NOT_ZERO: 'Le solde doit être nul pour clôturer le compte.',
  SOURCE_ACCOUNT_NOT_OWNED: "Ce compte ne vous appartient pas ou n'existe pas.",
  SAME_ACCOUNT_TRANSFER: 'Le compte débité et le compte crédité doivent être différents.',
  INSUFFICIENT_CASH: "La caisse de l'agence ne contient pas assez d'espèces.",
  FOUR_EYES_VIOLATION: "Contrôle 4 yeux : l'ordre a été approuvé par la personne qui l'a saisi.",
  OPERATION_NOT_ALLOWED_FOR_INITIATOR: "Votre profil ne permet pas ce type d'opération.",
  INTERNAL_ACCOUNT: 'Ce compte interne de la banque ne peut pas être utilisé ainsi.',
  INVALID_PERIOD: 'La période commence après sa fin.',
  // Ordres de paiement
  ORDER_NOT_FOUND: 'Opération introuvable.',
  IDEMPOTENCY_KEY_REUSED: "Cette demande a déjà été envoyée avec un contenu différent. Rechargez le formulaire.",
  INVALID_ACCOUNT_NUMBER: "Le numéro de compte n'est pas un IBAN valide.",
  AMOUNT_ABOVE_LIMIT: 'Le montant dépasse le plafond autorisé pour une opération.',
  INVALID_AMOUNT: 'Le montant est invalide.',
  ORDER_NOT_AWAITING_APPROVAL: "Cette opération n'est plus en attente d'approbation.",
  REFUSED_BY_REVIEWER: 'Refusée par le second contrôleur.',
  APPROVAL_EXPIRED: "Non approuvée avant la fin de la journée : l'opération a été annulée.",
};

export function messageFor(code: string | null | undefined): string | undefined {
  return code ? MESSAGES[code] : undefined;
}

export interface DescribedError {
  message: string;
  code?: string;
  traceId?: string;
  fieldErrors: Record<string, string>;
  retryable: boolean;
}

/** Transforme n'importe quelle erreur en texte affichable, avec les erreurs par champ. */
export function describeError(error: unknown): DescribedError {
  if (error instanceof ApiError) {
    const fieldErrors: Record<string, string> = {};
    for (const violation of error.problem.errors ?? []) {
      fieldErrors[violation.field] = violation.message;
    }
    return {
      message: messageFor(error.code) ?? error.problem.detail ?? error.problem.title ?? `Erreur ${error.status}`,
      code: error.code,
      traceId: error.problem.traceId,
      fieldErrors,
      retryable: error.isRetryable,
    };
  }
  return {
    message: error instanceof Error ? error.message : 'Erreur inconnue',
    fieldErrors: {},
    retryable: false,
  };
}
