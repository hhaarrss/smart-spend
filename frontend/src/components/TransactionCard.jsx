import React from 'react';
import { ArrowUpRight, ArrowDownLeft, Calendar, Landmark, HelpCircle } from 'lucide-react';
import CategoryBadge from './CategoryBadge';

/**
 * TransactionCard UI component.
 * Renders a single financial transaction list item beautifully.
 * 
 * @param {Object} tx - The transaction database payload.
 */
const TransactionCard = ({ tx }) => {
  const isCredit = tx.type === 'credit';
  const amount = parseFloat(tx.amount) || 0;
  
  // Format Date beautifully
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

  return (
    <div className="flex items-center justify-between p-4 rounded-xl border border-slate-800 bg-slate-900/60 backdrop-blur-sm transition-all duration-200 hover:border-slate-700 hover:bg-slate-800/40 hover:translate-x-1">
      <div className="flex items-center gap-3.5 min-w-0">
        {/* Transaction Flow Direction Icon */}
        <div className={`p-2.5 rounded-lg flex-shrink-0 ${
          isCredit 
            ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20' 
            : 'bg-rose-500/10 text-rose-400 border border-rose-500/20'
        }`}>
          {isCredit ? <ArrowUpRight className="w-5 h-5" /> : <ArrowDownLeft className="w-5 h-5" />}
        </div>

        {/* Merchant & Context Info */}
        <div className="min-w-0">
          <h4 className="font-semibold text-white text-sm truncate pr-2">
            {tx.merchant || 'Unknown Merchant'}
          </h4>
          
          <div className="flex flex-wrap items-center gap-2 mt-1">
            <CategoryBadge category={tx.category} />
            
            {/* Source / Bank Details */}
            {tx.bank && (
              <span className="inline-flex items-center gap-1 text-[10px] text-slate-500 bg-slate-800 px-2 py-0.5 rounded border border-slate-700/55 font-mono">
                <Landmark className="w-2.5 h-2.5" />
                {tx.bank} {tx.account_last4 ? `*${tx.account_last4}` : ''}
              </span>
            )}
            
            {!tx.bank && (
              <span className="inline-flex items-center gap-1 text-[10px] text-slate-500 bg-slate-800 px-2 py-0.5 rounded border border-slate-700/55 font-mono capitalize">
                {tx.source}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Amount & Time metadata */}
      <div className="text-right flex-shrink-0 ml-3">
        <div className={`text-base font-bold tracking-tight ${isCredit ? 'text-emerald-400' : 'text-slate-200'}`}>
          {isCredit ? '+' : '-'} ₹{amount.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
        </div>
        
        <div className="flex items-center justify-end gap-1 text-slate-500 text-[10px] mt-1 font-mono">
          <Calendar className="w-3 h-3 text-slate-600" />
          <span>{formatDate(tx.date)}</span>
        </div>
      </div>
    </div>
  );
};

export default TransactionCard;
