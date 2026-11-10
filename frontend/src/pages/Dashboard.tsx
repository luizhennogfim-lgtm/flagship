import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { envApi, flagsApi } from '../api/endpoints';
import { Badge, ErrorNotice, formatDate, PageHeader, Spinner } from '../components/ui';

export default function Dashboard() {
  const flags = useQuery({ queryKey: ['flags', 'overview'], queryFn: () => flagsApi.list({ size: 100 }) });
  const envs = useQuery({ queryKey: ['environments'], queryFn: envApi.list });

  if (flags.isLoading || envs.isLoading) return <Spinner />;
  if (flags.error || envs.error) return <ErrorNotice error={flags.error ?? envs.error} />;

  const items = flags.data?.content ?? [];
  const total = flags.data?.totalElements ?? 0;
  const truncated = total > items.length;
  const recent = [...items].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 6);

  return (
    <>
      <PageHeader title="Overview" subtitle="State of your feature flags across environments." />
      <div className="grid grid-4">
        <div className="card stat"><span className="muted">Active flags</span><strong>{total}</strong></div>
        {(envs.data ?? []).map((env) => {
          const on = items.filter((f) => f.environments.find((s) => s.environmentKey === env.key)?.enabled).length;
          const off = items.length - on;
          const pct = items.length ? Math.round((on / items.length) * 100) : 0;
          return (
            <div className="card stat" key={env.id}>
              <span className="muted">{env.name}</span>
              <strong>{on} <small className="muted">ON</small> · {off} <small className="muted">OFF</small></strong>
              <div className="bar"><div className="bar-fill" style={{ width: `${pct}%` }} /></div>
            </div>
          );
        })}
      </div>
      {truncated && <p className="muted">Counts are based on the first {items.length} of {total} flags.</p>}

      <div className="card">
        <h3>Recently changed</h3>
        {recent.length === 0 ? (
          <p className="muted">No flags yet. <Link to="/flags">Create your first flag</Link>.</p>
        ) : (
          <table className="table">
            <thead><tr><th>Flag</th><th>Environments</th><th>Updated</th></tr></thead>
            <tbody>
              {recent.map((f) => (
                <tr key={f.id}>
                  <td><Link to={`/flags/${f.id}`}><strong>{f.name}</strong></Link><br /><code>{f.key}</code></td>
                  <td>{f.environments.map((s) => (
                    <Badge key={s.environmentKey} tone={s.enabled ? 'green' : 'gray'}>{s.environmentKey}: {s.enabled ? `${s.rolloutPercentage}%` : 'OFF'}</Badge>
                  ))}</td>
                  <td>{formatDate(f.updatedAt)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </>
  );
}
