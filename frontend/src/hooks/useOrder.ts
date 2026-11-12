import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import { transactionsApi } from '../api/endpoints';
import type { PaymentOrder } from '../api/types';

/**
 * Suivi d'un ordre de paiement. La prise d'ordre répond 202 Accepted AVANT l'exécution : le grand
 * livre (account-service) traite l'ordre quelques millisecondes plus tard, via Kafka.
 * L'interface interroge donc l'ordre régulièrement (polling) jusqu'à un statut final.
 */
export function useOrder(id: string | undefined, initial?: PaymentOrder) {
  const queryClient = useQueryClient();
  const query = useQuery({
    queryKey: ['order', id],
    queryFn: () => transactionsApi.get(id as string),
    enabled: Boolean(id),
    initialData: initial,
    refetchInterval: (current) => {
      const status = current.state.data?.status;
      if (status === 'PENDING') {
        return 1_000; // en route vers le grand livre : réponse attendue très vite
      }
      if (status === 'AWAITING_APPROVAL') {
        return 10_000; // attend un second employé : on regarde de temps en temps
      }
      return false; // COMPLETED ou REJECTED : statut final, on arrête
    },
  });

  const status = query.data?.status;
  useEffect(() => {
    if (status === 'COMPLETED' || status === 'REJECTED') {
      // L'argent a bougé (ou pas) : les soldes et les relevés affichés doivent être rechargés.
      void queryClient.invalidateQueries({ queryKey: ['accounts'] });
      void queryClient.invalidateQueries({ queryKey: ['account'] });
      void queryClient.invalidateQueries({ queryKey: ['entries'] });
      void queryClient.invalidateQueries({ queryKey: ['orders'] });
    }
  }, [status, queryClient]);

  return query;
}
