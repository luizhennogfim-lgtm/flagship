import { useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { envApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { ErrorNotice, Field, formatDate, Modal, PageHeader, Spinner } from '../components/ui';

export default function Environments() {
  const { canAdmin } = useAuth();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [key, setKey] = useState('');
  const [name, setName] = useState('');
  const q = useQuery({ queryKey: ['environments'], queryFn: envApi.list });
  const create = useMutation({
    mutationFn: () => envApi.create({ key, name }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['environments'] });
      queryClient.invalidateQueries({ queryKey: ['flags'] });
      setOpen(false); setKey(''); setName('');
    },
  });
  const submit = (e: FormEvent) => { e.preventDefault(); create.mutate(); };

  return (
    <>
      <PageHeader title="Environments" subtitle="Each flag has its own state and rules per environment."
        actions={canAdmin && <button className="btn btn-primary" onClick={() => setOpen(true)}>+ New environment</button>} />
      {q.isLoading ? <Spinner /> : q.error ? <ErrorNotice error={q.error} /> : (
        <div className="card">
          <table className="table">
            <thead><tr><th>Name</th><th>Key</th><th>Created</th></tr></thead>
            <tbody>{q.data?.map((e) => <tr key={e.id}><td><strong>{e.name}</strong></td><td><code>{e.key}</code></td><td>{formatDate(e.createdAt)}</td></tr>)}</tbody>
          </table>
        </div>
      )}
      {open && (
        <Modal title="New environment" onClose={() => setOpen(false)}>
          <form onSubmit={submit}>
            <Field label="Key" hint="Lower-case letters, digits and single hyphens (e.g. qa)."><input className="input" value={key} onChange={(e) => setKey(e.target.value)} required /></Field>
            <Field label="Name"><input className="input" value={name} onChange={(e) => setName(e.target.value)} required /></Field>
            <ErrorNotice error={create.error} />
            <div className="actions"><button type="button" className="btn" onClick={() => setOpen(false)}>Cancel</button><button className="btn btn-primary" disabled={create.isPending}>Create</button></div>
          </form>
        </Modal>
      )}
    </>
  );
}
