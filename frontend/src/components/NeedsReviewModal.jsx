import React, { useState, useEffect } from 'react';
import { X, Sparkles, CheckCircle2, ArrowRight } from 'lucide-react';
import { categoryService, transactionService } from '../services/api';

const DEFAULT_CATEGORIES = [
  'Food', 'Transport', 'Shopping', 'Entertainment', 'Utilities',
  'Healthcare', 'Education', 'Travel', 'Rent', 'Transfer',
  'Investment', 'Salary', 'Refund', 'Other'
];

export default function NeedsReviewModal({ isOpen, onClose, transactions, onFinished }) {
  const [categories, setCategories] = useState(DEFAULT_CATEGORIES);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [loading, setLoading] = useState(false);
  const [reviewedCount, setReviewedCount] = useState(0);
  const [learnedCount, setLearnedCount] = useState(0);

  useEffect(() => {
    categoryService.getCategories()
      .then(res => {
        const cats = res?.categories || res;
        if (Array.isArray(cats)) setCategories(cats);
      })
      .catch(() => {});
  }, []);

  if (!isOpen || !transactions || transactions.length === 0) return null;

  const currentTx = transactions[currentIndex];

  const handleSelectCategory = async (selectedCategory) => {
    if (!currentTx || loading) return;
    setLoading(true);
    try {
      const res = await transactionService.categorizeTransaction(
        currentTx.id,
        selectedCategory,
        currentTx.merchant
      );

      setReviewedCount((prev) => prev + 1);
      if (res.learned) setLearnedCount((prev) => prev + 1);

      if (currentIndex + 1 < transactions.length) {
        setCurrentIndex((prev) => prev + 1);
      } else {
        // Finished all
        onFinished();
      }
    } catch (err) {
      console.error('Categorize error:', err);
    } finally {
      setLoading(false);
    }
  };

  const isComplete = currentIndex >= transactions.length;
  const progressPercent = Math.min(100, Math.round(((currentIndex) / transactions.length) * 100));

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4">
      <div className="bg-white rounded-2xl shadow-2xl max-w-lg w-full overflow-hidden border border-slate-100 animate-in fade-in zoom-in-95 duration-200">
        
        {/* Modal Header */}
        <div className="bg-gradient-to-r from-emerald-600 to-teal-600 p-5 text-white flex justify-between items-center">
          <div className="flex items-center gap-2">
            <Sparkles className="w-5 h-5 text-amber-300" />
            <h3 className="font-semibold text-lg">Daily Review Ritual</h3>
          </div>
          <button
            onClick={onClose}
            className="p-1 rounded-lg text-white/80 hover:text-white hover:bg-white/10 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Progress Bar */}
        <div className="w-full bg-slate-100 h-1.5">
          <div
            className="bg-emerald-500 h-1.5 transition-all duration-300"
            style={{ width: `${progressPercent}%` }}
          />
        </div>

        {/* Modal Content */}
        <div className="p-6">
          {!isComplete && currentTx ? (
            <div>
              <div className="flex justify-between items-center mb-4 text-xs font-semibold uppercase tracking-wider text-slate-400">
                <span>Transaction {currentIndex + 1} of {transactions.length}</span>
                <span>{progressPercent}% Complete</span>
              </div>

              {/* Transaction Card */}
              <div className="bg-slate-50 border border-slate-200/80 rounded-xl p-4 mb-6 shadow-sm">
                <div className="flex justify-between items-start mb-2">
                  <div>
                    <h4 className="font-bold text-slate-800 text-lg">
                      {currentTx.merchant || 'Unknown Merchant'}
                    </h4>
                    <p className="text-xs text-slate-500">
                      {new Date(currentTx.date).toLocaleDateString('en-IN', {
                        day: 'numeric',
                        month: 'short',
                        year: 'numeric'
                      })} • {currentTx.bank || 'Bank Transfer'}
                    </p>
                  </div>
                  <span className="text-xl font-bold text-rose-600">
                    ₹{parseFloat(currentTx.amount).toLocaleString('en-IN')}
                  </span>
                </div>
                <div className="mt-2 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-md bg-amber-50 text-amber-700 text-xs font-medium border border-amber-200/60">
                  <span>Current: </span>
                  <span className="font-semibold">{currentTx.category || 'Other'}</span>
                </div>
              </div>

              <p className="text-xs font-medium text-slate-600 mb-3">
                Tap correct category below (SmartSpend learns automatically 🧠):
              </p>

              {/* Category Chips Grid */}
              <div className="grid grid-cols-3 gap-2 max-h-48 overflow-y-auto pr-1">
                {categories.map((cat) => (
                  <button
                    key={cat}
                    disabled={loading}
                    onClick={() => handleSelectCategory(cat)}
                    className="px-3 py-2 text-xs font-semibold text-slate-700 bg-white border border-slate-200 rounded-lg hover:bg-emerald-50 hover:border-emerald-300 hover:text-emerald-700 transition-all text-center truncate shadow-sm active:scale-95 disabled:opacity-50"
                  >
                    {cat}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            /* Completion Screen */
            <div className="text-center py-6">
              <div className="w-16 h-16 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto mb-4">
                <CheckCircle2 className="w-10 h-10" />
              </div>
              <h4 className="text-xl font-bold text-slate-800 mb-2">
                All done for today! 🎉
              </h4>
              <p className="text-sm text-slate-600 mb-6">
                You reviewed {reviewedCount} transactions. SmartSpend learned {learnedCount} new merchant rule{learnedCount !== 1 ? 's' : ''}!
              </p>
              <button
                onClick={onFinished}
                className="inline-flex items-center gap-2 px-6 py-2.5 bg-emerald-600 text-white font-semibold rounded-xl hover:bg-emerald-700 transition-all shadow-md"
              >
                <span>Continue to Dashboard</span>
                <ArrowRight className="w-4 h-4" />
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
