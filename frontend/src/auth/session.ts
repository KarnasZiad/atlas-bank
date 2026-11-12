export type Role = 'CUSTOMER' | 'EMPLOYEE' | 'ADMIN';

/**
 * Ce que l'interface sait de l'utilisateur connecté, lu dans le jeton d'accès émis par Keycloak.
 *
 * Important : ces informations servent seulement à ADAPTER L'AFFICHAGE (quels menus, quels boutons).
 * La sécurité n'est jamais décidée ici : chaque service revérifie le jeton et les droits. Cacher un
 * bouton ne protège rien, un attaquant peut appeler l'API directement.
 */
export interface Session {
  /** Identifiant immuable (claim "sub") : c'est lui que le backend compare pour le contrôle 4 yeux. */
  subject: string;
  /** Login (claim "preferred_username"), affiché dans l'audit. */
  username: string;
  displayName: string;
  /** Claim "customer_id" : rattache un client à son dossier. Absent pour le personnel. */
  customerId: string | null;
  roles: Role[];
  isCustomer: boolean;
  isStaff: boolean;
  isAdmin: boolean;
  /** Fin de validité du jeton (5 minutes après son émission). */
  expiresAt: Date | null;
}

const KNOWN_ROLES: readonly Role[] = ['CUSTOMER', 'EMPLOYEE', 'ADMIN'];

/**
 * Lit la partie centrale d'un JWT (en-tête.CHARGE.signature), encodée en Base64URL.
 * Le navigateur ne vérifie pas la signature : il n'en a pas besoin, ce sont les services qui le font.
 */
export function decodeJwtPayload(token: string): Record<string, unknown> {
  const parts = token.split('.');
  if (parts.length !== 3) {
    throw new Error('Jeton JWT mal formé');
  }
  const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
  const padded = base64.padEnd(base64.length + ((4 - (base64.length % 4)) % 4), '=');
  const bytes = Uint8Array.from(atob(padded), (char) => char.charCodeAt(0));
  return JSON.parse(new TextDecoder().decode(bytes)) as Record<string, unknown>;
}

export function sessionFromAccessToken(accessToken: string): Session {
  const claims = decodeJwtPayload(accessToken);
  const rawRoles = Array.isArray(claims.roles) ? claims.roles : [];
  const roles = new Set(rawRoles.filter((role): role is Role => KNOWN_ROLES.includes(role as Role)));
  // Même hiérarchie que le backend : ADMIN inclut EMPLOYEE.
  if (roles.has('ADMIN')) {
    roles.add('EMPLOYEE');
  }
  const username = typeof claims.preferred_username === 'string' ? claims.preferred_username : '';
  const givenName = typeof claims.given_name === 'string' ? claims.given_name : '';
  const familyName = typeof claims.family_name === 'string' ? claims.family_name : '';
  const displayName = `${givenName} ${familyName}`.trim() || username;
  return {
    subject: typeof claims.sub === 'string' ? claims.sub : '',
    username,
    displayName,
    customerId: typeof claims.customer_id === 'string' && claims.customer_id ? claims.customer_id : null,
    roles: [...roles],
    isCustomer: roles.has('CUSTOMER'),
    isStaff: roles.has('EMPLOYEE'),
    isAdmin: roles.has('ADMIN'),
    expiresAt: typeof claims.exp === 'number' ? new Date(claims.exp * 1000) : null,
  };
}

/** La page d'accueil de chaque profil. */
export function homePathOf(session: Session): string {
  if (session.isStaff) {
    return '/agence';
  }
  if (session.isCustomer) {
    return '/client';
  }
  return '/sans-acces';
}
