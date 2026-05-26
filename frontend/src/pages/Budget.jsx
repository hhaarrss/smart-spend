import React, { useState, useEffect } from 'react';
import { budgetService, transactionService } from '../services/api';
import { 
  Wallet, PlusCircle, AlertCircle, CheckCircle2, 
  Loader2, Landmark, Settings, ShieldCheck, Info
} from 'lucide-react';
import BudgetProgressBar from '../components/BudgetProgressBar';

/**
 * Budget Limit protected page.
 */
const Budget = () => {
  const [budgets, setBudgets] = useState([]);
  const [categoryTotals, setCategoryTotals] = useState({});
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');

  // Form states
  const [category, setCategory] = useState('Food');
  const [monthlyLimit, setMonthlyLimit] = useState('');
  const [alertAtPercent, setAlertAtPercent] = useState(80);
  const [isFamilyLimit, setIsFamilyLimit] = useState(false);
  
  const [formLoading, setFormLoading] = useState(false);
  const [formSuccess, setFormSuccess] = useState('');
  const [formError, setFormError] = useState('');

  const CATEGORIES = [
    'Food', 'Travel', 'Shopping', 'Utilities', 'Entertainment',
    'Healthcare', 'Education', 'Fuel', 'Groceries', 'Other'
  ];

  useEffect(() => {
    loadBudgetData();
  }, []);

  const loadBudgetData = async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      // Calculate current month (YYYY-MM)
      const now = new Date();
      const yyyy = now.getFullYear();
      const mm = String(now.getMonth() + 1).padStart(2, '0');
      const currentMonth = `${yyyy}-${mm}`;

      // Calculate start and end date of current month for spent calculation
      const firstDay = new Date(yyyy, now.getMonth(), 1).toISOString();
      const lastDay = new Date(yyyy, now.getMonth() + 1, 0, 23, 59, 59, 999).toISOString();

      // Parallel API calls
      const [budgetLimits, txs] = await Promise.all([
        budgetService.getBudgets(),
        transactionService.listTransactions({ start_date: firstDay, end_date: lastDay })
      ]);

      setBudgets(budgetLimits);

      // Aggregate transactions (debits) by category client-side
      const totals = {};
      txs.filter(t => t.type === 'debit').forEach(t => {
        totals[t.category] = (totals[t.category] || 0) + parseFloat(t.amount);
      });
      setCategoryTotals(totals);
      
      setLoading(false);
    } catch (err) {
      setLoading(false);
      setErrorMsg('Could not load budget constraints. Check backend connection.');
    }
  };

  const handleSetBudget = async (e) => {
    e.preventDefault();
    if (!monthlyLimit || parseFloat(monthlyLimit) <= 0) {
      setFormError('Please enter a valid positive monthly limit.');
      return;
    }

    setFormLoading(true);
    setFormError('');
    setFormSuccess('');

    try {
      await budgetService.setBudget({
        category,
        monthly_limit: parseFloat(monthlyLimit),
        alert_at_percent: parseFloat(alertAtPercent),
        is_family_limit: isFamilyLimit
      });

      setFormSuccess(`Successfully configured budget for ${category}!`);
      setMonthlyLimit('');
      setFormLoading(false);
      
      // Reload lists
      loadBudgetData();
    } catch (err) {
      setFormLoading(false);
      setFormError(err.response?.data?.detail || 'Failed to save budget settings.');
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 flex justify-center items-center">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="w-10 h-10 text-indigo-500 animate-spin" />
          <span className="text-sm font-semibold text-slate-400">Loading budget trackers...</span>
        </div>
      </div>
    );
  }

  // Map active limits by category for lookup
  const activeBudgetsMap = {};
  budgets.forEach(b => {
    activeBudgetsMap[b.category] = b;
  });

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8 bg-slate-950 min-h-screen text-slate-100 font-sans">
      
      {/* Title */}
      <div>
        <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight flex items-center gap-2">
          <Wallet className="w-8 h-8 text-indigo-500" />
          Monthly Budget Limits
        </h1>
        <p className="text-sm text-slate-400 mt-1">Define spending constraints and receive alerts when nearing thresholds</p>
      </div>

      {errorMsg && (
        <div className="flex items-center gap-2 p-4 bg-rose-500/10 border border-rose-500/20 rounded-3xl text-sm text-rose-400 mb-5">
          <AlertCircle className="w-5 h-5 flex-shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Grid: Layout splitting form and list */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* Panel 1: Configure Limits Form (1/3 width) */}
        <div className="bg-slate-900 border border-slate-850 p-6 sm:p-8 rounded-3xl relative overflow-hidden shadow-2xl h-fit">
          <div className="absolute top-0 left-0 w-full h-[3px] bg-gradient-to-r from-indigo-500 to-violet-500" />
          
          <h3 className="text-lg font-bold text-white mb-1">Set Category Limit</h3>
          <p className="text-xs text-slate-400 mb-6">Create or update spending boundaries</p>

          {/* Form Alerts */}
          {formError && (
            <div className="flex items-center gap-2 p-3 bg-rose-500/10 border border-rose-500/20 rounded-2xl text-xs text-rose-400 mb-5">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span>{formError}</span>
            </div>
          )}

          {formSuccess && (
            <div className="flex items-center gap-2 p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-2xl text-xs text-emerald-400 mb-5">
              <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
              <span>{formSuccess}</span>
            </div>
          )}

          <form onSubmit={handleSetBudget} className="space-y-5">
            {/* Category Select */}
            <div>
              <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Category</label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="block w-full px-3.5 py-3 rounded-2xl bg-slate-950 border border-slate-800 text-white placeholder-slate-700 focus:outline-none focus:border-indigo-500 transition-all text-sm capitalize cursor-pointer"
              >
                {CATEGORIES.map(cat => (
                  <option key={cat} value={cat} className="capitalize">{cat}</option>
                ))}
              </select>
            </div>

            {/* Limit Input */}
            <div>
              <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Monthly limit (INR)</label>
              <div className="relative">
                <input
                  type="number"
                  required
                  value={monthlyLimit}
                  onChange={(e) => setMonthlyLimit(e.target.value)}
                  placeholder="e.g. 10000"
                  className="block w-full px-3.5 py-3 rounded-2xl bg-slate-950 border border-slate-800 text-white placeholder-slate-700 focus:outline-none focus:border-indigo-500 transition-all text-sm font-semibold"
                />
              </div>
            </div>

            {/* Alert percentage threshold input */}
            <div>
              <div className="flex justify-between items-center mb-2">
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider">Alert Threshold</label>
                <span className="text-xs font-bold text-indigo-400">{alertAtPercent}%</span>
              </div>
              <input
                type="range"
                min="10"
                max="100"
                step="5"
                value={alertAtPercent}
                onChange={(e) => setAlertAtPercent(parseInt(e.target.value))}
                className="w-full h-1.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-indigo-550"
              />
              <div className="flex justify-between text-[10px] text-slate-500 font-mono mt-1">
                <span>10%</span>
                <span>80% (Default)</span>
                <span>100%</span>
              </div>
            </div>

            {/* Family Limit Checkbox */}
            <div className="flex items-center gap-2 p-3 bg-slate-950 rounded-2xl border border-slate-800">
              <input
                type="checkbox"
                id="isFamilyLimit"
                checked={isFamilyLimit}
                onChange={(e) => setIsFamilyLimit(e.target.checked)}
                className="w-4 h-4 rounded text-indigo-650 bg-slate-900 border-slate-800 focus:ring-indigo-600 focus:ring-offset-slate-950 focus:ring-2 cursor-pointer"
              />
              <label htmlFor="isFamilyLimit" className="text-xs font-semibold text-slate-400 select-none cursor-pointer flex items-center gap-1">
                <ShieldCheck className="w-3.5 h-3.5 text-indigo-400" />
                Apply as Family-wide budget
              </label>
            </div>

            {/* Submit Button */}
            <button
              type="submit"
              disabled={formLoading}
              className="w-full py-3.5 px-4 rounded-2xl font-semibold text-white bg-indigo-600 hover:bg-indigo-500 border border-indigo-500/20 hover:border-indigo-500/40 shadow-lg shadow-indigo-650/15 hover:shadow-indigo-650/25 flex items-center justify-center gap-2 cursor-pointer transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {formLoading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Configuring limit...</span>
                </>
              ) : (
                <span>Set Budget Limit</span>
              )}
            </button>
          </form>
        </div>

        {/* Panel 2: Budgets Progress Display (2/3 width) */}
        <div className="lg:col-span-2 space-y-6">
          
          <div className="flex items-center justify-between">
            <div>
              <h3 className="text-lg font-bold text-white">Active Spending limits</h3>
              <p className="text-xs text-slate-400">Monthly budget thresholds mapped against active debits</p>
            </div>
            
            <div className="flex items-center gap-1.5 text-xs text-slate-500 font-mono">
              <Info className="w-4 h-4 text-indigo-400" />
              <span>Refreshes dynamically with logs</span>
            </div>
          </div>

          {/* Grid of progress items */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
            {CATEGORIES.map(cat => {
              const activeBudget = activeBudgetsMap[cat];
              const spent = categoryTotals[cat] || 0;

              if (activeBudget) {
                return (
                  <BudgetProgressBar
                    key={cat}
                    category={cat}
                    spent={spent}
                    limit={activeBudget.monthly_limit}
                    alertAt={activeBudget.alert_at_percent}
                  />
                );
              }

              // Display beautiful placeholder for unconfigured limits
              return (
                <div 
                  key={cat}
                  className="p-5 rounded-2xl border border-dashed border-slate-800 bg-slate-900/10 flex flex-col justify-between h-[156px] transition-all duration-300 hover:border-indigo-500/30 hover:bg-indigo-950/5 group"
                >
                  <div className="flex justify-between items-start">
                    <div>
                      <span className="text-sm font-medium text-slate-500 uppercase tracking-wider">{cat}</span>
                      <h4 className="text-xs text-slate-600 mt-1">No monthly limit set</h4>
                    </div>
                    <span className="text-xs font-mono font-bold text-slate-600">₹{spent.toLocaleString('en-IN')} spent</span>
                  </div>

                  <button
                    type="button"
                    onClick={() => {
                      setCategory(cat);
                      // Scroll to top on mobile to make input field visible
                      window.scrollTo({ top: 0, behavior: 'smooth' });
                    }}
                    className="flex items-center justify-center gap-1 py-2 px-3 rounded-xl border border-slate-800 text-[10px] font-bold text-slate-400 hover:text-white hover:bg-slate-800 hover:border-slate-700 transition-all cursor-pointer w-fit mt-3 group-hover:border-indigo-500/20 group-hover:text-indigo-400"
                  >
                    <PlusCircle className="w-3.5 h-3.5" />
                    <span>Setup Limit</span>
                  </button>
                </div>
              );
            })}
          </div>

        </div>

      </div>
    </div>
  );
};

export default Budget;
