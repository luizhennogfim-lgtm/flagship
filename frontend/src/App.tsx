import { Navigate, Route, Routes } from 'react-router-dom';
import { useAuth } from './auth/AuthContext';
import Layout from './components/Layout';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Flags from './pages/Flags';
import FlagDetail from './pages/FlagDetail';
import Environments from './pages/Environments';
import Audit from './pages/Audit';
import Organization from './pages/Organization';

function Protected({ children }: { children: JSX.Element }) {
  const { session } = useAuth();
  return session ? children : <Navigate to="/login" replace />;
}

export default function App() {
  const { session } = useAuth();
  return (
    <Routes>
      <Route path="/login" element={session ? <Navigate to="/" replace /> : <Login />} />
      <Route element={<Protected><Layout /></Protected>}>
        <Route index element={<Dashboard />} />
        <Route path="flags" element={<Flags />} />
        <Route path="flags/:id" element={<FlagDetail />} />
        <Route path="environments" element={<Environments />} />
        <Route path="audit" element={<Audit />} />
        <Route path="organization" element={<Organization />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
