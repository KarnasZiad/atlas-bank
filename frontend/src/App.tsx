import { Route, Routes } from 'react-router';
import { RequireRole } from './auth/RequireRole';
import { Layout } from './components/Layout';
import { LandingPage, NoAccessPage, NotFoundPage } from './pages/LandingPage';
import { DashboardPage } from './pages/customer/DashboardPage';
import { NotificationsPage } from './pages/customer/NotificationsPage';
import { ProfilePage } from './pages/customer/ProfilePage';
import { AccountPage } from './pages/shared/AccountPage';
import { OrderPage } from './pages/shared/OrderPage';
import { OrdersPage } from './pages/shared/OrdersPage';
import { TransferPage } from './pages/shared/TransferPage';
import { CashPage } from './pages/staff/CashPage';
import { CustomerPage } from './pages/staff/CustomerPage';
import { CustomersPage } from './pages/staff/CustomersPage';
import { EnrollPage } from './pages/staff/EnrollPage';
import { ReconciliationPage } from './pages/staff/ReconciliationPage';
import { StaffHomePage } from './pages/staff/StaffHomePage';

/**
 * La carte des pages. Deux espaces, chacun gardé par un rôle :
 *  - /client : la banque en ligne du client (CUSTOMER) ;
 *  - /agence : le back-office du personnel (EMPLOYEE, et ADMIN qui l'inclut).
 * Certaines pages (compte, virement, opérations) servent aux deux : elles s'adaptent à la session.
 */
export function App() {
  return (
    <Routes>
      <Route index element={<LandingPage />} />
      <Route path="sans-acces" element={<NoAccessPage />} />

      <Route path="client" element={<RequireRole roles={['CUSTOMER']} />}>
        <Route element={<Layout />}>
          <Route index element={<DashboardPage />} />
          <Route path="comptes/:accountId" element={<AccountPage />} />
          <Route path="virement" element={<TransferPage />} />
          <Route path="operations" element={<OrdersPage />} />
          <Route path="operations/:orderId" element={<OrderPage />} />
          <Route path="notifications" element={<NotificationsPage />} />
          <Route path="profil" element={<ProfilePage />} />
        </Route>
      </Route>

      <Route path="agence" element={<RequireRole roles={['EMPLOYEE']} />}>
        <Route element={<Layout />}>
          <Route index element={<StaffHomePage />} />
          <Route path="clients" element={<CustomersPage />} />
          <Route path="clients/nouveau" element={<EnrollPage />} />
          <Route path="clients/:customerId" element={<CustomerPage />} />
          <Route path="comptes/:accountId" element={<AccountPage />} />
          <Route path="guichet" element={<CashPage />} />
          <Route path="virement" element={<TransferPage />} />
          <Route path="operations" element={<OrdersPage />} />
          <Route path="operations/:orderId" element={<OrderPage />} />
          <Route path="controle" element={<RequireRole roles={['ADMIN']} />}>
            <Route index element={<ReconciliationPage />} />
          </Route>
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
