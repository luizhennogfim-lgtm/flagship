import { useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { auditApi, evaluateApi, flagsApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthContext';
import {
  RULE_OPERATORS, RULE_TYPES,
  type EnvironmentState, type Flag, type RuleInput, type RuleOperator, type RuleType,
} from '../api/types';
import { Badge, ErrorNotice, Field, formatDate, PageHeader, Spinner } from '../components/ui';

/* ---------- details ---------- */
function DetailsForm({ flag, canEdit }: { flag: Flag; canEdit: boolean }) {
  const queryClient = useQueryClient();
  const [name, setName] = useState(flag.name);
  const [description, setDescription] = useState(flag.description ?? '');
  const [saved, setSaved] = useState(false);
  useEffect(() => { setName(flag.name); setDescription(flag.description ?? ''); }, [flag.name, flag.description]);
  const save = useMutation({
    mutationFn: () => flagsApi.update(flag.id, { name, description: description || undefined }),
    onSuccess: () => { setSaved(true); queryClient.invalidateQueries({ queryKey: ['flag', flag.id] }); queryClient.invalidateQueries({ queryKey: ['flags'] }); },
  });
  const submit = (e: FormEvent) => { e.preventDefault(); setSaved(false); save.mutate(); };
  return (
    <form className="card" onSubmit={submit}>
      <h3>Details</h3>
      <Field label="Key"><input className="input" value={flag.key} disabled /></Field>
      <Field label="Name"><input className="input" value={name} onChange={(e) => setName(e.target.value)} disabled={!canEdit} required /></Field>
      <Field label="Description"><textarea className="input" rows={3} value={description} onChange={(e) => setDescription(e.target.value)} disabled={!canEdit} /></Field>
      <ErrorNotice error={save.error} />
      {saved && <div className="notice notice-ok">Saved.</div>}
      {canEdit && <button className="btn btn-primary" disabled={save.isPending}>Save details</button>}
    </form>
  );
}

/* ---------- rollout ---------- */
const PRESETS = [0, 10, 25, 50, 75, 100];

function RolloutGrid({ percent }: { percent: number }) {
  return (
    <div className="dots" aria-label={`${percent}% of users`}>
      {Array.from({ length: 100 }, (_, i) => <span key={i} className={i < percent ? 'dot on' : 'dot'} />)}
    </div>
  );
}

function StatePanel({ flag, state, canEdit }: { flag: Flag; state: EnvironmentState; canEdit: boolean }) {
  const queryClient = useQueryClient();
  const [enabled, setEnabled] = useState(state.enabled);
  const [percent, setPercent] = useState(state.rolloutPercentage);
  useEffect(() => { setEnabled(state.enabled); setPercent(state.rolloutPercentage); }, [state.environmentKey, state.version, state.enabled, state.rolloutPercentage]);
  const dirty = enabled !== state.enabled || percent !== state.rolloutPercentage;

  const refresh = () => {
    queryClient.invalidateQueries({ queryKey: ['flag', flag.id] });
    queryClient.invalidateQueries({ queryKey: ['rules', flag.id] });
    queryClient.invalidateQueries({ queryKey: ['flags'] });
    queryClient.invalidateQueries({ queryKey: ['audit'] });
  };
  const save = useMutation({
    mutationFn: () => flagsApi.updateEnvironment(flag.id, state.environmentKey, { enabled, rolloutPercentage: percent, version: state.version }),
    onSuccess: refresh,
    onError: refresh, // on a version conflict (409) reload the latest state
  });

  return (
    <div className="card">
      <h3>Release in {state.environmentName}</h3>
      <div className="row">
        <label className="switch">
          <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} disabled={!canEdit} />
          <span>{enabled ? 'Flag is ON' : 'Flag is OFF'}</span>
        </label>
        <Badge tone={enabled ? 'green' : 'gray'}>{enabled ? `${percent}% of users` : 'nobody'}</Badge>
      </div>
      <Field label={`Rollout: ${percent}%`} hint="Deterministic: a given user always lands in the same group, so raising the percentage only adds users.">
        <input type="range" min={0} max={100} step={1} value={percent} onChange={(e) => setPercent(Number(e.target.value))} disabled={!canEdit} />
      </Field>
      <div className="presets">
        {PRESETS.map((p) => (
          <button key={p} type="button" className={p === percent ? 'btn btn-primary' : 'btn'} onClick={() => setPercent(p)} disabled={!canEdit}>{p}%</button>
        ))}
      </div>
      <RolloutGrid percent={enabled ? percent : 0} />
      <p className="muted">Last change: {formatDate(state.updatedAt)} · version {state.version}</p>
      <ErrorNotice error={save.error} />
      {canEdit && <button className="btn btn-primary" onClick={() => save.mutate()} disabled={!dirty || save.isPending}>{save.isPending ? 'Saving…' : 'Save release'}</button>}
    </div>
  );
}

/* ---------- rules ---------- */
interface Draft {
  type: RuleType;
  attribute: string;
  operator: RuleOperator;
  valuesText: string;
  serve: boolean;
  description: string;
}

const emptyDraft = (): Draft => ({ type: 'USER_LIST', attribute: '', operator: 'EQUALS', valuesText: '', serve: true, description: '' });
const splitValues = (text: string) => text.split(/[\n,]/).map((v) => v.trim()).filter(Boolean);

function toInput(d: Draft): RuleInput {
  const isAttr = d.type === 'ATTRIBUTE';
  return {
    type: d.type,
    attribute: isAttr ? d.attribute.trim() : null,
    operator: isAttr ? d.operator : null,
    values: splitValues(d.valuesText),
    serve: d.serve,
    description: d.description.trim() || null,
  };
}

function RulesEditor({ flagId, envKey, canEdit }: { flagId: string; envKey: string; canEdit: boolean }) {
  const queryClient = useQueryClient();
  const q = useQuery({ queryKey: ['rules', flagId, envKey], queryFn: () => flagsApi.getRules(flagId, envKey) });
  const [drafts, setDrafts] = useState<Draft[]>([]);
  const [clientError, setClientError] = useState<string | null>(null);

  useEffect(() => {
    if (q.data) {
      setDrafts(q.data.rules.map((r) => ({
        type: r.type, attribute: r.attribute ?? '', operator: r.operator ?? 'EQUALS',
        valuesText: r.values.join(', '), serve: r.serve, description: r.description ?? '',
      })));
    }
  }, [q.data]);

  const save = useMutation({
    mutationFn: () => flagsApi.replaceRules(flagId, envKey, { version: q.data!.version, rules: drafts.map(toInput) }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['rules', flagId] });
      queryClient.invalidateQueries({ queryKey: ['flag', flagId] });
      queryClient.invalidateQueries({ queryKey: ['audit'] });
    },
    onError: () => queryClient.invalidateQueries({ queryKey: ['rules', flagId] }),
  });

  const patch = (i: number, p: Partial<Draft>) => setDrafts((list) => list.map((d, idx) => (idx === i ? { ...d, ...p } : d)));
  const move = (i: number, dir: -1 | 1) => setDrafts((list) => {
    const j = i + dir;
    if (j < 0 || j >= list.length) return list;
    const copy = [...list];
    [copy[i], copy[j]] = [copy[j], copy[i]];
    return copy;
  });

  function onSave() {
    const invalid = drafts.findIndex((d) => splitValues(d.valuesText).length === 0 || (d.type === 'ATTRIBUTE' && !d.attribute.trim()));
    if (invalid >= 0) { setClientError(`Rule ${invalid + 1}: fill in the values${drafts[invalid].type === 'ATTRIBUTE' ? ' and the attribute name' : ''}.`); return; }
    setClientError(null);
    save.mutate();
  }

  if (q.isLoading) return <Spinner />;
  if (q.error) return <ErrorNotice error={q.error} />;

  return (
    <div className="card">
      <h3>Targeting rules</h3>
      <p className="muted">Evaluated top to bottom; the first match wins and decides ON or OFF. If no rule matches, the rollout percentage applies.</p>
      {drafts.length === 0 && <p className="muted">No rules in this environment.</p>}
      {drafts.map((d, i) => (
        <div className="rule" key={i}>
          <div className="rule-head">
            <strong>#{i + 1}</strong>
            {canEdit && (
              <span>
                <button className="btn btn-ghost" onClick={() => move(i, -1)} disabled={i === 0} aria-label="Move up">↑</button>
                <button className="btn btn-ghost" onClick={() => move(i, 1)} disabled={i === drafts.length - 1} aria-label="Move down">↓</button>
                <button className="btn btn-ghost" onClick={() => setDrafts((l) => l.filter((_, idx) => idx !== i))} aria-label="Remove rule">Remove</button>
              </span>
            )}
          </div>
          <div className="rule-grid">
            <Field label="Type">
              <select className="input" value={d.type} disabled={!canEdit} onChange={(e) => patch(i, { type: e.target.value as RuleType })}>
                {RULE_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
              </select>
            </Field>
            {d.type === 'ATTRIBUTE' && (
              <>
                <Field label="Attribute"><input className="input" value={d.attribute} disabled={!canEdit} placeholder="country" onChange={(e) => patch(i, { attribute: e.target.value })} /></Field>
                <Field label="Operator">
                  <select className="input" value={d.operator} disabled={!canEdit} onChange={(e) => patch(i, { operator: e.target.value as RuleOperator })}>
                    {RULE_OPERATORS.map((o) => <option key={o} value={o}>{o}</option>)}
                  </select>
                </Field>
              </>
            )}
            <Field label={d.type === 'USER_LIST' ? 'User ids' : d.type === 'GROUP' ? 'Groups' : 'Values'} hint="Separate by comma or new line.">
              <textarea className="input" rows={2} value={d.valuesText} disabled={!canEdit} onChange={(e) => patch(i, { valuesText: e.target.value })} />
            </Field>
            <Field label="When it matches">
              <select className="input" value={String(d.serve)} disabled={!canEdit} onChange={(e) => patch(i, { serve: e.target.value === 'true' })}>
                <option value="true">Serve ON</option>
                <option value="false">Serve OFF</option>
              </select>
            </Field>
            <Field label="Description"><input className="input" value={d.description} disabled={!canEdit} onChange={(e) => patch(i, { description: e.target.value })} /></Field>
          </div>
        </div>
      ))}
      {clientError && <div className="notice notice-error">{clientError}</div>}
      <ErrorNotice error={save.error} />
      {canEdit && (
        <div className="actions">
          <button className="btn" onClick={() => setDrafts((l) => [...l, emptyDraft()])}>+ Add rule</button>
          <button className="btn btn-primary" onClick={onSave} disabled={save.isPending}>{save.isPending ? 'Saving…' : 'Save rules'}</button>
        </div>
      )}
    </div>
  );
}

/* ---------- playground ---------- */
function Playground({ flagKey, envKey }: { flagKey: string; envKey: string }) {
  const [userId, setUserId] = useState('user-123');
  const [groups, setGroups] = useState('');
  const [attrs, setAttrs] = useState('');
  const run = useMutation({
    mutationFn: () => {
      const attributes: Record<string, string> = {};
      attrs.split('\n').forEach((line) => {
        const idx = line.indexOf('=');
        if (idx > 0) attributes[line.slice(0, idx).trim()] = line.slice(idx + 1).trim();
      });
      return evaluateApi.evaluate({
        flagKey, userId, environment: envKey,
        groups: splitValues(groups), attributes,
      });
    },
  });
  return (
    <div className="card">
      <h3>Try an evaluation</h3>
      <p className="muted">Calls <code>POST /api/v1/evaluate</code> for <code>{flagKey}</code> in <code>{envKey}</code>, exactly as your application would.</p>
      <div className="rule-grid">
        <Field label="User id"><input className="input" value={userId} onChange={(e) => setUserId(e.target.value)} /></Field>
        <Field label="Groups" hint="comma separated"><input className="input" value={groups} onChange={(e) => setGroups(e.target.value)} placeholder="beta" /></Field>
        <Field label="Attributes" hint="one per line: key=value"><textarea className="input" rows={2} value={attrs} onChange={(e) => setAttrs(e.target.value)} placeholder="country=BR" /></Field>
      </div>
      <button className="btn btn-primary" onClick={() => run.mutate()} disabled={run.isPending || !userId.trim()}>Evaluate</button>
      <ErrorNotice error={run.error} />
      {run.data && (
        <div className="result">
          <Badge tone={run.data.enabled ? 'green' : 'red'}>{run.data.enabled ? 'ENABLED' : 'DISABLED'}</Badge>
          <span>reason: <code>{run.data.reason}</code></span>
          <span>variant: <code>{run.data.variant}</code></span>
        </div>
      )}
    </div>
  );
}

/* ---------- history ---------- */
function History({ flagId }: { flagId: string }) {
  const q = useQuery({ queryKey: ['audit', 'flag', flagId], queryFn: () => auditApi.list({ entityId: flagId, size: 10 }) });
  return (
    <div className="card">
      <h3>History</h3>
      {q.isLoading ? <Spinner /> : q.error ? <ErrorNotice error={q.error} /> : q.data && q.data.content.length === 0 ? <p className="muted">No changes recorded.</p> : (
        <table className="table">
          <thead><tr><th>When</th><th>Who</th><th>Action</th></tr></thead>
          <tbody>
            {q.data?.content.map((l) => (
              <tr key={l.id}>
                <td>{formatDate(l.createdAt)}</td>
                <td>{l.actorEmail ?? '—'}</td>
                <td><Badge tone="blue">{l.action}</Badge></td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      <Link to="/audit">Open the full audit log →</Link>
    </div>
  );
}

/* ---------- page ---------- */
export default function FlagDetail() {
  const { id = '' } = useParams();
  const { canEdit, canAdmin } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [envKey, setEnvKey] = useState<string | null>(null);

  const flag = useQuery({ queryKey: ['flag', id], queryFn: () => flagsApi.get(id) });
  const archive = useMutation({
    mutationFn: () => flagsApi.archive(id),
    onSuccess: () => { queryClient.invalidateQueries({ queryKey: ['flags'] }); navigate('/flags'); },
  });

  if (flag.isLoading) return <Spinner />;
  if (flag.error || !flag.data) return <ErrorNotice error={flag.error} />;

  const f = flag.data;
  const selected = f.environments.find((e) => e.environmentKey === envKey) ?? f.environments[0];
  const editable = canEdit && !f.archived;

  return (
    <>
      <PageHeader
        title={f.name}
        subtitle={f.key}
        actions={canAdmin && !f.archived && (
          <button className="btn btn-danger" disabled={archive.isPending} onClick={() => { if (window.confirm(`Archive "${f.name}"? Applications will get FLAG_ARCHIVED.`)) archive.mutate(); }}>Archive</button>
        )}
      />
      {f.archived && <div className="notice notice-error">This flag is archived and read-only.</div>}
      <ErrorNotice error={archive.error} />
      <DetailsForm flag={f} canEdit={editable} />

      <div className="tabs">
        {f.environments.map((s) => (
          <button key={s.environmentKey} className={s.environmentKey === selected?.environmentKey ? 'tab active' : 'tab'} onClick={() => setEnvKey(s.environmentKey)}>
            {s.environmentName} <Badge tone={s.enabled ? 'green' : 'gray'}>{s.enabled ? `${s.rolloutPercentage}%` : 'OFF'}</Badge>
          </button>
        ))}
      </div>

      {selected && (
        <>
          <StatePanel flag={f} state={selected} canEdit={editable} />
          <RulesEditor flagId={f.id} envKey={selected.environmentKey} canEdit={editable} />
          <Playground flagKey={f.key} envKey={selected.environmentKey} />
        </>
      )}
      {canAdmin && <History flagId={f.id} />}
    </>
  );
}
