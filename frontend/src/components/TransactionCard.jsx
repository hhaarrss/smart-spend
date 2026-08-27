import React, { useState, useEffect } from 'react';
import { ArrowUpRight, ArrowDownLeft, Calendar, Landmark, AlertOctagon, Edit3, Check, HelpCircle, ShieldCheck, Trash2, Edit, ArrowLeftRight, User } from 'lucide-react';
import CategoryBadge from './CategoryBadge';
import { categoryService, transactionService } from '../services/api';
import { isNewTransaction } from '../utils/transactions';
import { getCategoryMeta } from '../utils/categoryMeta';

const DEFAULT_CATEGORIES = [
  'Food', 'Transport', 'Shopping', 'Entertainment', 'Utilities',
  'Healthcare', 'Education', 'Travel', 'Rent', 'Transfer',
  'Investment', 'Salary', 'Refund', 'Other'
];

/**
 * TransactionCard UI component matching light fintech aesthetic.
 * Supports inline re-categorization, editing merchant/amount/date/notes, hard delete, and P2P transfer styling.
 */
const TransactionCard = ({ tx, anomaly, onRecategorize, onUpdate, onDelete }) => {
  const [isEditing, setIsEditing] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);
  const [categories, setCategories] = useState(DEFAULT_CATEGORIES);

  // Edit form states
  const [editMerchant, setEditMerchant] = useState(tx.merchant || '');
  const [editCategory, setEditCategory] = useState(tx.category || 'Other');
  const [editAmount, setEditAmount] = useState(tx.amount || 0);
  const [editDate, setEditDate] = useState(tx.date ? new Date(tx.date).toISOString().slice(0, 10) : '');
  const [editNotes, setEditNotes] = useState(tx.notes || '');
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [isQuickCategorizing, setIsQuickCategorizing] = useState(false);

  const handleQuickCategorySelect = async (newCategory) => {
    setSaving(true);
    try {
      await transactionService.categorizeTransaction(tx.id, newCategory, tx.merchant);
      if (onUpdate) {
        await onUpdate(tx.id, { category: newCategory });
      } else if (onRecategorize) {
        await onRecategorize(tx.id, newCategory);
      }
      setIsQuickCategorizing(false);
    } catch (err) {
      console.error('Failed to quick categorize:', err);
    } finally {
      setSaving(false);
    }
  };

  useEffect(() => {
    categoryService.getCategories()
      .then(res => {
        const cats = res?.categories || res;
        if (Array.isArray(cats)) setCategories(cats);
      })
      .catch(() => {});
  }, []);

  useEffect(() => {
    setEditMerchant(tx.merchant || '');
    setEditCategory(tx.category || 'Other');
    setEditAmount(tx.amount || 0);
    setEditDate(tx.date ? new Date(tx.date).toISOString().slice(0, 10) : '');
    setEditNotes(tx.notes || '');
  }, [tx]);

  const isCredit = tx.type === 'credit';
  const isTransfer = tx.is_transfer || tx.category === 'Transfer';
  const amount = parseFloat(tx.amount) || 0;
  const isNeedsReview = tx.review_status === 'needs_review' || tx.category === 'Needs Review';
  const showNewBadge = isNewTransaction(tx);
  const categoryMeta = getCategoryMeta(tx.category);
  const CategoryIcon = categoryMeta.icon;

  const formatDate = (dateStr) => {
    try {
      const date = new Date(dateStr);
      return date.toLocaleDateString('en-IN', {
        day: 'numeric',
        month: 'short',
        year: 'numeric'
      });
    } catch {
      return dateStr;
    }
  };

  const handleFullSave = async (e) => {
    if (e) e.preventDefault();
    setSaving(true);
    try {
      if (onUpdate) {
        await onUpdate(tx.id, {
          merchant: editMerchant,
          category: editCategory,
          amount: parseFloat(editAmount),
          date: new Date(editDate).toISOString(),
          notes: editNotes
        });
      }
      setIsEditing(false);
    } catch (err) {
      console.error('Error saving edit:', err);
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async () => {
    setDeleting(true);
    try {
      if (onDelete) {
        await onDelete(tx.id);
      }
    } catch (err) {
      console.error('Error deleting:', err);
    } finally {
      setDeleting(false);
      setShowDeleteConfirm(false);
    }
  };

  return (
    <div className={`flex flex-col p-4 rounded-2xl border ${
      isTransfer
        ? 'border-slate-300 bg-slate-50/70 hover:bg-slate-50 shadow-xs'
        : isNeedsReview
        ? 'border-amber-300 bg-amber-50/40 shadow-xs'
        : anomaly 
        ? 'border-rose-300 bg-[#FFF5F5] shadow-xs' 
        : 'border-slate-200/90 bg-white hover:border-slate-300 hover:shadow-xs'
    } transition-all duration-200 gap-3`}>
      
      {isEditing ? (
        /* Inline Edit Form */
        <form onSubmit={handleFullSave} className="space-y-3 bg-slate-50 p-3 rounded-xl border border-slate-200">
          <div className="flex items-center justify-between border-b border-slate-200 pb-2">
            <h4 className="text-xs font-bold text-slate-800 uppercase tracking-wider">Edit Transaction</h4>
            <span className="text-[10px] text-slate-400 font-mono">ID: #{tx.id}</span>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-2.5">
            <div>
              <label className="block text-[11px] font-semibold text-slate-600 mb-1">Merchant / Payee</label>
              <input
                type="text"
                value={editMerchant}
                onChange={(e) => setEditMerchant(e.target.value)}
                className="w-full text-xs border border-slate-300 rounded-lg px-2.5 py-1.5 bg-white font-medium focus:ring-2 focus:ring-emerald-500 focus:outline-none"
                placeholder="e.g. Swiggy"
                required
              />
            </div>

            <div>
              <label className="block text-[11px] font-semibold text-slate-600 mb-1">Category</label>
              <select
                value={editCategory}
                onChange={(e) => setEditCategory(e.target.value)}
                className="w-full text-xs border border-slate-300 rounded-lg px-2.5 py-1.5 bg-white font-medium focus:ring-2 focus:ring-emerald-500 focus:outline-none"
              >
                {categories.map((cat) => (
                  <option key={cat} value={cat}>{cat}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-[11px] font-semibold text-slate-600 mb-1">Amount (₹)</label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                value={editAmount}
                onChange={(e) => setEditAmount(e.target.value)}
                className="w-full text-xs border border-slate-300 rounded-lg px-2.5 py-1.5 bg-white font-medium focus:ring-2 focus:ring-emerald-500 focus:outline-none"
                required
              />
            </div>

            <div>
              <label className="block text-[11px] font-semibold text-slate-600 mb-1">Date</label>
              <input
                type="date"
                value={editDate}
                onChange={(e) => setEditDate(e.target.value)}
                className="w-full text-xs border border-slate-300 rounded-lg px-2.5 py-1.5 bg-white font-medium focus:ring-2 focus:ring-emerald-500 focus:outline-none"
                required
              />
            </div>
          </div>

          <div>
            <label className="block text-[11px] font-semibold text-slate-600 mb-1">Notes (Optional)</label>
            <input
              type="text"
              value={editNotes}
              onChange={(e) => setEditNotes(e.target.value)}
              className="w-full text-xs border border-slate-300 rounded-lg px-2.5 py-1.5 bg-white font-medium focus:ring-2 focus:ring-emerald-500 focus:outline-none"
              placeholder="e.g. Dinner split with friends"
            />
          </div>

          <div className="flex items-center justify-end gap-2 pt-1">
            <button
              type="button"
              onClick={() => setIsEditing(false)}
              className="text-xs font-semibold text-slate-600 hover:text-slate-800 px-3 py-1.5 rounded-lg border border-slate-300 bg-white cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={saving}
              className="text-xs font-semibold text-white bg-emerald-700 hover:bg-emerald-800 px-3 py-1.5 rounded-lg flex items-center gap-1 cursor-pointer"
            >
              <Check className="w-3.5 h-3.5" />
              <span>{saving ? 'Saving...' : 'Save Changes'}</span>
            </button>
          </div>
        </form>
      ) : (
        /* Standard View Mode */
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-3">
          <div className="flex items-center gap-3.5 min-w-0 flex-1">
            {/* Direction / Transfer Icon */}
            <div className={`p-2.5 rounded-xl flex-shrink-0 ${
              isTransfer
                ? 'bg-slate-200 text-slate-700 border border-slate-300'
                : isCredit 
                ? 'bg-[#EAF7EF] text-[#16803C] border border-emerald-200/60' 
                : isNeedsReview
                ? 'bg-amber-100 text-amber-700 border border-amber-200'
                : anomaly 
                ? 'bg-rose-100 text-[#EF4444] border border-rose-200' 
                : 'bg-[#FFF0F0] text-[#EF4444] border border-rose-200/60'
            }`}>
              {isTransfer ? <ArrowLeftRight className="w-5 h-5" /> : isCredit ? <ArrowUpRight className="w-5 h-5" /> : <ArrowDownLeft className="w-5 h-5" />}
            </div>

            {/* Details */}
            <div className="min-w-0 flex-1">
              <div className="flex flex-wrap items-center gap-2">
                <span className={`p-1 rounded-md ${categoryMeta.bg} ${categoryMeta.text}`} title={tx.category}>
                  <CategoryIcon className="w-3.5 h-3.5" />
                </span>
                <h4 className="font-bold text-slate-900 text-sm truncate">
                  {tx.transfer_to ? `Transfer to ${tx.transfer_to}` : (tx.merchant || 'Unknown Merchant')}
                </h4>

                {showNewBadge && (
                  <span className="inline-flex items-center text-[9px] font-extrabold tracking-wider text-emerald-700 bg-emerald-50 px-1.5 py-0.5 rounded-full border border-emerald-200">
                    NEW
                  </span>
                )}
                
                {/* Needs Review Badge */}
                {isNeedsReview && (
                  <button 
                    type="button"
                    onClick={() => setIsQuickCategorizing(!isQuickCategorizing)}
                    title="Click to quick categorize"
                    className="inline-flex items-center gap-1 text-[10px] font-bold text-amber-700 bg-amber-100 px-2 py-0.5 rounded-full border border-amber-300 hover:bg-amber-200 transition-colors cursor-pointer"
                  >
                    <HelpCircle className="w-3 h-3" />
                    <span>Needs Review</span>
                  </button>
                )}

                {/* Confidence Pill */}
                {tx.confidence && tx.confidence !== 'none' && (
                  <span 
                    title={`AI Categorization Accuracy Confidence: ${tx.confidence.toUpperCase()}`}
                    className={`inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full border capitalize cursor-help ${
                    tx.confidence === 'high'
                      ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                      : tx.confidence === 'medium'
                      ? 'bg-blue-50 text-blue-700 border-blue-200'
                      : 'bg-slate-100 text-slate-600 border-slate-200'
                  }`}>
                    <ShieldCheck className="w-2.5 h-2.5" />
                    <span>{tx.confidence}</span>
                  </span>
                )}
              </div>
              
              <div className="flex flex-wrap items-center gap-2 mt-1.5">
                <button
                  type="button"
                  onClick={() => setIsQuickCategorizing(!isQuickCategorizing)}
                  title="Click to change category"
                  className="cursor-pointer hover:opacity-80 transition-opacity"
                >
                  <CategoryBadge category={tx.category} />
                </button>

                <button
                  type="button"
                  onClick={() => setIsQuickCategorizing(!isQuickCategorizing)}
                  className="text-[10px] text-indigo-600 font-bold hover:underline cursor-pointer"
                >
                  Change Category
                </button>
                
                {/* Notes string if available */}
                {tx.notes && (
                  <span className="text-[11px] text-slate-500 italic bg-slate-50 px-2 py-0.5 rounded border border-slate-200">
                    "{tx.notes}"
                  </span>
                )}

                {/* Bank Info */}
                {tx.bank && (
                  <span className="inline-flex items-center gap-1 text-[10px] text-slate-500 bg-slate-100 px-2 py-0.5 rounded border border-slate-200 font-mono">
                    <Landmark className="w-2.5 h-2.5" />
                    {tx.bank} {tx.account_last4 ? `*${tx.account_last4}` : ''}
                  </span>
                )}
              </div>

              {/* Quick Categorize Panel */}
              {isQuickCategorizing && (
                <div className="mt-3 p-3 bg-slate-100/90 rounded-xl border border-slate-200 animate-in fade-in duration-150">
                  <div className="flex items-center justify-between mb-2">
                    <span className="text-xs font-bold text-slate-700">Select New Category:</span>
                    <button
                      type="button"
                      onClick={() => setIsQuickCategorizing(false)}
                      className="text-xs text-slate-400 hover:text-slate-600 font-semibold"
                    >
                      Cancel
                    </button>
                  </div>
                  <div className="flex flex-wrap gap-1.5">
                    {categories.map((cat) => (
                      <button
                        key={cat}
                        type="button"
                        disabled={saving}
                        onClick={() => handleQuickCategorySelect(cat)}
                        className={`px-2.5 py-1 text-xs font-semibold rounded-lg border transition-all cursor-pointer ${
                          tx.category === cat
                            ? 'bg-emerald-600 text-white border-emerald-600 shadow-xs'
                            : 'bg-white text-slate-700 border-slate-200 hover:bg-emerald-50 hover:border-emerald-300 hover:text-emerald-700'
                        }`}
                      >
                        {cat}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Amount, Time & Action Icons */}
          <div className="flex items-center justify-between md:justify-end gap-4 min-w-[150px]">
            <div className="text-right">
              <div className={`text-base font-black tracking-tight ${
                isCredit ? 'text-[#16803C]' : 'text-[#EF4444]'
              }`}>
                {isCredit ? '+' : '-'} ₹{amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </div>
              
              <div className="flex items-center justify-end gap-1 text-slate-500 text-[10px] mt-1 font-mono">
                <Calendar className="w-3 h-3 text-slate-400" />
                <span>{formatDate(tx.date)}</span>
              </div>
            </div>

            {/* Action Buttons (Edit Pencil & Delete Trash) */}
            <div className="flex items-center gap-1 border-l border-slate-200 pl-3">
              <button
                type="button"
                onClick={() => setIsEditing(true)}
                className="p-1.5 text-slate-400 hover:text-indigo-600 rounded-lg hover:bg-indigo-50 transition-colors cursor-pointer"
                title="Edit Transaction"
              >
                <Edit className="w-4 h-4" />
              </button>

              <button
                type="button"
                onClick={() => setShowDeleteConfirm(true)}
                className="p-1.5 text-slate-400 hover:text-rose-600 rounded-lg hover:bg-rose-50 transition-colors cursor-pointer"
                title="Delete Transaction"
              >
                <Trash2 className="w-4 h-4" />
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Delete Confirmation Dialog */}
      {showDeleteConfirm && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/40 backdrop-blur-xs p-4">
          <div className="bg-white rounded-2xl p-5 max-w-sm w-full shadow-xl border border-slate-100 space-y-4">
            <div className="flex items-center gap-3 text-rose-600">
              <div className="p-2 bg-rose-100 rounded-xl">
                <Trash2 className="w-5 h-5" />
              </div>
              <h3 className="font-extrabold text-slate-900 text-base">Delete Transaction?</h3>
            </div>
            <p className="text-xs text-slate-600 leading-relaxed">
              Are you sure you want to permanently delete this transaction ({tx.merchant || 'Unknown Merchant'} for ₹{amount})? This action cannot be undone.
            </p>
            <div className="flex items-center justify-end gap-2 pt-2">
              <button
                type="button"
                onClick={() => setShowDeleteConfirm(false)}
                className="text-xs font-semibold text-slate-600 bg-slate-100 hover:bg-slate-200 px-3.5 py-2 rounded-xl transition-colors cursor-pointer"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={handleDelete}
                disabled={deleting}
                className="text-xs font-semibold text-white bg-rose-600 hover:bg-rose-700 px-3.5 py-2 rounded-xl transition-colors cursor-pointer"
              >
                {deleting ? 'Deleting...' : 'Delete Permanently'}
              </button>
            </div>
          </div>
        </div>
      )}

    </div>
  );
};

export default TransactionCard;
