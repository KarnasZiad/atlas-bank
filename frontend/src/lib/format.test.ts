import { describe, expect, it } from 'vitest';
import { formatIban, formatMoney, normalizeIban } from './format';

describe('formatage', () => {
  it('groupe un IBAN par 4 et le remet en forme pour l’API', () => {
    expect(formatIban('MA64999001000000000000000001')).toBe('MA64 9990 0100 0000 0000 0000 0001');
    expect(normalizeIban('ma64 9990 0100 0000 0000 0000 0001')).toBe('MA64999001000000000000000001');
  });

  it('affiche un montant avec sa devise et deux décimales', () => {
    const text = formatMoney(1250.5, 'MAD');
    expect(text).toContain('MAD');
    expect(text.replace(/\s/g, '')).toContain('250,50');
  });
});
