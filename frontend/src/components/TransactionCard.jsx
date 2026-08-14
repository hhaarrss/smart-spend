import React from 'react';
import { ArrowUpRight, ArrowDownLeft, Calendar, Landmark, AlertOctagon } from 'lucide-react';
import CategoryBadge from './CategoryBadge';

/**
 * TransactionCard UI component matching light fintech aesthetic.
 * Supports inline anomaly/spike tags when flagged by AI insights engine.
 * 
 * @param {Object} tx - The transaction database payload.
 * @param {Object|boolean} anomaly - Optional anomaly metadata if flagged.
 */
const TransactionCard = ({ tx, anomaly }) => {
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
    <div className={`flex items-center justify-between p-4 rounded-2xl border ${
      anomaly 
        ? 'border-rose-300 bg-[#FFF5F5] shadow-xs' 
        : 'border-slate-200/90 bg-white hover:border-slate-300 hover:shadow-xs'
    } transition-all duration-200`}>
      <div className="flex items-center gap-3.5 min-w-0">
        {/* Transaction Flow Direction Icon */}
        <div className={`p-2.5 rounded-xl flex-shrink-0 ${
          isCredit 
            ? 'bg-[#EAF7EF] text-[#16803C] border border-emerald-200/60' 
            : anomaly 
            ? 'bg-rose-100 text-[#EF4444] border border-rose-200' 
            : 'bg-[#FFF0F0] text-[#EF4444] border border-rose-200/60'
        }`}>
          {isCredit ? <ArrowUpRight className="w-5 h-5" /> : <ArrowDownLeft className="w-5 h-5" />}
        </div>

        {/* Merchant & Context Info */}
        <div className="min-w-0">
          <div className="flex items-center gap-2">
            <h4 className="font-bold text-slate-900 text-sm truncate">
              {tx.merchant || 'Unknown Merchant'}
            </h4>
            
            {/* Inline Anomaly / Spike Tag */}
            {anomaly && (
              <span className="inline-flex items-center gap-1 text-[10px] font-extrabold text-[#EF4444] bg-white px-2 py-0.5 rounded-full border border-rose-300 shadow-xs">
                <AlertOctagon className="w-3 h-3" />
                <span>Spike ({anomaly.avg ? `+${Math.round(((amount - anomaly.avg) / anomaly.avg) * 100)}%` : 'Anomaly'})</span>
              </span>
            )}
          </div>
          
          <div className="flex flex-wrap items-center gap-2 mt-1">
            <CategoryBadge category={tx.category} />
            
            {/* Source / Bank Details */}
            {tx.bank && (
              <span className="inline-flex items-center gap-1 text-[10px] text-slate-500 bg-slate-100 px-2 py-0.5 rounded border border-slate-200 font-mono">
                <Landmark className="w-2.5 h-2.5" />
                {tx.bank} {tx.account_last4 ? `*${tx.account_last4}` : ''}
              </span>
            )}
            
            {!tx.bank && (
              <span className="inline-flex items-center gap-1 text-[10px] text-slate-500 bg-slate-100 px-2 py-0.5 rounded border border-slate-200 font-mono capitalize">
                {tx.source}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Amount & Time metadata */}
      <div className="text-right flex-shrink-0 ml-3">
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
