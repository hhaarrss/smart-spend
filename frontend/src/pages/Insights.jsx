import React, { useState, useEffect } from 'react';
import { insightService } from '../services/api';
import { 
  BrainCircuit, ArrowUpRight, ArrowDownRight, AlertOctagon, 
  RefreshCw, BookmarkCheck, CalendarClock, ShieldAlert,
  TrendingUp, Loader2
} from 'lucide-react';
import CategoryBadge from '../components/CategoryBadge';

/**
 * AI Insights protected page component matching light fintech aesthetic.
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
      <div className="py-20 flex justify-center items-center">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="w-8 h-8 text-[#16803C] animate-spin" />
          <span className="text-xs font-semibold text-slate-500">Consulting AI Insights Engine...</span>
        </div>
      </div>
    );
  }

  const { spending_changes = [], anomalies = [], recurring = [], budget_alerts = [] } = insights || {};

  return (
    <div className="space-y-8 font-sans">
      
      {/* Page Header */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tight flex items-center gap-2.5">
            <div className="p-2 bg-[#EAF7EF] text-[#16803C] rounded-xl border border-emerald-200">
              <BrainCircuit className="w-6 h-6" />
            </div>
            AI Insights Dashboard
          </h1>
          <p className="text-xs sm:text-sm text-slate-500 font-medium mt-1">Advanced algorithms detecting anomalies, recurring EMIs, and spending habits</p>
        </div>

        <button
          onClick={fetchInsights}
          className="flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-bold text-slate-700 bg-white border border-slate-200 hover:bg-slate-50 transition-all shadow-xs cursor-pointer"
        >
          <RefreshCw className="w-3.5 h-3.5 text-[#16803C]" />
          <span>Refresh Analysis</span>
        </button>
      </div>

      {errorMsg && (
        <div className="flex items-center gap-2 p-4 bg-rose-50 border border-rose-200 rounded-2xl text-xs font-semibold text-rose-700">
          <AlertOctagon className="w-4 h-4 flex-shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* Column 1 & 2: Trends & Anomalies (2/3 width) */}
        <div className="lg:col-span-2 space-y-8">
          
          {/* Section 1: MoM Category Trends */}
          <div className="bg-white border border-slate-200 p-6 sm:p-8 rounded-2xl space-y-6 shadow-xs">
            <div>
              <h3 className="text-base font-extrabold text-slate-900 flex items-center gap-2">
                <TrendingUp className="w-5 h-5 text-[#16803C]" />
                Month-over-Month Spending Trends
              </h3>
              <p className="text-xs text-slate-500 font-medium mt-0.5">Changes in spending volume compared to previous billing cycle</p>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              {spending_changes.length > 0 ? (
                spending_changes.map((item, idx) => {
                  const isUp = item.direction === 'up';
                  return (
                    <div 
                      key={idx} 
                      className={`p-4 rounded-xl border bg-slate-50/50 flex justify-between items-center ${
                        isUp ? 'border-rose-200' : 'border-emerald-200'
                      }`}
                    >
                      <div>
                        <CategoryBadge category={item.category} />
                        <h4 className="text-slate-500 text-[10px] mt-2 font-bold uppercase tracking-wider">MoM Deviation</h4>
                      </div>

                      <div className="text-right flex items-center gap-2">
                        <div className={`p-1.5 rounded-lg ${isUp ? 'bg-rose-100 text-rose-600' : 'bg-emerald-100 text-emerald-700'}`}>
                          {isUp ? <ArrowUpRight className="w-4 h-4" /> : <ArrowDownRight className="w-4 h-4" />}
                        </div>
                        <div>
                          <span className={`text-sm font-black block ${isUp ? 'text-rose-600' : 'text-emerald-700'}`}>
                            {item.change_percent}%
                          </span>
                          <span className="text-[9px] text-slate-500 uppercase tracking-widest font-extrabold block">
                            {isUp ? 'Increased' : 'Reduced'}
                          </span>
                        </div>
                      </div>
                    </div>
                  );
                })
              ) : (
                <div className="col-span-2 text-center py-10 text-xs text-slate-400 border border-dashed border-slate-200 rounded-xl bg-slate-50">
                  Not enough historical database transactions to calculate MoM trends.
                </div>
              )}
            </div>
          </div>

          {/* Section 2: Anomaly Flags */}
          <div className="bg-white border border-slate-200 p-6 sm:p-8 rounded-2xl space-y-6 shadow-xs">
            <div>
              <h3 className="text-base font-extrabold text-slate-900 flex items-center gap-2">
                <ShieldAlert className="w-5 h-5 text-[#EF4444]" />
                Spike & Anomaly Notifications
              </h3>
              <p className="text-xs text-slate-500 font-medium mt-0.5">Current month charges that exceed 2x your historical category averages</p>
            </div>

            <div className="grid grid-cols-1 gap-3">
              {anomalies.length > 0 ? (
                anomalies.map((item, idx) => (
                  <div 
                    key={idx} 
                    className="p-4 rounded-xl border border-rose-200 bg-[#FFF0F0] flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4"
                  >
                    <div className="flex items-center gap-3">
                      <div className="p-2.5 bg-rose-100 text-[#EF4444] rounded-xl border border-rose-200">
                        <AlertOctagon className="w-5 h-5" />
                      </div>
                      <div>
                        <h4 className="font-extrabold text-slate-900 text-xs uppercase tracking-wider">{item.merchant}</h4>
                        <p className="text-xs text-slate-600 mt-0.5 capitalize">Category: <span className="text-slate-900 font-bold">{item.category}</span></p>
                      </div>
                    </div>

                    <div className="text-left sm:text-right">
                      <span className="text-base font-black text-[#EF4444] block">₹{item.amount.toLocaleString('en-IN')}</span>
                      <span className="text-[10px] text-slate-500 font-medium mt-0.5 block">
                        Rolling Category Avg: <span className="font-mono text-slate-700 font-bold">₹{item.avg.toLocaleString('en-IN')}</span>
                      </span>
                    </div>
                  </div>
                ))
              ) : (
                <div className="text-center py-10 text-xs text-slate-500 border border-dashed border-slate-200 rounded-xl bg-slate-50">
                  <BookmarkCheck className="w-7 h-7 text-[#16803C] mx-auto mb-2 opacity-80" />
                  <span>No large transaction anomalies detected in this billing cycle. Safe spendings!</span>
                </div>
              )}
            </div>
          </div>

        </div>

        {/* Column 3: Subscriptions & Budgets (1/3 width) */}
        <div className="space-y-8">
          
          {/* Section 3: Recurring Obligations */}
          <div className="bg-white border border-slate-200 p-6 sm:p-8 rounded-2xl space-y-6 shadow-xs flex flex-col">
            <div>
              <h3 className="text-base font-extrabold text-slate-900 flex items-center gap-2">
                <CalendarClock className="w-5 h-5 text-[#16803C]" />
                Recurring & Subscriptions
              </h3>
              <p className="text-xs text-slate-500 font-medium mt-0.5">Identified monthly subscription charges</p>
            </div>

            <div className="space-y-3 flex-grow">
              {recurring.length > 0 ? (
                recurring.map((item, idx) => (
                  <div 
                    key={idx} 
                    className="p-3.5 rounded-xl border border-slate-200 bg-slate-50 flex justify-between items-center"
                  >
                    <div>
                      <h4 className="font-bold text-slate-900 text-xs capitalize">{item.merchant}</h4>
                      <span className="inline-flex items-center gap-1 mt-1 text-[9px] font-extrabold text-[#16803C] bg-[#EAF7EF] px-2 py-0.5 rounded border border-emerald-200 uppercase tracking-widest font-mono">
                        {item.frequency}
                      </span>
                    </div>

                    <div className="text-right">
                      <span className="text-xs font-black text-slate-900">₹{item.amount.toLocaleString('en-IN')}/mo</span>
                    </div>
                  </div>
                ))
              ) : (
                <div className="text-center py-10 text-xs text-slate-400 border border-dashed border-slate-200 rounded-xl bg-slate-50">
                  No recurring subscriptions or EMIs identified in transaction records.
                </div>
              )}
            </div>
          </div>

          {/* Section 4: Budget Threshold Warnings */}
          <div className="bg-white border border-slate-200 p-6 sm:p-8 rounded-2xl space-y-6 shadow-xs flex flex-col">
            <div>
              <h3 className="text-base font-extrabold text-slate-900 flex items-center gap-2">
                <ShieldAlert className="w-5 h-5 text-[#F59E0B]" />
                Critical Budget Warnings
              </h3>
              <p className="text-xs text-slate-500 font-medium mt-0.5">Categories utilizing over 80% of limit thresholds</p>
            </div>

            <div className="space-y-3 flex-grow">
              {budget_alerts.length > 0 ? (
                budget_alerts.map((item, idx) => (
                  <div 
                    key={idx} 
                    className="p-4 rounded-xl border border-amber-200 bg-[#FFF9EE] text-xs space-y-2"
                  >
                    <div className="flex justify-between items-center">
                      <span className="font-extrabold text-slate-900 uppercase tracking-wider">{item.category}</span>
                      <span className="font-black text-[#F59E0B] text-xs">{item.percent}% used</span>
                    </div>

                    <div className="h-1.5 w-full bg-slate-200 rounded-full overflow-hidden">
                      <div 
                        className="h-full bg-[#F59E0B] rounded-full" 
                        style={{ width: `${Math.min(item.percent, 100)}%` }}
                      />
                    </div>

                    <div className="text-[10px] text-slate-600 flex justify-between font-mono font-medium">
                      <span>Spent: ₹{item.spent.toLocaleString('en-IN')}</span>
                      <span>Limit: ₹{item.limit.toLocaleString('en-IN')}</span>
                    </div>
                  </div>
                ))
              ) : (
                <div className="text-center py-10 text-xs text-slate-400 border border-dashed border-slate-200 rounded-xl bg-slate-50">
                  All category spending budgets are fully optimized and under threshold constraints.
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
