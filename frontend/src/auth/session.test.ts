import { describe, expect, it } from 'vitest';
import { decodeJwtPayload, homePathOf, sessionFromAccessToken } from './session';

/** Fabrique un faux JWT (non signé) : le navigateur ne vérifie jamais la signature, seul le serveur le fait. */
function tokenWith(claims: Record<string, unknown>): string {
  const encode = (value: unknown) =>
    btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(value))))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
  return `${encode({ alg: 'RS256' })}.${encode(claims)}.signature`;
}

describe('session lue dans le jeton d’accès', () => {
  it('reconnaît un client et son dossier', () => {
    const session = sessionFromAccessToken(tokenWith({
      sub: 'sub-salma', preferred_username: 'salma@example.ma', roles: ['CUSTOMER'],
      customer_id: '0192a8b4-0000-7000-8000-000000000001', given_name: 'Salma', family_name: 'Benali', exp: 1_800_000_000,
    }));
    expect(session.isCustomer).toBe(true);
    expect(session.isStaff).toBe(false);
    expect(session.customerId).toBe('0192a8b4-0000-7000-8000-000000000001');
    expect(session.displayName).toBe('Salma Benali');
    expect(homePathOf(session)).toBe('/client');
  });

  it('applique la hiérarchie ADMIN ⊃ EMPLOYEE, comme le backend', () => {
    const session = sessionFromAccessToken(tokenWith({ sub: 's', preferred_username: 'admin.atlas', roles: ['ADMIN'] }));
    expect(session.isAdmin).toBe(true);
    expect(session.isStaff).toBe(true);
    expect(homePathOf(session)).toBe('/agence');
  });

  it('ignore les rôles inconnus et un utilisateur sans rôle n’a aucun espace', () => {
    const session = sessionFromAccessToken(tokenWith({ sub: 's', preferred_username: 'x', roles: ['offline_access'] }));
    expect(session.roles).toEqual([]);
    expect(homePathOf(session)).toBe('/sans-acces');
  });

  it('décode les caractères non ASCII (noms arabes)', () => {
    expect(decodeJwtPayload(tokenWith({ given_name: 'سلمى' })).given_name).toBe('سلمى');
  });

  it('refuse un jeton mal formé', () => {
    expect(() => decodeJwtPayload('pas-un-jwt')).toThrow();
  });
});
