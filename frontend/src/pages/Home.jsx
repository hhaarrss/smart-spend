import React, { useEffect, useState, useCallback } from 'react';
import {
  User, Search, TrendingUp, TrendingDown, ArrowRight,
  Layers, Target, ChevronRight, AlertCircle, Sparkles,
  BarChart3, Tag, RefreshCw, Wallet, IndianRupee
} from 'lucide-react';
import { homeService } from '../services/api';
import { getCategoryMeta } from '../utils/categoryMeta';

/* ─────────────────────────────────────────
   Stub user shown while data loads (or if AUTH_STUB=true hasn't set a real name yet)
───────────────────────────────────────── */
const STUB_USER = { full_name: 'Test User', id: 1 };

/* ─────────────────────────────────────────
   Formatters
───────────────────────────────────────── */
function fmtINR(amount) {
  if (amount == null) return '—';
  return `₹${Number(amount).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`;
}

function fmtDate(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short' });
}

function monthName(month, year) {
  return new Date(year, month - 1, 1).toLocaleString('default', { month: 'long', year: 'numeric' });
}

/* ─────────────────────────────────────────
   Skeleton loader
───────────────────────────────────────── */
function Skeleton({ className = '' }) {
  return (
    <div className={`animate-pulse bg-slate-200 rounded-xl ${className}`} />
  );
}

/* ─────────────────────────────────────────
   MoM badge
───────────────────────────────────────── */
function MoMBadge({ pct }) {
  if (pct == null) return (
    <span className="text-xs font-semibold text-slate-400 bg-slate-100 rounded-full px-2.5 py-0.5">
      No prev data
    </span>
  );
  const up = pct >= 0;
  return (
    <span className={`inline-flex items-center gap-1 text-xs font-bold rounded-full px-2.5 py-0.5 ${
      up ? 'bg-rose-50 text-rose-600' : 'bg-emerald-50 text-emerald-700'
    }`}>
      {up ? <TrendingUp className="w-3 h-3" /> : <TrendingDown className="w-3 h-3" />}
      {up ? '+' : ''}{pct}%
    </span>
  );
}

/* ─────────────────────────────────────────
   Category chip
───────────────────────────────────────── */
function CategoryChip({ cat, spent }) {
  const meta = getCategoryMeta(cat);
  const Icon = meta.icon;
  return (
    <div className={`flex items-center justify-between p-3.5 rounded-2xl border border-slate-100 bg-white shadow-xs hover:shadow-sm transition-shadow`}>
      <div className="flex items-center gap-2.5">
        <div className={`p-2 rounded-xl ${meta.bg} ${meta.text}`}>
          <Icon className="w-4 h-4" />
        </div>
        <span className="text-sm font-bold text-slate-800 capitalize">{cat}</span>
      </div>
      <span className="text-sm font-extrabold text-slate-900">{fmtINR(spent)}</span>
    </div>
  );
}

/* ─────────────────────────────────────────
   Budget progress row
───────────────────────────────────────── */
function BudgetRow({ category, spent, limit, percent_used, is_alert }) {
  const pct = Math.min(percent_used, 100);
  const barColor = percent_used > 90
    ? 'bg-rose-500'
    : percent_used >= 60
    ? 'bg-amber-400'
    : 'bg-emerald-500';
  const trackColor = percent_used > 90
    ? 'bg-rose-100'
    : percent_used >= 60
    ? 'bg-amber-100'
    : 'bg-emerald-100';

  return (
    <div className="space-y-1.5">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-1.5">
          {is_alert && <AlertCircle className="w-3.5 h-3.5 text-rose-500 flex-shrink-0" />}
          <span className="text-xs font-bold text-slate-700 capitalize">{category}</span>
        </div>
        <span className={`text-xs font-extrabold ${
          percent_used > 90 ? 'text-rose-600' : percent_used >= 60 ? 'text-amber-600' : 'text-emerald-700'
        }`}>
          {percent_used.toFixed(0)}%
        </span>
      </div>
      <div className={`h-2 rounded-full overflow-hidden ${trackColor}`}>
        <div
          className={`h-full rounded-full transition-all duration-700 ease-out ${barColor}`}
          style={{ width: `${pct}%` }}
        />
      </div>
      <div className="flex justify-between text-[10px] text-slate-400 font-medium">
        <span>{fmtINR(spent)} spent</span>
        <span>{fmtINR(limit)} limit</span>
      </div>
    </div>
  );
}

/* ─────────────────────────────────────────
   Recent transaction row
───────────────────────────────────────── */
function TxRow({ tx }) {
  const isDebit = tx.type === 'debit';
  const meta = getCategoryMeta(tx.category);
  const Icon = meta.icon;

  return (
    <div className="flex items-center gap-3 py-2.5 px-1 group">
      <div className={`p-2.5 rounded-xl flex-shrink-0 ${meta.bg} ${meta.text}`}>
        <Icon className="w-4 h-4" />
      </div>
      <div className="flex-1 min-w-0">
        <div className="text-sm font-bold text-slate-800 truncate">
          {tx.merchant || tx.category || 'Unknown'}
        </div>
        <div className="text-[11px] text-slate-400 font-medium">
          {tx.category || '—'} · {fmtDate(tx.date)}
        </div>
      </div>
      <div className={`text-sm font-extrabold flex-shrink-0 ${
        isDebit ? 'text-slate-900' : 'text-emerald-600'
      }`}>
        {isDebit ? '-' : '+'}{fmtINR(tx.amount)}
      </div>
    </div>
  );
}

/* ─────────────────────────────────────────
   Main Home Screen
───────────────────────────────────────── */
export default function Home() {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const fetchData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const result = await homeService.getHomeData();
      setData(result);
    } catch (err) {
      console.error('[Home] Failed to fetch home data:', err);
      setError(err?.response?.data?.detail || 'Failed to load home data.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const user = data?.user ?? STUB_USER;
  const overview = data?.overview ?? {};
  const recentTxs = data?.recent_transactions ?? [];
  const topCats = data?.top_categories ?? [];
  const budgetSnap = data?.budget_snapshot ?? [];
  const label = data ? monthName(data.month, data.year) : '';

  /* ── Greeting ── */
  const hour = new Date().getHours();
  const greeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  const firstName = (user.full_name || 'User').split(' ')[0];

  return (
    <div
      id="home-screen"
      className="min-h-screen bg-[#FAFAF8] pb-12"
    >
      {/* ── Top Bar ── */}
      <div
        id="home-topbar"
        className="flex items-center justify-between px-4 pt-5 pb-3 sticky top-0 z-10 bg-[#FAFAF8]/95 backdrop-blur-sm border-b border-slate-100"
      >
        {/* Left: user icon + name */}
        <button
          id="home-user-btn"
          type="button"
          aria-label="User profile"
          className="flex items-center gap-2.5 group cursor-default"
        >
          <div className="w-9 h-9 rounded-full bg-gradient-to-br from-emerald-400 to-emerald-700 flex items-center justify-center shadow-sm flex-shrink-0">
            <User className="w-4 h-4 text-white" />
          </div>
          <div className="text-left">
            <div className="text-[11px] text-slate-400 font-medium leading-none">{greeting}</div>
            <div className="text-sm font-extrabold text-slate-900 leading-tight">{firstName}</div>
          </div>
        </button>

        {/* Right: search icon */}
        <button
          id="home-search-btn"
          type="button"
          aria-label="Search"
          className="w-9 h-9 rounded-full border border-slate-200 bg-white flex items-center justify-center shadow-xs hover:bg-slate-50 transition-colors cursor-default"
        >
          <Search className="w-4 h-4 text-slate-500" />
        </button>
      </div>

      <div className="px-4 pt-5 space-y-5">
        {/* ── Hero: Total Spend ── */}
        <section
          id="home-hero"
          className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-[#16803C] via-[#16803C] to-emerald-800 p-6 shadow-xl"
        >
          {/* Decorative orbs */}
          <div className="absolute -top-8 -right-8 w-36 h-36 bg-white/5 rounded-full" />
          <div className="absolute -bottom-6 -left-4 w-28 h-28 bg-black/10 rounded-full" />

          <div className="relative z-10">
            <div className="flex items-center justify-between mb-4">
              <div>
                <p className="text-xs text-emerald-200 font-semibold tracking-wide uppercase mb-1">
                  Total spend — {label}
                </p>
                {loading ? (
                  <Skeleton className="w-40 h-9 bg-white/20" />
                ) : (
                  <h2 className="text-3xl font-black text-white tracking-tight">
                    {fmtINR(overview.total_spent)}
                  </h2>
                )}
              </div>
              {!loading && <MoMBadge pct={overview.mom_change_percent} />}
            </div>

            {/* Income + Budget CTA */}
            <div className="flex items-center gap-3 mt-5">
              {/* Income card */}
              <div className="flex-1 bg-white/10 backdrop-blur-sm rounded-2xl p-3.5 border border-white/10">
                <div className="flex items-center gap-1.5 mb-1">
                  <IndianRupee className="w-3 h-3 text-emerald-200" />
                  <span className="text-[10px] font-bold text-emerald-200 uppercase tracking-wide">Income</span>
                </div>
                {loading ? (
                  <Skeleton className="w-24 h-5 bg-white/20" />
                ) : (
                  <div className="text-base font-black text-white">{fmtINR(overview.total_income)}</div>
                )}
              </div>

              {/* Set monthly budget button */}
              <button
                id="home-set-budget-btn"
                type="button"
                aria-label="Set monthly budget"
                className="flex-1 bg-white/10 backdrop-blur-sm rounded-2xl p-3.5 border border-white/10 text-left hover:bg-white/15 transition-colors cursor-default"
              >
                <div className="flex items-center gap-1.5 mb-1">
                  <Target className="w-3 h-3 text-emerald-200" />
                  <span className="text-[10px] font-bold text-emerald-200 uppercase tracking-wide">Budget</span>
                </div>
                <div className="text-sm font-bold text-white/80">Set limit →</div>
              </button>
            </div>
          </div>
        </section>

        {/* ── Quick Actions ── */}
        <div className="grid grid-cols-2 gap-3">
          {/* Trends */}
          <button
            id="home-trends-btn"
            type="button"
            aria-label="View Trends"
            className="flex items-center gap-3 p-4 bg-white rounded-2xl border border-slate-200 shadow-xs hover:shadow-sm hover:border-slate-300 transition-all cursor-default text-left"
          >
            <div className="p-2.5 rounded-xl bg-indigo-50 text-indigo-700">
              <BarChart3 className="w-4 h-4" />
            </div>
            <div>
              <div className="text-sm font-extrabold text-slate-800">Trends</div>
              <div className="text-[10px] text-slate-400 font-medium">MoM analysis</div>
            </div>
          </button>

          {/* Categories */}
          <button
            id="home-categories-btn"
            type="button"
            aria-label="View Categories"
            className="flex items-center gap-3 p-4 bg-white rounded-2xl border border-slate-200 shadow-xs hover:shadow-sm hover:border-slate-300 transition-all cursor-default text-left"
          >
            <div className="p-2.5 rounded-xl bg-purple-50 text-purple-700">
              <Tag className="w-4 h-4" />
            </div>
            <div>
              <div className="text-sm font-extrabold text-slate-800">Categories</div>
              <div className="text-[10px] text-slate-400 font-medium">All groups</div>
            </div>
          </button>
        </div>

        {/* ── Recent Transactions ── */}
        <section id="home-recent-transactions" className="bg-white rounded-3xl border border-slate-200 shadow-xs overflow-hidden">
          <div className="flex items-center justify-between px-5 pt-5 pb-2">
            <h3 className="text-sm font-extrabold text-slate-900">Recent Transactions</h3>
            <button
              id="home-see-all-tx-btn"
              type="button"
              aria-label="See all transactions"
              className="flex items-center gap-0.5 text-xs font-bold text-emerald-700 hover:text-emerald-800 transition-colors cursor-default"
            >
              See all <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>

          <div className="px-4 pb-4 divide-y divide-slate-50">
            {loading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <div key={i} className="flex items-center gap-3 py-3">
                  <Skeleton className="w-10 h-10 rounded-xl flex-shrink-0" />
                  <div className="flex-1 space-y-1.5">
                    <Skeleton className="w-32 h-3.5" />
                    <Skeleton className="w-20 h-2.5" />
                  </div>
                  <Skeleton className="w-16 h-4" />
                </div>
              ))
            ) : recentTxs.length === 0 ? (
              <div className="py-8 text-center text-xs text-slate-400 font-medium">
                No transactions yet for this month.
              </div>
            ) : (
              recentTxs.map(tx => (
                <TxRow key={tx.id} tx={tx} />
              ))
            )}
          </div>
        </section>

        {/* ── Categories Preview (top 4) ── */}
        <section id="home-categories-preview">
          <div className="flex items-center justify-between mb-3">
            <h3 className="text-sm font-extrabold text-slate-900">Categories</h3>
            <button
              id="home-see-all-cats-btn"
              type="button"
              aria-label="See all categories"
              className="flex items-center gap-0.5 text-xs font-bold text-emerald-700 hover:text-emerald-800 transition-colors cursor-default"
            >
              See all <ChevronRight className="w-3.5 h-3.5" />
            </button>
          </div>

          {loading ? (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {Array.from({ length: 4 }).map((_, i) => (
                <Skeleton key={i} className="h-14" />
              ))}
            </div>
          ) : topCats.length === 0 ? (
            <div className="py-8 text-center text-xs text-slate-400 border border-dashed border-slate-200 rounded-2xl bg-slate-50">
              No category data for this month.
            </div>
          ) : (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              {topCats.map(cat => (
                <CategoryChip key={cat.category} cat={cat.category} spent={cat.spent} />
              ))}
            </div>
          )}
        </section>

        {/* ── Budget Snapshot (top 4) ── */}
        {(loading || budgetSnap.length > 0) && (
          <section id="home-budget-snapshot" className="bg-white rounded-3xl border border-slate-200 shadow-xs p-5">
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-extrabold text-slate-900">Budget Snapshot</h3>
              <div className="flex items-center gap-1.5 text-[10px] font-bold text-slate-400 bg-slate-50 rounded-full px-2.5 py-1 border border-slate-100">
                <Wallet className="w-3 h-3" />
                Top 4
              </div>
            </div>

            {loading ? (
              <div className="space-y-5">
                {Array.from({ length: 3 }).map((_, i) => (
                  <div key={i} className="space-y-2">
                    <Skeleton className="w-full h-3" />
                    <Skeleton className="w-full h-2 rounded-full" />
                  </div>
                ))}
              </div>
            ) : (
              <div className="space-y-5">
                {budgetSnap.map(b => (
                  <BudgetRow key={b.category} {...b} />
                ))}
              </div>
            )}
          </section>
        )}

        {/* ── Error state ── */}
        {error && (
          <div className="flex items-start gap-3 p-4 bg-rose-50 border border-rose-200 rounded-2xl">
            <AlertCircle className="w-4 h-4 text-rose-500 flex-shrink-0 mt-0.5" />
            <div className="flex-1">
              <p className="text-sm font-bold text-rose-700">Failed to load</p>
              <p className="text-xs text-rose-500 mt-0.5">{error}</p>
            </div>
            <button
              id="home-retry-btn"
              type="button"
              aria-label="Retry"
              onClick={fetchData}
              className="flex items-center gap-1 text-xs font-bold text-rose-600 hover:text-rose-800 transition-colors"
            >
              <RefreshCw className="w-3.5 h-3.5" /> Retry
            </button>
          </div>
        )}

        {/* ── Needs Review nudge ── */}
        {!loading && (overview.needs_review_count ?? 0) > 0 && (
          <div
            id="home-needs-review-nudge"
            className="flex items-center gap-3 p-4 bg-amber-50 border border-amber-200 rounded-2xl"
          >
            <Sparkles className="w-4 h-4 text-amber-500 flex-shrink-0" />
            <p className="text-xs font-semibold text-amber-800 flex-1">
              <span className="font-extrabold">{overview.needs_review_count}</span> transaction{overview.needs_review_count === 1 ? '' : 's'} need{overview.needs_review_count === 1 ? 's' : ''} review.
            </p>
            <ChevronRight className="w-3.5 h-3.5 text-amber-400" />
          </div>
        )}
      </div>
    </div>
  );
}
