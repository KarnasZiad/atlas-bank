const LOCALE = 'fr-MA';

/** 1250.5 MAD → « 1 250,50 MAD » (format marocain en français). Les décimales viennent de la devise. */
export function formatMoney(amount: number, currency: string): string {
  return new Intl.NumberFormat(LOCALE, { style: 'currency', currency, currencyDisplay: 'code' }).format(amount);
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  return new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium' }).format(new Date(iso));
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  return new Intl.DateTimeFormat(LOCALE, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso));
}

/** « MA64999001000000000000000001 » → « MA64 9990 0100 0000 0000 0000 0001 », plus lisible au guichet. */
export function formatIban(accountNumber: string | null | undefined): string {
  if (!accountNumber) {
    return '—';
  }
  return accountNumber.replace(/\s+/g, '').replace(/(.{4})/g, '$1 ').trim();
}

/** L'IBAN tel que le client le saisit (espaces, minuscules) → la forme attendue par l'API. */
export function normalizeIban(input: string): string {
  return input.replace(/\s+/g, '').toUpperCase();
}

/** Aujourd'hui au format AAAA-MM-JJ (champs de date HTML et paramètres from/to de l'API). */
export function isoDay(date: Date = new Date()): string {
  const local = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return local.toISOString().slice(0, 10);
}
