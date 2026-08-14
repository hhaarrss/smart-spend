import React from 'react';

/**
 * CategoryBadge UI component.
 * Renders color-coded semi-transparent badges based on category in light fintech palette.
 * 
 * @param {string} category - Category string (e.g. 'Food').
 */
const CategoryBadge = ({ category }) => {
  const normalized = (category || 'Other').trim().toLowerCase();

  const styles = {
    food: 'bg-emerald-50 text-emerald-800 border border-emerald-200/80',
    travel: 'bg-sky-50 text-sky-800 border border-sky-200/80',
    shopping: 'bg-purple-50 text-purple-800 border border-purple-200/80',
    utilities: 'bg-amber-50 text-amber-800 border border-amber-200/80',
    entertainment: 'bg-rose-50 text-rose-800 border border-rose-200/80',
    healthcare: 'bg-teal-50 text-teal-800 border border-teal-200/80',
    education: 'bg-blue-50 text-blue-800 border border-blue-200/80',
    fuel: 'bg-orange-50 text-orange-800 border border-orange-200/80',
    groceries: 'bg-lime-50 text-lime-800 border border-lime-200/80',
  };

  const defaultStyle = 'bg-slate-100 text-slate-700 border border-slate-200';
  const badgeStyle = styles[normalized] || defaultStyle;

  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-semibold tracking-wide capitalize ${badgeStyle}`}>
      {category || 'Other'}
    </span>
  );
};

export default CategoryBadge;
