import React, { useState } from 'react';
import { ArrowUpRight, ArrowDownLeft, Calendar, Landmark, AlertOctagon, Edit3, Check, HelpCircle, ShieldCheck } from 'lucide-react';
import CategoryBadge from './CategoryBadge';

const CATEGORIES = [
  'Food & Dining',
  'Groceries',
  'Shopping',
  'Transportation',
  'Fuel',
  'Healthcare',
  'Entertainment',
  'Utilities & Bills',
  'Education',
  'Travel & Hotels',
  'Finance & Insurance',
  'Other'
];

/**
 * TransactionCard UI component matching light fintech aesthetic.
 * Supports inline re-categorization, confidence badges, and Needs Review flags.
 * 
 * @param {Object} tx - The transaction payload.
 * @param {Object|boolean} anomaly - Optional anomaly metadata.
 * @param {Function} onRecategorize - Callback when user corrects a category.
 */
const TransactionCard = ({ tx, anomaly, onRecategorize }) => {
  const [isEditing, setIsEditing] = useState(false);
  const [selectedCategory, setSelectedCategory] = useState(tx.category || 'Other');
  const [saving, setSaving] = useState(false);

  const isCredit = tx.type === 'credit';
  const amount = parseFloat(tx.amount) || 0;
  const isNeedsReview = tx.review_status === 'needs_review' || tx.category === 'Needs Review';

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

  const handleSave = async () => {
    if (!selectedCategory || selectedCategory === 'Needs Review') return;
    try {
      setSaving(true);
      if (onRecategorize) {
        await onRecategorize(tx.id, selectedCategory, tx.merchant);
      }
      setIsEditing(false);
    } catch (err) {
      console.error('Failed to recategorize transaction:', err);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className={`flex flex-col md:flex-row md:items-center justify-between p-4 rounded-2xl border ${
      isNeedsReview
        ? 'border-amber-300 bg-amber-50/40 shadow-xs'
        : anomaly 
        ? 'border-rose-300 bg-[#FFF5F5] shadow-xs' 
        : 'border-slate-200/90 bg-white hover:border-slate-300 hover:shadow-xs'
    } transition-all duration-200 gap-3`}>
      
      <div className="flex items-center gap-3.5 min-w-0 flex-1">
        {/* Direction Icon */}
        <div className={`p-2.5 rounded-xl flex-shrink-0 ${
          isCredit 
            ? 'bg-[#EAF7EF] text-[#16803C] border border-emerald-200/60' 
            : isNeedsReview
            ? 'bg-amber-100 text-amber-700 border border-amber-200'
            : anomaly 
            ? 'bg-rose-100 text-[#EF4444] border border-rose-200' 
            : 'bg-[#FFF0F0] text-[#EF4444] border border-rose-200/60'
        }`}>
          {isCredit ? <ArrowUpRight className="w-5 h-5" /> : <ArrowDownLeft className="w-5 h-5" />}
        </div>

        {/* Details */}
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <h4 className="font-bold text-slate-900 text-sm truncate">
              {tx.merchant || 'Unknown Merchant'}
            </h4>
            
            {/* Needs Review Badge */}
            {isNeedsReview && (
              <span className="inline-flex items-center gap-1 text-[10px] font-bold text-amber-700 bg-amber-100 px-2 py-0.5 rounded-full border border-amber-300">
                <HelpCircle className="w-3 h-3" />
                <span>Needs Review</span>
              </span>
            )}

            {/* Confidence Pill */}
            {tx.confidence && tx.confidence !== 'none' && (
              <span className={`inline-flex items-center gap-1 text-[10px] font-semibold px-2 py-0.5 rounded-full border capitalize ${
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

            {/* Anomaly Badge */}
            {anomaly && (
              <span className="inline-flex items-center gap-1 text-[10px] font-extrabold text-[#EF4444] bg-white px-2 py-0.5 rounded-full border border-rose-300 shadow-xs">
                <AlertOctagon className="w-3 h-3" />
                <span>Spike ({anomaly.avg ? `+${Math.round(((amount - anomaly.avg) / anomaly.avg) * 100)}%` : 'Anomaly'})</span>
              </span>
            )}
          </div>
          
          <div className="flex flex-wrap items-center gap-2 mt-1.5">
            {isEditing ? (
              <div className="flex items-center gap-2">
                <select
                  value={selectedCategory}
                  onChange={(e) => setSelectedCategory(e.target.value)}
                  className="text-xs border border-slate-300 rounded-lg px-2.5 py-1 bg-white font-medium focus:ring-2 focus:ring-emerald-500 focus:outline-none"
                >
                  {CATEGORIES.map((cat) => (
                    <option key={cat} value={cat}>{cat}</option>
                  ))}
                </select>
                
                <button
                  onClick={handleSave}
                  disabled={saving}
                  className="inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-1 bg-emerald-600 hover:bg-emerald-700 text-white rounded-lg transition-colors"
                >
                  <Check className="w-3 h-3" />
                  <span>{saving ? 'Saving...' : 'Save'}</span>
                </button>

                <button
                  onClick={() => setIsEditing(false)}
                  className="text-xs text-slate-500 hover:text-slate-700 px-1.5 py-1"
                >
                  Cancel
                </button>
              </div>
            ) : (
              <div className="flex items-center gap-1.5">
                <CategoryBadge category={tx.category} />
                
                {onRecategorize && (
                  <button
                    onClick={() => setIsEditing(true)}
                    className="p-1 text-slate-400 hover:text-slate-600 rounded-md hover:bg-slate-100 transition-colors"
                    title="Change Category"
                  >
                    <Edit3 className="w-3 h-3" />
                  </button>
                )}
              </div>
            )}
            
            {/* Bank Info */}
            {tx.bank && (
              <span className="inline-flex items-center gap-1 text-[10px] text-slate-500 bg-slate-100 px-2 py-0.5 rounded border border-slate-200 font-mono">
                <Landmark className="w-2.5 h-2.5" />
                {tx.bank} {tx.account_last4 ? `*${tx.account_last4}` : ''}
              </span>
            )}

            {tx.source && (
              <span className="inline-flex items-center gap-1 text-[10px] text-slate-500 bg-slate-50 px-2 py-0.5 rounded border border-slate-200/80 font-mono capitalize">
                {tx.source}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Amount & Time */}
      <div className="text-right flex-shrink-0 min-w-[120px]">
        <div className={`text-base font-black tracking-tight ${
          isCredit ? 'text-[#16803C]' : anomaly ? 'text-[#EF4444]' : 'text-slate-900'
        }`}>
          {isCredit ? '+' : '-'} ₹{amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </div>
        
        <div className="flex items-center justify-end gap-1 text-slate-500 text-[10px] mt-1 font-mono">
          <Calendar className="w-3 h-3 text-slate-400" />
          <span>{formatDate(tx.date)}</span>
        </div>
      </div>

    </div>
  );
};

export default TransactionCard;
