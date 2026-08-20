import React from 'react';
import { getCategoryMeta, budgetUsageTone } from '../utils/categoryMeta';

/**
 * Grid of category spend cards sorted by total spent (highest first).
 */
const CategorySpendCards = ({ categories = [], onSelectCategory, selectedCategory }) => {
  if (!categories.length) {
    return (
      <div className="text-center py-10 text-slate-400 border border-dashed border-slate-200 rounded-xl bg-slate-50 text-xs">
        No category spend for this month.
      </div>
    );
  }

  return (
    <div className="grid grid-cols-1 sm:grid-cols-2 xl:grid-cols-3 gap-4">
      {categories.map((cat) => {
        const meta = getCategoryMeta(cat.category);
        const Icon = meta.icon;
        const hasBudget = Number(cat.budget_limit) > 0;
        const usedPct = hasBudget ? Number(cat.budget_used_percent) || 0 : 0;
        const tone = budgetUsageTone(usedPct);
        const isSelected = selectedCategory && selectedCategory.toLowerCase() === cat.category.toLowerCase();

        return (
          <button
            key={cat.category}
            type="button"
            onClick={() => onSelectCategory?.(isSelected ? null : cat.category)}
            className={`text-left p-4 rounded-2xl border bg-white shadow-xs transition-all cursor-pointer ${
              isSelected ? 'border-[#16803C] ring-2 ring-emerald-100' : 'border-slate-200 hover:border-slate-300'
            }`}
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-2.5 min-w-0">
                <div className={`p-2 rounded-xl ${meta.bg} ${meta.text}`}>
                  <Icon className="w-4 h-4" />
                </div>
                <div className="min-w-0">
                  <h4 className="text-sm font-extrabold text-slate-900 truncate capitalize">{cat.category}</h4>
                  <p className="text-[11px] text-slate-500 font-medium">
                    {cat.transaction_count} transaction{cat.transaction_count === 1 ? '' : 's'}
                  </p>
                </div>
              </div>
              <div className="text-right flex-shrink-0">
                <div className="text-sm font-black text-slate-900">
                  ₹{Number(cat.total).toLocaleString('en-IN', { minimumFractionDigits: 2 })}
                </div>
                <div className="text-[10px] text-slate-500 font-bold">{cat.percentage}% of spend</div>
              </div>
            </div>

            <div className="mt-3">
              <div className="flex items-center justify-between mb-1">
                <span className={`text-[10px] font-bold ${hasBudget ? tone.text : 'text-slate-400'}`}>
                  {hasBudget ? `${usedPct.toFixed(0)}% of budget` : 'No budget set'}
                </span>
                {hasBudget && (
                  <span className="text-[10px] text-slate-400 font-medium">
                    ₹{Number(cat.budget_limit).toLocaleString('en-IN')}
                  </span>
                )}
              </div>
              <div className={`h-2 rounded-full overflow-hidden ${hasBudget ? tone.track : 'bg-slate-100'}`}>
                <div
                  className={`h-full rounded-full transition-all duration-500 ${hasBudget ? tone.bar : 'bg-slate-300'}`}
                  style={{ width: `${Math.min(hasBudget ? usedPct : 0, 100)}%` }}
                />
              </div>
            </div>
          </button>
        );
      })}
    </div>
  );
};

export default CategorySpendCards;
