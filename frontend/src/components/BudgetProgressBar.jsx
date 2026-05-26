import React from 'react';
import { AlertCircle, CheckCircle } from 'lucide-react';

/**
 * BudgetProgressBar UI component.
 * Renders spent progress relative to limits, with dynamic color alerts.
 * 
 * @param {string} category - Budget category name.
 * @param {number} spent - Amount already spent.
 * @param {number} limit - Total budget limit.
 * @param {number} alertAt - Threshold percentage that triggers budget warning alerts.
 */
const BudgetProgressBar = ({ category, spent = 0, limit = 0, alertAt = 80 }) => {
  const parsedSpent = parseFloat(spent) || 0;
  const parsedLimit = parseFloat(limit) || 1;
  const percent = (parsedSpent / parsedLimit) * 100;
  const displayPercent = Math.round(percent);

  // Dynamic colors based on budget utilization
  let progressColor = 'bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.3)]';
  let textColor = 'text-emerald-400';
  let bgGlow = 'bg-emerald-500/5';
  let borderStyle = 'border-emerald-500/20';

  if (percent >= 100) {
    progressColor = 'bg-rose-500 animate-pulse shadow-[0_0_12px_rgba(244,63,94,0.5)]';
    textColor = 'text-rose-400 font-bold';
    bgGlow = 'bg-rose-950/20';
    borderStyle = 'border-rose-500/40 border-2';
  } else if (percent >= alertAt) {
    progressColor = 'bg-amber-500 shadow-[0_0_8px_rgba(245,158,11,0.3)]';
    textColor = 'text-amber-400 font-semibold';
    bgGlow = 'bg-amber-500/5';
    borderStyle = 'border-amber-500/20';
  }

  return (
    <div className={`p-5 rounded-2xl border ${borderStyle} ${bgGlow} transition-all duration-300 hover:scale-[1.01] hover:shadow-xl bg-slate-800/40 backdrop-blur-md`}>
      <div className="flex justify-between items-center mb-3">
        <div>
          <span className="text-sm font-medium text-slate-400 uppercase tracking-wider">{category}</span>
          <div className="flex items-center gap-1.5 mt-1">
            <span className="text-xl font-bold text-white">₹{parsedSpent.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
            <span className="text-xs text-slate-500">/ ₹{parsedLimit.toLocaleString('en-IN', { maximumFractionDigits: 0 })} limit</span>
          </div>
        </div>
        
        <div className="text-right">
          <span className={`text-lg font-bold ${textColor}`}>{displayPercent}%</span>
          <p className="text-xs text-slate-500 mt-0.5">utilized</p>
        </div>
      </div>

      {/* Progress Bar Container */}
      <div className="h-2.5 w-full bg-slate-700/50 rounded-full overflow-hidden mb-3">
        <div 
          className={`h-full rounded-full transition-all duration-500 ease-out ${progressColor}`}
          style={{ width: `${Math.min(percent, 100)}%` }}
        />
      </div>

      {/* Message alerts */}
      <div className="flex items-center justify-between text-xs">
        {percent >= 100 ? (
          <div className="flex items-center gap-1 text-rose-400 font-medium">
            <AlertCircle className="w-3.5 h-3.5" />
            <span>Budget Exceeded! Reduced spending required.</span>
          </div>
        ) : percent >= alertAt ? (
          <div className="flex items-center gap-1 text-amber-400 font-medium">
            <AlertCircle className="w-3.5 h-3.5" />
            <span>Approaching Limit. Spend carefully.</span>
          </div>
        ) : (
          <div className="flex items-center gap-1 text-emerald-400">
            <CheckCircle className="w-3.5 h-3.5" />
            <span>Within budget constraints. Safe to spend.</span>
          </div>
        )}
      </div>
    </div>
  );
};

export default BudgetProgressBar;
