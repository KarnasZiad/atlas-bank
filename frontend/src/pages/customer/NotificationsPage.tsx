import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { notificationsApi } from '../../api/endpoints';
import { Empty, ErrorAlert, Loading } from '../../components/Feedback';
import { Badge, Card, PageHeader, Pagination } from '../../components/ui';
import { formatDateTime } from '../../lib/format';
import { notificationTypeLabel } from '../../lib/labels';

/** Les e-mails envoyés au client par notification-service, dans sa langue, IBAN masqués. */
export function NotificationsPage() {
  const [page, setPage] = useState(0);
  const notifications = useQuery({
    queryKey: ['notifications', { page }],
    queryFn: () => notificationsApi.list({ page, sort: 'sentAt,desc' }),
  });

  return (
    <>
      <PageHeader title="Notifications" subtitle="Les messages envoyés à votre adresse e-mail." />
      <Card>
        {notifications.isPending && <Loading />}
        <ErrorAlert error={notifications.error} />
        {notifications.data?.content.length === 0 && <Empty>Aucune notification.</Empty>}
        <div className="stack">
          {notifications.data?.content.map((notification) => (
            <details key={notification.id} className="message">
              <summary>
                <span>
                  <strong>{notification.subject}</strong>
                  <span className="sub">{formatDateTime(notification.sentAt)} · {notification.destination}</span>
                </span>
                <Badge tone={notification.type === 'CONTACT_DETAILS_CHANGED' || notification.type === 'TRANSACTION_REJECTED' ? 'warning' : 'neutral'}>
                  {notificationTypeLabel[notification.type]}
                </Badge>
              </summary>
              <pre className="message-body">{notification.body}</pre>
            </details>
          ))}
        </div>
        {notifications.data && <Pagination page={notifications.data.page} onChange={setPage} />}
      </Card>
    </>
  );
}
