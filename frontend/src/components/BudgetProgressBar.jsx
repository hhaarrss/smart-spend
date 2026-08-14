import React, { useState } from 'react';
import { AlertCircle, CheckCircle, Edit2, ShieldCheck, Loader2, Save, X, PlusCircle } from 'lucide-react';

/**
 * BudgetProgressBar UI component with Inline Card Editing support.
 * 
 * @param {string} category - Budget category name.
 * @param {number} spent - Amount already spent.
 * @param {number} limit - Total budget limit (0 if unconfigured).
 * @param {number} alertAt - Threshold percentage that triggers warning alerts.
 * @param {boolean} isFamilyLimit - Whether limit is applied family-wide.
 * @param {function} onSaveBudget - Async callback to save/update budget limit inline.
 */
const BudgetProgressBar = ({ 
  category, 
  spent = 0, 
  limit = 0, 
  alertAt = 80, 
  isFamilyLimit = false,
  onSaveBudget 
}) => {
  const [isEditing, setIsEditing] = useState(false);
  const [editLimit, setEditLimit] = useState(limit || '');
  const [editAlertAt, setEditAlertAt] = useState(alertAt || 80);
  const [editIsFamily, setEditIsFamily] = useState(isFamilyLimit || false);
  const [saving, setSaving] = useState(false);
  const [errorMsg, setErrorMsg] = useState('');

  const isConfigured = limit > 0;
  const parsedSpent = parseFloat(spent) || 0;
  const parsedLimit = parseFloat(limit) || 1;
  const percent = isConfigured ? (parsedSpent / parsedLimit) * 100 : 0;
  const displayPercent = Math.round(percent);

  // Dynamic colors based on budget utilization
  let progressColor = 'bg-[#22A447]';
  let textColor = 'text-[#16803C]';
  let bgCard = 'bg-white';
  let borderStyle = 'border-slate-200';

  if (isConfigured) {
    if (percent >= 100) {
      progressColor = 'bg-[#EF4444]';
      textColor = 'text-[#EF4444] font-bold';
      bgCard = 'bg-[#FFF0F0]';
      borderStyle = 'border-rose-300';
    } else if (percent >= alertAt) {
      progressColor = 'bg-[#F59E0B]';
      textColor = 'text-[#F59E0B] font-semibold';
      bgCard = 'bg-[#FFF9EE]';
      borderStyle = 'border-amber-300';
    }
  }

  const handleSaveInline = async (e) => {
    e.preventDefault();
    if (!editLimit || parseFloat(editLimit) <= 0) {
      setErrorMsg('Enter a positive limit amount.');
      return;
    }

    setSaving(true);
    setErrorMsg('');
    try {
      if (onSaveBudget) {
        await onSaveBudget({
          category,
          monthly_limit: parseFloat(editLimit),
          alert_at_percent: parseFloat(editAlertAt),
          is_family_limit: editIsFamily
        });
      }
      setSaving(false);
      setIsEditing(false);
    } catch (err) {
      setSaving(false);
      setErrorMsg(err.message || 'Failed to update limit.');
    }
  };

  // Inline Form View
  if (isEditing) {
    return (
      <div className="p-5 rounded-2xl border border-[#16803C] bg-white shadow-md space-y-4">
        <div className="flex justify-between items-center pb-2 border-b border-slate-100">
          <span className="text-xs font-black text-slate-900 uppercase tracking-wider">Configure {category} Limit</span>
          <button 
            type="button" 
            onClick={() => setIsEditing(false)}
            className="text-slate-400 hover:text-slate-600 p-1"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {errorMsg && (
          <div className="p-2 bg-rose-50 border border-rose-200 text-rose-700 text-[11px] rounded-lg">
            {errorMsg}
          </div>
        )}

        <form onSubmit={handleSaveInline} className="space-y-4">
          <div>
            <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-wider mb-1">Monthly Limit (INR)</label>
            <input
              type="number"
              required
              value={editLimit}
              onChange={(e) => setEditLimit(e.target.value)}
              placeholder="e.g. 10000"
              className="w-full px-3 py-2 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 text-xs font-bold focus:outline-none focus:border-[#16803C]"
            />
          </div>

          <div>
            <div className="flex justify-between text-[10px] font-bold text-slate-500 uppercase mb-1">
              <span>Alert Threshold</span>
              <span className="text-[#16803C] font-black">{editAlertAt}%</span>
            </div>
            <input
              type="range"
              min="10"
              max="100"
              step="5"
              value={editAlertAt}
              onChange={(e) => setEditAlertAt(parseInt(e.target.value))}
              className="w-full h-1.5 bg-slate-200 rounded-lg appearance-none cursor-pointer accent-[#16803C]"
            />
          </div>

          <div className="flex items-center gap-2 pt-1">
            <input
              type="checkbox"
              id={`family-${category}`}
              checked={editIsFamily}
              onChange={(e) => setEditIsFamily(e.target.checked)}
              className="w-3.5 h-3.5 rounded text-[#16803C] border-slate-300 focus:ring-[#16803C]"
            />
            <label htmlFor={`family-${category}`} className="text-xs font-semibold text-slate-600 flex items-center gap-1 select-none">
              <ShieldCheck className="w-3.5 h-3.5 text-[#16803C]" />
              Family-wide limit
            </label>
          </div>

          <div className="flex items-center gap-2 pt-2">
            <button
              type="submit"
              disabled={saving}
              className="flex-1 py-2 px-3 rounded-xl bg-[#16803C] hover:bg-[#136e33] text-white font-bold text-xs shadow-xs flex items-center justify-center gap-1 cursor-pointer disabled:opacity-50"
            >
              {saving ? <Loader2 className="w-3.5 h-3.5 animate-spin" /> : <Save className="w-3.5 h-3.5" />}
              <span>Save Limit</span>
            </button>
            <button
              type="button"
              onClick={() => setIsEditing(false)}
              className="py-2 px-3 rounded-xl bg-slate-100 text-slate-700 font-bold text-xs hover:bg-slate-200 cursor-pointer"
            >
              Cancel
            </button>
          </div>
        </form>
      </div>
    );
  }

  // Unconfigured Category Card (Inviting Primary CTA)
  if (!isConfigured) {
    return (
      <div className="p-5 rounded-2xl border border-dashed border-slate-300 bg-white/80 flex flex-col justify-between h-[160px] transition-all hover:border-[#16803C] hover:shadow-xs group">
        <div className="flex justify-between items-start">
          <div>
            <span className="text-xs font-bold text-slate-500 uppercase tracking-wider">{category}</span>
            <h4 className="text-xs text-slate-400 font-medium mt-0.5">No monthly limit set</h4>
          </div>
          <span className="text-xs font-mono font-bold text-slate-500">₹{parsedSpent.toLocaleString('en-IN')} spent</span>
        </div>

        <button
          type="button"
          onClick={() => {
            setEditLimit(parsedSpent > 0 ? parsedSpent * 2 : 5000);
            setIsEditing(true);
          }}
          className="w-full py-2.5 px-3 rounded-xl bg-[#EAF7EF] border border-emerald-200 text-[#16803C] font-bold text-xs flex items-center justify-center gap-1.5 hover:bg-[#16803C] hover:text-white transition-all cursor-pointer shadow-xs"
        >
          <PlusCircle className="w-4 h-4" />
          <span>Setup Budget Limit</span>
        </button>
      </div>
    );
  }

  // Configured Active Budget Card
  return (
    <div className={`p-5 rounded-2xl border ${borderStyle} ${bgCard} shadow-xs transition-all duration-200 hover:shadow-md space-y-3 relative group`}>
      <div className="flex justify-between items-start">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xs font-extrabold text-slate-900 uppercase tracking-wider">{category}</span>
            {isFamilyLimit && (
              <span className="inline-flex items-center gap-0.5 text-[9px] font-bold text-[#16803C] bg-[#EAF7EF] px-1.5 py-0.5 rounded border border-emerald-200">
                <ShieldCheck className="w-3 h-3" />
                Family
              </span>
            )}
          </div>
          
          <div className="flex items-baseline gap-1.5 mt-1">
            <span className="text-xl font-black text-slate-900">
              ₹{parsedSpent.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </span>
            <span className="text-xs text-slate-500 font-medium">/ ₹{parsedLimit.toLocaleString('en-IN', { maximumFractionDigits: 0 })} limit</span>
          </div>
        </div>
        
        <div className="flex items-center gap-2">
          <div className="text-right">
            <span className={`text-lg font-black ${textColor}`}>{displayPercent}%</span>
            <p className="text-[10px] text-slate-500 uppercase tracking-wider font-bold">utilized</p>
          </div>

          {/* Quick Edit Trigger Button */}
          <button
            type="button"
            onClick={() => setIsEditing(true)}
            className="p-1.5 rounded-lg bg-slate-100 hover:bg-slate-200 text-slate-600 transition-all cursor-pointer opacity-80 group-hover:opacity-100 ml-1"
            title="Edit limit inline"
          >
            <Edit2 className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Progress Bar Container */}
      <div className="h-2.5 w-full bg-slate-100 rounded-full overflow-hidden">
        <div 
          className={`h-full rounded-full transition-all duration-500 ease-out ${progressColor}`}
          style={{ width: `${Math.min(percent, 100)}%` }}
        />
      </div>

      {/* Status Message */}
      <div className="flex items-center justify-between text-xs pt-1">
        {percent >= 100 ? (
          <div className="flex items-center gap-1.5 text-[#EF4444] font-bold">
            <AlertCircle className="w-3.5 h-3.5" />
            <span>Budget Exceeded! Reduced spending required.</span>
          </div>
        ) : percent >= alertAt ? (
          <div className="flex items-center gap-1.5 text-[#D97706] font-bold">
            <AlertCircle className="w-3.5 h-3.5" />
            <span>Approaching Limit. Spend carefully.</span>
          </div>
        ) : (
          <div className="flex items-center gap-1.5 text-[#16803C] font-semibold">
            <CheckCircle className="w-3.5 h-3.5" />
            <span>Within budget constraints. Safe to spend.</span>
          </div>
        )}
      </div>
    </div>
  );
};

export default BudgetProgressBar;
