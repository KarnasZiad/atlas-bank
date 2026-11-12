import { useCallback, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { OrderSubmission } from '../api/types';

/** Une clé unique par demande (16 à 64 caractères acceptés par le serveur ; un UUID en fait 36). */
export function newIdempotencyKey(): string {
  return crypto.randomUUID();
}

/**
 * Envoi d'un ordre de paiement avec une clé d'idempotence, comme dans une vraie application bancaire.
 *
 * Le scénario à éviter : le client clique « Valider », le réseau coupe avant la réponse, il reclique.
 * L'ordre a-t-il été pris en compte ? S'il réessaie, paiera-t-il deux fois ?
 *
 * La règle :
 *  - une clé est créée pour la demande et GARDÉE tant que la demande n'a pas abouti ;
 *  - un nouvel essai (réseau coupé, 503, 429) réutilise LA MÊME clé : si le premier envoi avait bien
 *    été enregistré, le serveur renvoie l'ordre d'origine (Idempotent-Replayed: true), jamais un second ;
 *  - après un succès, une nouvelle clé est préparée pour la demande suivante.
 */
export function useOrderSubmission(send: (idempotencyKey: string) => Promise<OrderSubmission>) {
  const keyRef = useRef(newIdempotencyKey());
  const [submission, setSubmission] = useState<OrderSubmission | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [pending, setPending] = useState(false);
  const queryClient = useQueryClient();

  const submit = useCallback(async () => {
    setPending(true);
    setError(null);
    try {
      const result = await send(keyRef.current);
      setSubmission(result);
      keyRef.current = newIdempotencyKey();
      // Les listes d'opérations et les soldes ne sont plus à jour.
      void queryClient.invalidateQueries({ queryKey: ['orders'] });
      void queryClient.invalidateQueries({ queryKey: ['accounts'] });
    } catch (failure) {
      setError(failure);
    } finally {
      setPending(false);
    }
  }, [send, queryClient]);

  const reset = useCallback(() => {
    setSubmission(null);
    setError(null);
  }, []);

  return { submit, reset, submission, error, pending, currentKey: keyRef.current };
}
