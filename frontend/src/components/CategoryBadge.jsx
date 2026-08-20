import React from 'react';
import { getCategoryMeta } from '../utils/categoryMeta';

/**
 * CategoryBadge UI component.
 * Renders color-coded semi-transparent badges based on category in light fintech palette.
 * 
 * @param {string} category - Category string (e.g. 'Food').
 */
const CategoryBadge = ({ category }) => {
  const meta = getCategoryMeta(category);
  const Icon = meta.icon;

  return (
    <span className={`inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-semibold tracking-wide capitalize border ${meta.bg} ${meta.text}`}>
      <Icon className="w-3 h-3" />
      {category || 'Other'}
    </span>
  );
};

export default CategoryBadge;
