import { useEffect, useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { auditApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import { Badge, ErrorNotice, formatDate, PageHeader, Pagination, Spinner, useDebounce } from '../components/ui';

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export default function Audit() {
  const { canAdmin } = useAuth();
  const [entityId, setEntityId] = useState('');
  const debounced = useDebounce(entityId.trim(), 300);
  const [page, setPage] = useState(0);
  useEffect(() => setPage(0), [debounced]);
  const validFilter = debounced === '' || UUID.test(debounced);

  const q = useQuery({
    queryKey: ['audit', { debounced, page }],
    queryFn: () => auditApi.list({ entityId: debounced || undefined, page, size: 20 }),
    enabled: canAdmin && validFilter,
    placeholderData: keepPreviousData,
  });

  if (!canAdmin) return <div className="notice notice-error">The audit log requires the ADMIN or OWNER role.</div>;

  return (
    <>
      <PageHeader title="Audit log" subtitle="Who changed what, and when. Newest first." />
      <div className="card filters">
        <input className="input" placeholder="Filter by entity id (e.g. a flag id)…" value={entityId} onChange={(e) => setEntityId(e.target.value)} />
      </div>
      {!validFilter && <div className="notice notice-error">That is not a valid id (UUID).</div>}
      {q.isLoading ? <Spinner /> : q.error ? <ErrorNotice error={q.error} /> : (
        <div className="card">
          {q.data?.content.length === 0 ? <p className="muted">No audit entries.</p> : (
            <table className="table">
              <thead><tr><th>When</th><th>Who</th><th>Action</th><th>Entity</th><th>Change</th></tr></thead>
              <tbody>
                {q.data?.content.map((l) => (
                  <tr key={l.id}>
                    <td>{formatDate(l.createdAt)}</td>
                    <td>{l.actorEmail ?? '—'}</td>
                    <td><Badge tone="blue">{l.action}</Badge></td>
                    <td>{l.entityType}<br /><code className="small">{l.entityId}</code></td>
                    <td>
                      <details>
                        <summary>View</summary>
                        <div className="diff">
                          <div><strong>Before</strong><pre>{JSON.stringify(l.oldValue, null, 2) ?? 'null'}</pre></div>
                          <div><strong>After</strong><pre>{JSON.stringify(l.newValue, null, 2) ?? 'null'}</pre></div>
                        </div>
                      </details>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
          <Pagination page={page} totalPages={q.data?.totalPages ?? 0} onChange={setPage} />
        </div>
      )}
    </>
  );
}
