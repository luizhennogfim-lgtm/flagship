import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { envApi, flagsApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { Badge, ErrorNotice, Field, formatDate, Modal, PageHeader, Pagination, Spinner, useDebounce } from '../components/ui';

function CreateFlagModal({ onClose }: { onClose: () => void }) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [key, setKey] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const create = useMutation({
    mutationFn: () => flagsApi.create({ key, name, description: description || undefined }),
    onSuccess: (flag) => {
      queryClient.invalidateQueries({ queryKey: ['flags'] });
      navigate(`/flags/${flag.id}`);
    },
  });
  const submit = (e: FormEvent) => { e.preventDefault(); create.mutate(); };
  return (
    <Modal title="New feature flag" onClose={onClose}>
      <form onSubmit={submit}>
        <Field label="Key" hint="Lower-case letters, digits and - _ . — applications reference the flag by this key and it cannot change later.">
          <input className="input" value={key} onChange={(e) => setKey(e.target.value)} placeholder="new-checkout" required />
        </Field>
        <Field label="Name"><input className="input" value={name} onChange={(e) => setName(e.target.value)} placeholder="New checkout" required /></Field>
        <Field label="Description"><textarea className="input" rows={3} value={description} onChange={(e) => setDescription(e.target.value)} /></Field>
        <ErrorNotice error={create.error} />
        <div className="actions"><button type="button" className="btn" onClick={onClose}>Cancel</button><button className="btn btn-primary" disabled={create.isPending}>Create flag</button></div>
      </form>
    </Modal>
  );
}

export default function Flags() {
  const { canEdit } = useAuth();
  const [search, setSearch] = useState('');
  const debounced = useDebounce(search, 300);
  const [environment, setEnvironment] = useState('');
  const [enabled, setEnabled] = useState('');
  const [archived, setArchived] = useState(false);
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);

  useEffect(() => setPage(0), [debounced, environment, enabled, archived]);

  const envs = useQuery({ queryKey: ['environments'], queryFn: envApi.list });
  const flags = useQuery({
    queryKey: ['flags', { debounced, environment, enabled, archived, page }],
    queryFn: () => flagsApi.list({
      search: debounced, environment, archived, page, size: 15,
      enabled: environment && enabled ? enabled === 'true' : undefined,
    }),
    placeholderData: keepPreviousData,
  });

  return (
    <>
      <PageHeader
        title="Feature flags"
        subtitle="Create, filter and roll out features without a deploy."
        actions={canEdit && <button className="btn btn-primary" onClick={() => setCreating(true)}>+ New flag</button>}
      />
      <div className="card filters">
        <input className="input" placeholder="Search by key or name…" value={search} onChange={(e) => setSearch(e.target.value)} />
        <select className="input" value={environment} onChange={(e) => { setEnvironment(e.target.value); if (!e.target.value) setEnabled(''); }}>
          <option value="">All environments</option>
          {envs.data?.map((e) => <option key={e.id} value={e.key}>{e.name}</option>)}
        </select>
        <select className="input" value={enabled} disabled={!environment} onChange={(e) => setEnabled(e.target.value)} title={environment ? '' : 'Pick an environment first'}>
          <option value="">ON or OFF</option>
          <option value="true">ON</option>
          <option value="false">OFF</option>
        </select>
        <label className="check"><input type="checkbox" checked={archived} onChange={(e) => setArchived(e.target.checked)} /> Archived</label>
      </div>

      {flags.isLoading ? <Spinner /> : flags.error ? <ErrorNotice error={flags.error} /> : (
        <div className="card">
          {flags.data && flags.data.content.length === 0 ? (
            <p className="muted">No flags match your filters.</p>
          ) : (
            <table className="table">
              <thead><tr><th>Flag</th><th>Environments</th><th>Updated</th></tr></thead>
              <tbody>
                {flags.data?.content.map((f) => (
                  <tr key={f.id}>
                    <td>
                      <Link to={`/flags/${f.id}`}><strong>{f.name}</strong></Link> {f.archived && <Badge tone="amber">archived</Badge>}
                      <br /><code>{f.key}</code>
                    </td>
                    <td>{f.environments.map((s) => (
                      <Badge key={s.environmentKey} tone={s.enabled ? 'green' : 'gray'}>{s.environmentKey}: {s.enabled ? `ON ${s.rolloutPercentage}%` : 'OFF'}</Badge>
                    ))}</td>
                    <td>{formatDate(f.updatedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <Pagination page={page} totalPages={flags.data?.totalPages ?? 0} onChange={setPage} />
        </div>
      )}
      {creating && <CreateFlagModal onClose={() => setCreating(false)} />}
    </>
  );
}
