import { describe, expect, it } from 'vitest';
import { ApiError, describeError } from './problem';

describe('erreurs RFC 9457', () => {
  it('traduit un code métier et garde le traceId pour le support', () => {
    const error = new ApiError(422, { status: 422, code: 'INSUFFICIENT_FUNDS', traceId: '4bf92f35', detail: 'Insufficient funds on account MA64…' });
    const described = describeError(error);
    expect(described.message).toBe('Provision insuffisante (découvert autorisé compris).');
    expect(described.traceId).toBe('4bf92f35');
    expect(described.retryable).toBe(false);
  });

  it('range les erreurs de validation par champ', () => {
    const error = new ApiError(400, {
      code: 'VALIDATION_FAILED',
      errors: [{ field: 'email', message: 'must be a well-formed email address' }, { field: 'address.city', message: 'must not be blank' }],
    });
    expect(describeError(error).fieldErrors).toEqual({
      email: 'must be a well-formed email address',
      'address.city': 'must not be blank',
    });
  });

  it('considère réseau coupé, 429 et 503 comme réessayables (avec la même clé d’idempotence)', () => {
    expect(new ApiError(0, { code: 'NETWORK_ERROR' }).isRetryable).toBe(true);
    expect(new ApiError(429, { code: 'TOO_MANY_REQUESTS' }).isRetryable).toBe(true);
    expect(new ApiError(503, { code: 'SERVICE_UNAVAILABLE' }).isRetryable).toBe(true);
    expect(new ApiError(403, { code: 'SELF_APPROVAL_FORBIDDEN' }).isRetryable).toBe(false);
  });

  it('retombe sur le détail du serveur pour un code inconnu', () => {
    expect(describeError(new ApiError(422, { code: 'SOMETHING_NEW', detail: 'Nouvelle règle' })).message).toBe('Nouvelle règle');
  });
});
