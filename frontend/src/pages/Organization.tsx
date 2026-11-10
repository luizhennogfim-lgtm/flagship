import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiKeysApi, membersApi, teamsApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import type { CreatedApiKey, Role } from '../api/types';
import { Badge, ErrorNotice, Field, formatDate, Modal, PageHeader, Pagination, Spinner } from '../components/ui';

const ROLES: Role[] = ['OWNER', 'ADMIN', 'EDITOR', 'VIEWER'];

function Members() {
  const { canAdmin, session } = useAuth();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ fullName: '', email: '', password: '', role: 'VIEWER' as Role });
  const q = useQuery({ queryKey: ['members', page], queryFn: () => membersApi.list(page) });
  const add = useMutation({
    mutationFn: () => membersApi.add(form),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['members'] });
      setOpen(false);
      setForm({ fullName: '', email: '', password: '', role: 'VIEWER' });
    },
  });
  const submit = (e: FormEvent) => { e.preventDefault(); add.mutate(); };
  return (
    <div className="card">
      <div className="card-head"><h3>Members</h3>{canAdmin && <button className="btn btn-primary" onClick={() => setOpen(true)}>+ Add member</button>}</div>
      {q.isLoading ? <Spinner /> : q.error ? <ErrorNotice error={q.error} /> : (
        <>
          <table className="table">
            <thead><tr><th>Name</th><th>Email</th><th>Role</th></tr></thead>
            <tbody>{q.data?.content.map((m) => <tr key={m.userId}><td>{m.fullName}</td><td>{m.email}</td><td><Badge tone={m.role === 'OWNER' ? 'amber' : 'blue'}>{m.role}</Badge></td></tr>)}</tbody>
          </table>
          <Pagination page={page} totalPages={q.data?.totalPages ?? 0} onChange={setPage} />
        </>
      )}
      {open && (
        <Modal title="Add member" onClose={() => setOpen(false)}>
          <form onSubmit={submit}>
            <Field label="Full name"><input className="input" value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} required /></Field>
            <Field label="Email"><input className="input" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} required /></Field>
            <Field label="Temporary password" hint="At least 8 characters."><input className="input" type="password" minLength={8} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} required /></Field>
            <Field label="Role" hint="Only an OWNER can grant OWNER.">
              <select className="input" value={form.role} onChange={(e) => setForm({ ...form, role: e.target.value as Role })}>
                {ROLES.map((r) => <option key={r} value={r} disabled={r === 'OWNER' && session?.role !== 'OWNER'}>{r}</option>)}
              </select>
            </Field>
            <ErrorNotice error={add.error} />
            <div className="actions"><button type="button" className="btn" onClick={() => setOpen(false)}>Cancel</button><button className="btn btn-primary" disabled={add.isPending}>Add member</button></div>
          </form>
        </Modal>
      )}
    </div>
  );
}

function Teams() {
  const { canAdmin } = useAuth();
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [name, setName] = useState('');
  const q = useQuery({ queryKey: ['teams', page], queryFn: () => teamsApi.list(page) });
  const create = useMutation({
    mutationFn: () => teamsApi.create({ name }),
    onSuccess: () => { setName(''); queryClient.invalidateQueries({ queryKey: ['teams'] }); },
  });
  const submit = (e: FormEvent) => { e.preventDefault(); create.mutate(); };
  return (
    <div className="card">
      <h3>Teams</h3>
      {canAdmin && (
        <form className="inline-form" onSubmit={submit}>
          <input className="input" placeholder="New team name" value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} />
          <button className="btn btn-primary" disabled={create.isPending}>Create team</button>
        </form>
      )}
      <ErrorNotice error={create.error} />
      {q.isLoading ? <Spinner /> : q.error ? <ErrorNotice error={q.error} /> : (
        <>
          <table className="table">
            <thead><tr><th>Name</th><th>Created</th></tr></thead>
            <tbody>{q.data?.content.map((t) => <tr key={t.id}><td>{t.name}</td><td>{formatDate(t.createdAt)}</td></tr>)}</tbody>
          </table>
          {q.data?.content.length === 0 && <p className="muted">No teams yet.</p>}
          <Pagination page={page} totalPages={q.data?.totalPages ?? 0} onChange={setPage} />
        </>
      )}
    </div>
  );
}

function ApiKeys() {
  const queryClient = useQueryClient();
  const [name, setName] = useState('');
  const [created, setCreated] = useState<CreatedApiKey | null>(null);
  const [copied, setCopied] = useState(false);
  const q = useQuery({ queryKey: ['api-keys'], queryFn: apiKeysApi.list });
  const create = useMutation({
    mutationFn: () => apiKeysApi.create({ name }),
    onSuccess: (key) => { setCreated(key); setName(''); queryClient.invalidateQueries({ queryKey: ['api-keys'] }); },
  });
  const revoke = useMutation({
    mutationFn: (id: string) => apiKeysApi.revoke(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['api-keys'] }),
  });
  const submit = (e: FormEvent) => { e.preventDefault(); create.mutate(); };

  async function copy(text: string) {
    try { await navigator.clipboard.writeText(text); setCopied(true); } catch { setCopied(false); }
  }

  return (
    <div className="card">
      <h3>API keys</h3>
      <p className="muted">Applications send a key in the <code>X-API-Key</code> header to call <code>/api/v1/evaluate</code>.</p>
      <form className="inline-form" onSubmit={submit}>
        <input className="input" placeholder="Key name (e.g. checkout service)" value={name} onChange={(e) => setName(e.target.value)} required maxLength={120} />
        <button className="btn btn-primary" disabled={create.isPending}>Create key</button>
      </form>
      <ErrorNotice error={create.error ?? revoke.error} />
      {q.isLoading ? <Spinner /> : q.error ? <ErrorNotice error={q.error} /> : (
        <table className="table">
          <thead><tr><th>Name</th><th>Prefix</th><th>Created</th><th>Status</th><th /></tr></thead>
          <tbody>
            {q.data?.map((k) => (
              <tr key={k.id}>
                <td>{k.name}</td><td><code>{k.prefix}…</code></td><td>{formatDate(k.createdAt)}</td>
                <td>{k.revokedAt ? <Badge tone="red">revoked</Badge> : <Badge tone="green">active</Badge>}</td>
                <td>{!k.revokedAt && <button className="btn btn-danger" disabled={revoke.isPending} onClick={() => { if (window.confirm(`Revoke "${k.name}"? Applications using it will stop working.`)) revoke.mutate(k.id); }}>Revoke</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {created && (
        <Modal title="Your new API key" onClose={() => { setCreated(null); setCopied(false); }}>
          <p>Copy it now. For security it is <strong>shown only once</strong>.</p>
          <pre className="secret">{created.key}</pre>
          <div className="actions">
            <button className="btn btn-primary" onClick={() => copy(created.key)}>{copied ? 'Copied ✓' : 'Copy key'}</button>
            <button className="btn" onClick={() => { setCreated(null); setCopied(false); }}>Done</button>
          </div>
        </Modal>
      )}
    </div>
  );
}

export default function Organization() {
  const { canAdmin, session } = useAuth();
  const [tab, setTab] = useState<'members' | 'teams' | 'keys'>('members');
  return (
    <>
      <PageHeader title="Organization" subtitle={`Members, teams and API keys of ${session?.organizationSlug}. Data is isolated per organization.`} />
      <div className="tabs">
        <button className={tab === 'members' ? 'tab active' : 'tab'} onClick={() => setTab('members')}>Members</button>
        <button className={tab === 'teams' ? 'tab active' : 'tab'} onClick={() => setTab('teams')}>Teams</button>
        {canAdmin && <button className={tab === 'keys' ? 'tab active' : 'tab'} onClick={() => setTab('keys')}>API keys</button>}
      </div>
      {tab === 'members' && <Members />}
      {tab === 'teams' && <Teams />}
      {tab === 'keys' && canAdmin && <ApiKeys />}
    </>
  );
}
