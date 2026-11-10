import { useEffect, useState, type ReactNode } from 'react';
import { ApiError } from '../api/client';

export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return <div className="center muted">{label}</div>;
}

export function ErrorNotice({ error }: { error: unknown }) {
  if (!error) return null;
  const api = error instanceof ApiError ? error : null;
  const message = error instanceof Error ? error.message : String(error);
  return (
    <div className="notice notice-error" role="alert">
      <strong>{api ? api.title : 'Error'}:</strong> {message}
      {api && Object.keys(api.fieldErrors).length > 0 && (
        <ul>
          {Object.entries(api.fieldErrors).map(([field, msg]) => (
            <li key={field}><code>{field}</code>: {msg}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function Badge({ tone = 'gray', children }: { tone?: 'gray' | 'green' | 'red' | 'blue' | 'amber'; children: ReactNode }) {
  return <span className={`badge badge-${tone}`}>{children}</span>;
}

export function Modal({ title, onClose, children }: { title: string; onClose: () => void; children: ReactNode }) {
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [onClose]);
  return (
    <div className="overlay" onMouseDown={onClose}>
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-head">
          <h3>{title}</h3>
          <button className="btn btn-ghost" onClick={onClose} aria-label="Close">✕</button>
        </div>
        {children}
      </div>
    </div>
  );
}

export function Field({ label, hint, children }: { label: string; hint?: string; children: ReactNode }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint && <small className="muted">{hint}</small>}
    </label>
  );
}

export function Pagination({ page, totalPages, onChange }: { page: number; totalPages: number; onChange: (p: number) => void }) {
  if (totalPages <= 1) return null;
  return (
    <div className="pagination">
      <button className="btn" disabled={page === 0} onClick={() => onChange(page - 1)}>← Previous</button>
      <span className="muted">Page {page + 1} of {totalPages}</span>
      <button className="btn" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next →</button>
    </div>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="page-head">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      <div>{actions}</div>
    </div>
  );
}

export function useDebounce<T>(value: T, ms: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setDebounced(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return debounced;
}

export function formatDate(iso: string | null | undefined): string {
  return iso ? new Date(iso).toLocaleString() : '—';
}
