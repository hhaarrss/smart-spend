import React, { useState, useEffect } from 'react';
import { insightService } from '../services/api';
import { 
  BrainCircuit, ArrowUpRight, ArrowDownRight, AlertOctagon, 
  RefreshCw, BookmarkCheck, CalendarClock, ShieldAlert,
  HelpCircle, Sparkles, TrendingUp, DollarSign, Loader2
} from 'lucide-react';
import CategoryBadge from '../components/CategoryBadge';

/**
 * AI Insights protected page component.
 */
const Insights = () => {
  const [insights, setInsights] = useState(null);
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');

  useEffect(() => {
    fetchInsights();
  }, []);

  const fetchInsights = async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const data = await insightService.getSummary();
      setInsights(data);
      setLoading(false);
    } catch (err) {
      setLoading(false);
      setErrorMsg('Could not fetch insights. Please check backend services.');
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 flex justify-center items-center">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="w-10 h-10 text-indigo-500 animate-spin" />
          <span className="text-sm font-semibold text-slate-400">Consulting AI Insights Engine...</span>
        </div>
      </div>
    );
  }

  const { spending_changes = [], anomalies = [], recurring = [], budget_alerts = [] } = insights || {};

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8 bg-slate-950 min-h-screen text-slate-100 font-sans">
      
      {/* Page Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight flex items-center gap-2.5">
            <div className="p-2 bg-gradient-to-tr from-indigo-600 to-violet-500 rounded-xl text-white">
              <BrainCircuit className="w-6 h-6" />
            </div>
            AI Insights Dashboard
          </h1>
          <p className="text-sm text-slate-400 mt-1">Advanced algorithms detecting anomalies, recurring EMIs, and spending habits</p>
        </div>

        {/* Sync Trigger button */}
        <button
          onClick={fetchInsights}
          className="flex items-center gap-2 px-4 py-2.5 rounded-2xl text-xs font-semibold text-slate-300 border border-slate-850 hover:bg-slate-900 transition-all cursor-pointer bg-slate-950"
        >
          <RefreshCw className="w-4 h-4 text-indigo-400" />
          <span>Refresh Analysis</span>
        </button>
      </div>

      {errorMsg && (
        <div className="flex items-center gap-2 p-4 bg-rose-500/10 border border-rose-500/20 rounded-3xl text-sm text-rose-400 mb-5">
          <AlertOctagon className="w-5 h-5 flex-shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Grid: 2 columns (Left: Trends & Anomalies, Right: Subscriptions & Budgets) */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* Column 1 & 2: Trends & Anomalies (2/3 width on desktop) */}
        <div className="lg:col-span-2 space-y-8">
          
          {/* Section 1: MoM Category Trends */}
          <div className="bg-slate-900/30 border border-slate-850 p-6 sm:p-8 rounded-3xl space-y-6 shadow-xl">
            <div>
              <h3 className="text-lg font-bold text-white flex items-center gap-2">
                <TrendingUp className="w-5 h-5 text-indigo-400" />
                Month-over-Month Spending Trends
              </h3>
              <p className="text-xs text-slate-400 mt-1">Changes in spending volume compared to previous billing cycle</p>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {spending_changes.length > 0 ? (
                spending_changes.map((item, idx) => {
                  const isUp = item.direction === 'up';
                  return (
                    <div 
                      key={idx} 
                      className={`p-5 rounded-2xl border bg-slate-900/50 flex justify-between items-center transition-all hover:scale-[1.01] ${
                        isUp ? 'border-rose-500/15' : 'border-emerald-500/15'
                      }`}
                    >
                      <div>
                        <CategoryBadge category={item.category} />
                        <h4 className="text-slate-400 text-xs mt-2.5 font-medium uppercase tracking-wider">MoM Deviation</h4>
                      </div>

                      <div className="text-right flex items-center gap-1.5">
                        <div className={`p-1.5 rounded-lg ${isUp ? 'bg-rose-500/10 text-rose-400' : 'bg-emerald-500/10 text-emerald-400'}`}>
                          {isUp ? <ArrowUpRight className="w-5 h-5" /> : <ArrowDownRight className="w-5 h-5" />}
                        </div>
                        <div>
                          <span className={`text-base font-extrabold block ${isUp ? 'text-rose-400' : 'text-emerald-400'}`}>
                            {item.change_percent}%
                          </span>
                          <span className="text-[10px] text-slate-500 uppercase tracking-widest font-semibold block mt-0.5">
                            {isUp ? 'Increased' : 'Reduced'}
                          </span>
                        </div>
                      </div>
                    </div>
                  );
                })
              ) : (
                <div className="col-span-2 text-center py-10 text-xs text-slate-500 border border-dashed border-slate-800 rounded-2xl">
                  Not enough historical database transactions to calculate MoM trends.
                </div>
              )}
            </div>
          </div>

          {/* Section 2: Anomaly Flags */}
          <div className="bg-slate-900/30 border border-slate-850 p-6 sm:p-8 rounded-3xl space-y-6 shadow-xl">
            <div>
              <h3 className="text-lg font-bold text-white flex items-center gap-2">
                <ShieldAlert className="w-5 h-5 text-rose-400" />
                Spike & Anomaly Notifications
              </h3>
              <p className="text-xs text-slate-400 mt-1">Current month charges that exceed 2x your historical category averages</p>
            </div>

            <div className="grid grid-cols-1 gap-4">
              {anomalies.length > 0 ? (
                anomalies.map((item, idx) => (
                  <div 
                    key={idx} 
                    className="p-5 rounded-2xl border border-rose-500/20 bg-rose-500/5 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4 animate-pulse-slow"
                  >
                    <div className="flex items-center gap-3">
                      <div className="p-2.5 bg-rose-500/10 text-rose-400 rounded-xl border border-rose-500/20">
                        <AlertOctagon className="w-5 h-5" />
                      </div>
                      <div>
                        <h4 className="font-bold text-white text-sm uppercase tracking-wide">{item.merchant}</h4>
                        <p className="text-xs text-slate-400 mt-0.5 capitalize">Category: <span className="text-slate-300 font-semibold">{item.category}</span></p>
                      </div>
                    </div>

                    <div className="text-left sm:text-right">
                      <span className="text-base font-extrabold text-rose-400 block">₹{item.amount.toLocaleString('en-IN')}</span>
                      <span className="text-[10px] text-slate-500 mt-0.5 block">
                        Rolling Category Avg: <span className="font-mono text-slate-400">₹{item.avg.toLocaleString('en-IN')}</span>
                      </span>
                    </div>
                  </div>
                ))
              ) : (
                <div className="text-center py-10 text-xs text-slate-500 border border-dashed border-slate-800 rounded-2xl bg-slate-900/10">
                  <BookmarkCheck className="w-8 h-8 text-emerald-400 mx-auto mb-2 opacity-75" />
                  <span>No large transaction anomalies detected in this billing cycle. Safe spendings!</span>
                </div>
              )}
            </div>
          </div>

        </div>

        {/* Column 3: Subscriptions & Budgets (1/3 width) */}
        <div className="space-y-8">
          
          {/* Section 3: Recurring Obligations */}
          <div className="bg-slate-900/30 border border-slate-850 p-6 sm:p-8 rounded-3xl space-y-6 shadow-xl flex flex-col">
            <div>
              <h3 className="text-lg font-bold text-white flex items-center gap-2">
                <CalendarClock className="w-5 h-5 text-indigo-400" />
                Recurring & Subscriptions
              </h3>
              <p className="text-xs text-slate-400 mt-1">Identified monthly subscription charges</p>
            </div>

            <div className="space-y-3.5 flex-grow">
              {recurring.length > 0 ? (
                recurring.map((item, idx) => (
                  <div 
                    key={idx} 
                    className="p-4 rounded-xl border border-slate-800 bg-slate-950 flex justify-between items-center"
                  >
                    <div>
                      <h4 className="font-semibold text-white text-sm capitalize">{item.merchant}</h4>
                      <span className="inline-flex items-center gap-1 mt-1 text-[9px] font-bold text-indigo-400 bg-indigo-500/10 px-2 py-0.5 rounded border border-indigo-500/20 uppercase tracking-widest font-mono">
                        {item.frequency}
                      </span>
                    </div>

                    <div className="text-right">
                      <span className="text-sm font-bold text-slate-200">₹{item.amount.toLocaleString('en-IN')}/mo</span>
                    </div>
                  </div>
                ))
              ) : (
                <div className="text-center py-10 text-xs text-slate-500 border border-dashed border-slate-800 rounded-2xl bg-slate-900/10">
                  No recurring subscriptions or EMIs identified in transaction records.
                </div>
              )}
            </div>
          </div>

          {/* Section 4: Budget Threshold Warnings */}
          <div className="bg-slate-900/30 border border-slate-850 p-6 sm:p-8 rounded-3xl space-y-6 shadow-xl flex flex-col">
            <div>
              <h3 className="text-lg font-bold text-white flex items-center gap-2">
                <ShieldAlert className="w-5 h-5 text-amber-400" />
                Critical Budget Warnings
              </h3>
              <p className="text-xs text-slate-400 mt-1">Categories utilizing over 80% of limit thresholds</p>
            </div>

            <div className="space-y-3.5 flex-grow">
              {budget_alerts.length > 0 ? (
                budget_alerts.map((item, idx) => (
                  <div 
                    key={idx} 
                    className="p-4 rounded-2xl border border-amber-500/25 bg-amber-500/5 text-xs space-y-2"
                  >
                    <div className="flex justify-between items-center">
                      <span className="font-bold text-white uppercase tracking-wider">{item.category}</span>
                      <span className="font-extrabold text-amber-400 text-sm">{item.percent}% used</span>
                    </div>

                    <div className="h-1.5 w-full bg-slate-800 rounded-full overflow-hidden">
                      <div 
                        className="h-full bg-amber-500 rounded-full" 
                        style={{ width: `${Math.min(item.percent, 100)}%` }}
                      />
                    </div>

                    <div className="text-[10px] text-slate-500 flex justify-between font-mono">
                      <span>Spent: ₹{item.spent.toLocaleString('en-IN')}</span>
                      <span>Limit: ₹{item.limit.toLocaleString('en-IN')}</span>
                    </div>
                  </div>
                ))
              ) : (
                <div className="text-center py-10 text-xs text-slate-500 border border-dashed border-slate-800 rounded-2xl bg-slate-900/10">
                  All category spending budgets are fully optimized and under threshold constraints. Excellent job!
                </div>
              )}
            </div>
          </div>

        </div>

      </div>
    </div>
  );
};

export default Insights;
