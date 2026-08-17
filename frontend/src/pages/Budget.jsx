import React, { useState, useEffect } from 'react';
import { budgetService, transactionService, categoryService } from '../services/api';
import { 
  Wallet, PlusCircle, AlertCircle, CheckCircle2, 
  Loader2, Info, X, ShieldCheck
} from 'lucide-react';
import BudgetProgressBar from '../components/BudgetProgressBar';

/**
 * Budget Limit page featuring full inline-editable cards (no redundant form panel).
 */
const Budget = () => {
  const [budgets, setBudgets] = useState([]);
  const [categoryTotals, setCategoryTotals] = useState({});
  const [loading, setLoading] = useState(true);
  const [errorMsg, setErrorMsg] = useState('');
  const [categories, setCategories] = useState([
    'Food & Dining', 'Groceries', 'Shopping', 'Transportation', 'Telecom & Recharge',
    'Utilities & Bills', 'Fuel', 'Healthcare', 'Entertainment', 'Subscriptions',
    'Education', 'Travel & Hotels', 'Finance & Insurance', 'Personal Care', 'Other'
  ]);

  // Modal State for creating a new custom category budget
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [newCat, setNewCat] = useState('Food & Dining');
  const [newLimit, setNewLimit] = useState('');
  const [newAlertAt, setNewAlertAt] = useState(80);
  const [newIsFamily, setNewIsFamily] = useState(false);
  const [modalLoading, setModalLoading] = useState(false);

  useEffect(() => {
    loadBudgetData();
  }, []);

  const loadBudgetData = async () => {
    setLoading(true);
    setErrorMsg('');
    try {
      const now = new Date();
      const yyyy = now.getFullYear();
      const firstDay = new Date(yyyy, now.getMonth(), 1).toISOString();
      const lastDay = new Date(yyyy, now.getMonth() + 1, 0, 23, 59, 59, 999).toISOString();

      const [budgetLimits, txs, fetchedCategories] = await Promise.all([
        budgetService.getBudgets(),
        transactionService.listTransactions({ start_date: firstDay, end_date: lastDay }),
        categoryService.getCategories().catch(() => null)
      ]);

      if (fetchedCategories && Array.isArray(fetchedCategories)) {
        setCategories(fetchedCategories);
      }

      setBudgets(budgetLimits);

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

  // Inline Card Save Callback
  const handleSaveBudgetInline = async (budgetData) => {
    await budgetService.setBudget(budgetData);
    await loadBudgetData();
  };

  // Modal Submit for New Category
  const handleModalSubmit = async (e) => {
    e.preventDefault();
    if (!newLimit || parseFloat(newLimit) <= 0) {
      return;
    }

    setModalLoading(true);
    try {
      await budgetService.setBudget({
        category: newCat,
        monthly_limit: parseFloat(newLimit),
        alert_at_percent: parseFloat(newAlertAt),
        is_family_limit: newIsFamily
      });

      setNewLimit('');
      setModalLoading(false);
      setIsModalOpen(false);
      loadBudgetData();
    } catch (err) {
      setModalLoading(false);
      setErrorMsg(err.response?.data?.detail || 'Failed to save budget settings.');
    }
  };

  if (loading) {
    return (
      <div className="py-20 flex justify-center items-center">
        <div className="flex flex-col items-center gap-3">
          <Loader2 className="w-8 h-8 text-[#16803C] animate-spin" />
          <span className="text-xs font-semibold text-slate-500">Loading budget trackers...</span>
        </div>
      </div>
    );
  }

  const activeBudgetsMap = {};
  budgets.forEach(b => {
    activeBudgetsMap[b.category] = b;
  });

  return (
    <div className="space-y-8 font-sans">
      
      {/* Title & Action Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tight flex items-center gap-2.5">
            <Wallet className="w-7 h-7 text-[#16803C]" />
            Monthly Budget Limits
          </h1>
          <p className="text-xs sm:text-sm text-slate-500 font-medium mt-1">
            Inline-editable category constraints. Click any card to adjust limits directly.
          </p>
        </div>

        {/* Modal Trigger for New Category */}
        <button
          onClick={() => setIsModalOpen(true)}
          className="flex items-center gap-2 px-4 py-2.5 rounded-xl font-bold text-white bg-[#16803C] hover:bg-[#136e33] shadow-xs transition-all cursor-pointer text-xs"
        >
          <PlusCircle className="w-4 h-4" />
          <span>Create New Category Budget</span>
        </button>
      </div>

      {errorMsg && (
        <div className="flex items-center gap-2 p-4 bg-rose-50 border border-rose-200 rounded-2xl text-xs font-semibold text-rose-700">
          <AlertCircle className="w-4 h-4 flex-shrink-0" />
          <span>{errorMsg}</span>
        </div>
      )}

      {/* Main Full-Width Grid of Inline Editable Cards */}
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h3 className="text-base font-extrabold text-slate-900">Category Budget Cards</h3>
          <div className="flex items-center gap-1.5 text-xs text-slate-500 font-mono">
            <Info className="w-4 h-4 text-[#16803C]" />
            <span>Click any card to inline-edit threshold or limit</span>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {Array.from(new Set([...categories, ...budgets.map(b => b.category), ...Object.keys(categoryTotals)])).map(cat => {
            const activeBudget = activeBudgetsMap[cat];
            const spent = categoryTotals[cat] || 0;

            return (
              <BudgetProgressBar
                key={cat}
                category={cat}
                spent={spent}
                limit={activeBudget?.monthly_limit || 0}
                alertAt={activeBudget?.alert_at_percent || 80}
                isFamilyLimit={activeBudget?.is_family_limit || false}
                onSaveBudget={handleSaveBudgetInline}
              />
            );
          })}
        </div>
      </div>

      {/* Modal for Creating New Custom Category Budget */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex justify-center items-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="bg-white border border-slate-200 rounded-2xl max-w-md w-full p-6 shadow-xl space-y-5 animate-in fade-in zoom-in duration-150">
            <div className="flex justify-between items-center pb-3 border-b border-slate-100">
              <h3 className="text-base font-extrabold text-slate-900">Create Category Budget</h3>
              <button 
                onClick={() => setIsModalOpen(false)}
                className="text-slate-400 hover:text-slate-600 p-1"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleModalSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1.5">Category</label>
                <select
                  value={newCat}
                  onChange={(e) => setNewCat(e.target.value)}
                  className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 text-xs font-semibold focus:outline-none focus:border-[#16803C]"
                >
                  {categories.map(c => (
                    <option key={c} value={c}>{c}</option>
                  ))}
                </select>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-1.5">Monthly Limit (INR)</label>
                <input
                  type="number"
                  required
                  value={newLimit}
                  onChange={(e) => setNewLimit(e.target.value)}
                  placeholder="e.g. 10000"
                  className="w-full px-3.5 py-2.5 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 text-xs font-bold focus:outline-none focus:border-[#16803C]"
                />
              </div>

              <div>
                <div className="flex justify-between text-xs font-bold text-slate-600 uppercase mb-1">
                  <span>Alert Threshold</span>
                  <span className="text-[#16803C] font-black">{newAlertAt}%</span>
                </div>
                <input
                  type="range"
                  min="10"
                  max="100"
                  step="5"
                  value={newAlertAt}
                  onChange={(e) => setNewAlertAt(parseInt(e.target.value))}
                  className="w-full h-1.5 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-[#16803C]"
                />
              </div>

              <div className="flex items-center gap-2 pt-1">
                <input
                  type="checkbox"
                  id="modalFamily"
                  checked={newIsFamily}
                  onChange={(e) => setNewIsFamily(e.target.checked)}
                  className="w-4 h-4 rounded text-[#16803C] border-slate-300 focus:ring-[#16803C]"
                />
                <label htmlFor="modalFamily" className="text-xs font-semibold text-slate-700 flex items-center gap-1">
                  <ShieldCheck className="w-3.5 h-3.5 text-[#16803C]" />
                  Apply as Family-wide budget
                </label>
              </div>

              <div className="flex items-center gap-2 pt-3">
                <button
                  type="submit"
                  disabled={modalLoading}
                  className="flex-1 py-3 px-4 rounded-xl bg-[#16803C] hover:bg-[#136e33] text-white font-bold text-xs shadow-xs flex items-center justify-center gap-2 cursor-pointer"
                >
                  {modalLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <span>Save Budget</span>}
                </button>
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="py-3 px-4 rounded-xl bg-slate-100 text-slate-700 font-bold text-xs hover:bg-slate-200 cursor-pointer"
                >
                  Cancel
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

    </div>
  );
};

export default Budget;
