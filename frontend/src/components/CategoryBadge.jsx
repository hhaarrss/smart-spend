import React from 'react';

/**
 * CategoryBadge UI component.
 * Renders color-coded semi-transparent badges based on category.
 * 
 * @param {string} category - Category string (e.g. 'Food').
 */
const CategoryBadge = ({ category }) => {
  const normalized = (category || 'Other').trim().toLowerCase();

  const styles = {
    food: 'bg-rose-500/10 text-rose-400 border border-rose-500/20',
    travel: 'bg-sky-500/10 text-sky-400 border border-sky-500/20',
    shopping: 'bg-amber-500/10 text-amber-400 border border-amber-500/20',
    utilities: 'bg-purple-500/10 text-purple-400 border border-purple-500/20',
    entertainment: 'bg-indigo-500/10 text-indigo-400 border border-indigo-500/20',
    healthcare: 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20',
    education: 'bg-teal-500/10 text-teal-400 border border-teal-500/20',
    fuel: 'bg-orange-500/10 text-orange-400 border border-orange-500/20',
    groceries: 'bg-lime-500/10 text-lime-400 border border-lime-500/20',
  };

  const defaultStyle = 'bg-slate-500/10 text-slate-400 border border-slate-500/20';
  const badgeStyle = styles[normalized] || defaultStyle;

  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold tracking-wide capitalize ${badgeStyle}`}>
      {category || 'Other'}
    </span>
  );
};

export default CategoryBadge;
