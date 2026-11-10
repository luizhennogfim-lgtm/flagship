import { useState, type FormEvent } from 'react';
import { useAuth } from '../auth/AuthContext';
import { ErrorNotice, Field } from '../components/ui';

export default function Login() {
  const { login, register } = useAuth();
  const [mode, setMode] = useState<'login' | 'register'>('login');
  const [fullName, setFullName] = useState('');
  const [organizationName, setOrganizationName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [organizationSlug, setOrganizationSlug] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      if (mode === 'login') await login(email, password, organizationSlug.trim());
      else await register(fullName, email, password, organizationName);
    } catch (err) {
      setError(err);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-page">
      <form className="card login-card" onSubmit={submit}>
        <h1>🚩 Flagship</h1>
        <p className="muted">{mode === 'login' ? 'Sign in to manage your feature flags.' : 'Create your account and organization.'}</p>
        {mode === 'register' && (
          <>
            <Field label="Full name"><input className="input" value={fullName} onChange={(e) => setFullName(e.target.value)} required maxLength={120} /></Field>
            <Field label="Organization name"><input className="input" value={organizationName} onChange={(e) => setOrganizationName(e.target.value)} required maxLength={120} /></Field>
          </>
        )}
        <Field label="Email"><input className="input" type="email" value={email} onChange={(e) => setEmail(e.target.value)} required autoComplete="email" /></Field>
        <Field label="Password" hint={mode === 'register' ? 'At least 8 characters.' : undefined}>
          <input className="input" type="password" value={password} onChange={(e) => setPassword(e.target.value)} required minLength={mode === 'register' ? 8 : 1} autoComplete={mode === 'login' ? 'current-password' : 'new-password'} />
        </Field>
        {mode === 'login' && (
          <Field label="Organization slug (optional)" hint="Only needed if your email belongs to more than one organization.">
            <input className="input" value={organizationSlug} onChange={(e) => setOrganizationSlug(e.target.value)} />
          </Field>
        )}
        <ErrorNotice error={error} />
        <button className="btn btn-primary btn-block" disabled={busy}>{busy ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}</button>
        <button type="button" className="btn btn-ghost btn-block" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(null); }}>
          {mode === 'login' ? 'New here? Create an account' : 'Already have an account? Sign in'}
        </button>
      </form>
    </div>
  );
}
