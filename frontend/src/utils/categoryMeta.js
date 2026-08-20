import {
  Utensils, ShoppingCart, ShoppingBag, Car, Smartphone,
  Zap, Fuel, HeartPulse, Film, Repeat, GraduationCap,
  Plane, Landmark, Sparkles, HelpCircle, Layers, Wallet
} from 'lucide-react';

const DEFAULT_META = {
  icon: Layers,
  color: '#94A3B8',
  bg: 'bg-slate-50',
  text: 'text-slate-700',
};

const CATEGORY_META = {
  'food & dining': { icon: Utensils, color: '#16803C', bg: 'bg-emerald-50', text: 'text-emerald-800' },
  food: { icon: Utensils, color: '#16803C', bg: 'bg-emerald-50', text: 'text-emerald-800' },
  groceries: { icon: ShoppingCart, color: '#84CC16', bg: 'bg-lime-50', text: 'text-lime-800' },
  shopping: { icon: ShoppingBag, color: '#22A447', bg: 'bg-purple-50', text: 'text-purple-800' },
  transportation: { icon: Car, color: '#7655B8', bg: 'bg-violet-50', text: 'text-violet-800' },
  'telecom & recharge': { icon: Smartphone, color: '#3B82F6', bg: 'bg-blue-50', text: 'text-blue-800' },
  'utilities & bills': { icon: Zap, color: '#F59E0B', bg: 'bg-amber-50', text: 'text-amber-800' },
  utilities: { icon: Zap, color: '#F59E0B', bg: 'bg-amber-50', text: 'text-amber-800' },
  fuel: { icon: Fuel, color: '#F97316', bg: 'bg-orange-50', text: 'text-orange-800' },
  healthcare: { icon: HeartPulse, color: '#0EA5E9', bg: 'bg-teal-50', text: 'text-teal-800' },
  entertainment: { icon: Film, color: '#EF4444', bg: 'bg-rose-50', text: 'text-rose-800' },
  subscriptions: { icon: Repeat, color: '#6366F1', bg: 'bg-indigo-50', text: 'text-indigo-800' },
  education: { icon: GraduationCap, color: '#2563EB', bg: 'bg-blue-50', text: 'text-blue-800' },
  'travel & hotels': { icon: Plane, color: '#0EA5E9', bg: 'bg-sky-50', text: 'text-sky-800' },
  travel: { icon: Plane, color: '#0EA5E9', bg: 'bg-sky-50', text: 'text-sky-800' },
  'finance & insurance': { icon: Landmark, color: '#475569', bg: 'bg-slate-100', text: 'text-slate-800' },
  'personal care': { icon: Sparkles, color: '#EC4899', bg: 'bg-pink-50', text: 'text-pink-800' },
  'needs review': { icon: HelpCircle, color: '#D97706', bg: 'bg-amber-50', text: 'text-amber-800' },
  other: { icon: Wallet, color: '#94A3B8', bg: 'bg-slate-50', text: 'text-slate-700' },
};

export function getCategoryMeta(category) {
  const key = (category || 'Other').trim().toLowerCase();
  return CATEGORY_META[key] || DEFAULT_META;
}

export function budgetUsageTone(percent) {
  if (percent > 90) return { bar: 'bg-rose-500', text: 'text-rose-700', track: 'bg-rose-100' };
  if (percent >= 60) return { bar: 'bg-amber-500', text: 'text-amber-700', track: 'bg-amber-100' };
  return { bar: 'bg-emerald-500', text: 'text-emerald-700', track: 'bg-emerald-100' };
}
