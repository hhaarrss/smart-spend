import React, { useState, useEffect, useRef } from 'react';
import { transactionService, budgetService, insightService } from '../services/api';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, LabelList
} from 'recharts';
import {
  AlertTriangle, ShoppingBag, Wallet, Activity,
  TrendingUp, ArrowUpRight, ArrowDownRight, X, HelpCircle,
  Search, Download, Layers, ChevronDown, Calendar, Sparkles
} from 'lucide-react';
import TransactionCard from '../components/TransactionCard';
import MonthPicker from '../components/MonthPicker';
import NeedsReviewModal from '../components/NeedsReviewModal';
import { fetchAllMonthTransactions, sortTransactionsLatestFirst } from '../utils/transactions';

const PAGE_SIZE = 10;

/**
 * Main dashboard page delivering 3-second clarity:
 * - Top stat strip: Outflow (Expenses), Inflow (Income), Net Savings Cash Flow, & Total Logs
 * - Suppressed divide-by-zero MoM percentage artifacts ("N/A First Month Tracked")
 * - Interactive Daily Spending Curve with month/year picker
 * - Category breakdown donut chart synced with month/year selection
 * - Highlighted "Today" bar in daily spending chart with accent color
 * - Paginated transactions with Show More and auto-collapse on scroll-up
 */
const Dashboard = () => {
  const now = new Date();

  const [transactions, setTransactions] = useState([]);
  const [prevTransactions, setPrevTransactions] = useState([]);
  const [budgets, setBudgets] = useState([]);
  const [insights, setInsights] = useState(null);

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [selectedCategory, setSelectedCategory] = useState(null);
  const [selectedDay, setSelectedDay] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [activeTab, setActiveTab] = useState('all');
  const [visibleCount, setVisibleCount] = useState(PAGE_SIZE);

  const [selectedMonth, setSelectedMonth] = useState(now.getMonth() + 1);
  const [selectedYear, setSelectedYear] = useState(now.getFullYear());
  const [includeTransfers, setIncludeTransfers] = useState(false);

  const [isReviewModalOpen, setIsReviewModalOpen] = useState(false);
  const [needsReviewItems, setNeedsReviewItems] = useState([]);

  const openNeedsReviewModal = async () => {
    try {
      const res = await transactionService.getNeedsReviewTransactions();
      setNeedsReviewItems(res.transactions || []);
      setIsReviewModalOpen(true);
    } catch (err) {
      console.error('[Dashboard] Error opening needs review modal:', err);
    }
  };

  const selectedMonthRef = useRef(selectedMonth);
  const selectedYearRef = useRef(selectedYear);
  selectedMonthRef.current = selectedMonth;
  selectedYearRef.current = selectedYear;

  const txSentinelRef = useRef(null);
  const hasLeftTxTop = useRef(false);

  const monthName = new Date(selectedYear, selectedMonth - 1, 1).toLocaleString('default', { month: 'long' });
  const monthYearLabel = `${monthName} ${selectedYear}`;
  const prevMonthDate = new Date(selectedYear, selectedMonth - 2, 1);
  const prevMonthName = prevMonthDate.toLocaleString('default', { month: 'long' });

  useEffect(() => {
    loadDashboardData(selectedMonth, selectedYear);

    const intervalId = setInterval(() => {
      loadDashboardData(selectedMonthRef.current, selectedYearRef.current, false);
    }, 10000);

    return () => clearInterval(intervalId);
  }, [selectedMonth, selectedYear]);

  useEffect(() => {
    setVisibleCount(PAGE_SIZE);
    hasLeftTxTop.current = false;
  }, [selectedMonth, selectedYear, selectedCategory, selectedDay, searchQuery, activeTab]);

  useEffect(() => {
    const sentinel = txSentinelRef.current;
    if (!sentinel) return;

    const observer = new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) {
          hasLeftTxTop.current = true;
          return;
        }
        if (hasLeftTxTop.current && visibleCount > PAGE_SIZE) {
          setVisibleCount(PAGE_SIZE);
          hasLeftTxTop.current = false;
        }
      },
      { threshold: 0, rootMargin: '0px' }
    );

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [visibleCount, loading]);

  const loadDashboardData = async (month, year, showLoading = true) => {
    if (showLoading) setLoading(true);
    setError('');
    try {
      const prevMonth = month === 1 ? 12 : month - 1;
      const prevYear = month === 1 ? year - 1 : year;

      const prevTxPromise = prevYear < 2020
        ? Promise.resolve([])
        : fetchAllMonthTransactions(transactionService.listTransactions, prevMonth, prevYear).catch(() => []);

      const [txs, prevTxs, budgetLimitsRaw, insightsData] = await Promise.all([
        fetchAllMonthTransactions(transactionService.listTransactions, month, year),
        prevTxPromise,
        budgetService.getBudgets(),
        insightService.getSummary().catch(() => null),
      ]);

      const budgetLimits = Array.isArray(budgetLimitsRaw) ? budgetLimitsRaw : (budgetLimitsRaw?.data || []);

      setTransactions(sortTransactionsLatestFirst(txs));
      setPrevTransactions(sortTransactionsLatestFirst(prevTxs));
      setBudgets(budgetLimits);
      setInsights(insightsData);

      if (showLoading) setLoading(false);
    } catch (err) {
      console.error('[Dashboard] Error loading data:', err);
      if (showLoading) setLoading(false);
      setError(`Dashboard load error: ${err.response?.data?.detail || err.message || 'Check backend connection.'}`);
    }
  };

  const handleMonthChange = (month, year) => {
    setSelectedMonth(month);
    setSelectedYear(year);
    setSelectedDay(null);
    setSelectedCategory(null);
  };

  const handleRecategorize = async (transactionId, newCategory, merchantRaw) => {
    setTransactions(prev => prev.map(t =>
      t.id === transactionId
        ? { ...t, category: newCategory, review_status: 'reviewed', source: 'user_correction', confidence: 'high' }
        : t
    ));
    try {
      await transactionService.recategorizeTransaction(transactionId, newCategory, null, merchantRaw);
      await loadDashboardData(selectedMonth, selectedYear, false);
    } catch (err) {
      console.error('[Dashboard] Error recategorizing transaction:', err);
      await loadDashboardData(selectedMonth, selectedYear, false);
    }
  };

  const handleUpdateTransaction = async (transactionId, updates) => {
    try {
      const updatedTx = await transactionService.updateTransaction(transactionId, updates);
      setTransactions(prev => prev.map(t => t.id === transactionId ? { ...t, ...updatedTx } : t));
      await loadDashboardData(selectedMonth, selectedYear, false);
    } catch (err) {
      console.error('[Dashboard] Error updating transaction:', err);
    }
  };

  const handleDeleteTransaction = async (transactionId) => {
    try {
      await transactionService.deleteTransaction(transactionId);
      setTransactions(prev => prev.filter(t => t.id !== transactionId));
      await loadDashboardData(selectedMonth, selectedYear, false);
    } catch (err) {
      console.error('[Dashboard] Error deleting transaction:', err);
    }
  };

  const exportToCSV = () => {
    if (!transactions || transactions.length === 0) return;

    const headers = ['ID', 'Date', 'Type', 'Category', 'Merchant', 'Amount (INR)', 'Bank', 'Account Last 4', 'Source', 'Review Status'];

    const sanitize = (val) => {
      if (val === null || val === undefined) return '""';
      const str = String(val).trim();
      if (/^[=+\-@\t\r]/.test(str)) {
        return `"'${str.replace(/"/g, '""')}"`;
      }
      return `"${str.replace(/"/g, '""')}"`;
    };

    const csvRows = sortTransactionsLatestFirst(transactions).map(t => [
      t.id,
      sanitize(t.date),
      sanitize(t.type),
      sanitize(t.category),
      sanitize(t.merchant || 'Unknown'),
      t.amount,
      sanitize(t.bank || ''),
      sanitize(t.account_last4 || ''),
      sanitize(t.source || 'manual'),
      sanitize(t.review_status || 'reviewed')
    ]);

    const csvString = [headers.join(','), ...csvRows.map(r => r.join(','))].join('\n');
    const blob = new Blob([csvString], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', `SmartSpend_Export_${selectedYear}-${String(selectedMonth).padStart(2, '0')}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const needsReviewTx = transactions.filter(t => t.review_status === 'needs_review' || t.category === 'Needs Review');
  const needsReviewCount = needsReviewTx.length;

  const debits = transactions.filter(t => t.type === 'debit');
  const credits = transactions.filter(t => t.type === 'credit');

  const merchantDebits = debits.filter(t => !t.is_transfer && t.category !== 'Transfer');
  const transferDebits = debits.filter(t => t.is_transfer || t.category === 'Transfer');

  const merchantSpent = merchantDebits.reduce((acc, t) => acc + parseFloat(t.amount), 0);
  const transferSent = transferDebits.reduce((acc, t) => acc + parseFloat(t.amount), 0);
  const totalSpent = includeTransfers ? (merchantSpent + transferSent) : merchantSpent;

  const totalIncome = credits.reduce((acc, t) => acc + parseFloat(t.amount), 0);
  const netCashFlow = totalIncome - totalSpent;
  const totalTxCount = transactions.length;

  const prevDebits = prevTransactions.filter(t => t.type === 'debit');
  const prevTotalSpent = prevDebits.reduce((acc, t) => acc + parseFloat(t.amount), 0);

  const rawMomDelta = prevTotalSpent > 0 ? ((totalSpent - prevTotalSpent) / prevTotalSpent) * 100 : null;
  const hasPrevData = rawMomDelta !== null;
  const spentDeltaPercent = hasPrevData ? rawMomDelta.toFixed(1) : null;
  const isSpentIncrease = totalSpent >= prevTotalSpent;

  const chartDebits = includeTransfers ? debits : merchantDebits;

  const categoryTotals = {};
  chartDebits.forEach(t => {
    categoryTotals[t.category] = (categoryTotals[t.category] || 0) + parseFloat(t.amount);
  });

  const FINTECH_COLORS = {
    'food & dining': '#16803C',
    'food': '#16803C',
    'groceries': '#84CC16',
    'shopping': '#22A447',
    'transportation': '#7655B8',
    'transport': '#7655B8',
    'utilities': '#F59E0B',
    'entertainment': '#EF4444',
    'healthcare': '#0EA5E9',
    'transfer': '#64748B',
    'other': '#94A3B8'
  };

  const pieData = Object.entries(categoryTotals).map(([name, value]) => ({
    name,
    value,
    color: FINTECH_COLORS[name.trim().toLowerCase()] || FINTECH_COLORS.other
  })).sort((a, b) => b.value - a.value);

  const anomalies = insights?.anomalies || [];
  const anomalyMap = {};
  const anomalyDaysSet = new Set();

  anomalies.forEach(a => {
    if (a.merchant) {
      anomalyMap[a.merchant.toLowerCase()] = a;
    }
    if (a.date) {
      try {
        const d = new Date(a.date);
        if (d.getMonth() + 1 === selectedMonth && d.getFullYear() === selectedYear) {
          anomalyDaysSet.add(d.getDate());
        }
      } catch {}
    }
  });

  const daysInMonth = new Date(selectedYear, selectedMonth, 0).getDate();
  const dailyTotals = {};
  for (let i = 1; i <= daysInMonth; i++) {
    dailyTotals[String(i).padStart(2, '0')] = 0;
  }

  debits.forEach(t => {
    try {
      const day = String(new Date(t.date).getDate()).padStart(2, '0');
      if (dailyTotals[day] !== undefined) {
        dailyTotals[day] += parseFloat(t.amount);
      }
    } catch { }
  });

  const isCurrentMonthYear = selectedMonth === (now.getMonth() + 1) && selectedYear === now.getFullYear();
  const todayDay = isCurrentMonthYear ? now.getDate() : null;

  let peakDay = 0;
  let peakAmount = 0;

  const barData = Object.entries(dailyTotals).map(([day, amount]) => {
    const dayNum = parseInt(day);
    const isToday = isCurrentMonthYear && dayNum === todayDay;
    if (amount > peakAmount) {
      peakAmount = amount;
      peakDay = dayNum;
    }
    return {
      day: dayNum,
      amount: Math.round(amount),
      isSpike: anomalyDaysSet.has(dayNum),
      isToday,
      todayLabel: isToday ? 'Today' : ''
    };
  }).sort((a, b) => a.day - b.day);

  let filteredTransactions = transactions;

  if (activeTab === 'needs_review') {
    filteredTransactions = filteredTransactions.filter(t => t.review_status === 'needs_review' || t.category === 'Needs Review');
  } else if (activeTab === 'debit') {
    filteredTransactions = filteredTransactions.filter(t => t.type === 'debit');
  } else if (activeTab === 'credit') {
    filteredTransactions = filteredTransactions.filter(t => t.type === 'credit');
  }

  if (selectedCategory) {
    filteredTransactions = filteredTransactions.filter(t => t.category.toLowerCase() === selectedCategory.toLowerCase());
  }

  if (selectedDay !== null) {
    filteredTransactions = filteredTransactions.filter(t => {
      try {
        return new Date(t.date).getDate() === selectedDay;
      } catch {
        return false;
      }
    });
  }

  if (searchQuery.trim()) {
    const query = searchQuery.toLowerCase();
    filteredTransactions = filteredTransactions.filter(t => {
      const merchantMatch = t.merchant && t.merchant.toLowerCase().includes(query);
      const categoryMatch = t.category && t.category.toLowerCase().includes(query);
      const bankMatch = t.bank && t.bank.toLowerCase().includes(query);
      const amountMatch = String(t.amount).includes(query);
      return merchantMatch || categoryMatch || bankMatch || amountMatch;
    });
  }

  const sortedFiltered = sortTransactionsLatestFirst(filteredTransactions);
  const visibleTransactions = sortedFiltered.slice(0, visibleCount);
  const remainingCount = Math.max(sortedFiltered.length - visibleCount, 0);

  const categoryRows = [...(categorySummary?.categories || [])].sort((a, b) => b.total - a.total);

  if (loading) {
    return (
      <div className="py-20 flex justify-center items-center">
        <div className="flex flex-col items-center gap-3">
          <Activity className="w-8 h-8 text-[#16803C] animate-spin" />
          <span className="text-xs font-semibold text-slate-500">Loading SmartSpend Engine...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8 font-sans">
      {error && (
        <div className="p-4 rounded-2xl bg-rose-50 border border-rose-200 text-rose-700 text-xs font-semibold flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {needsReviewCount > 0 && (
        <div className="p-4 rounded-2xl bg-gradient-to-r from-amber-500 to-amber-600 text-white flex flex-wrap items-center justify-between gap-3 shadow-md">
          <div className="flex items-center gap-3">
            <div className="p-2 bg-white/20 rounded-xl">
              <HelpCircle className="w-6 h-6 text-white" />
            </div>
            <div>
              <h4 className="font-extrabold text-sm tracking-tight">Needs Review: {needsReviewCount} Transactions Pending Classification</h4>
              <p className="text-xs text-amber-100 mt-0.5">Low-confidence bank SMS matches require your categorization to train future matching.</p>
            </div>
          </div>

          <button
            onClick={openNeedsReviewModal}
            className="px-4 py-2 bg-white text-amber-800 hover:bg-amber-50 rounded-xl font-bold text-xs shadow-xs transition-colors cursor-pointer inline-flex items-center gap-1.5"
          >
            <Sparkles className="w-3.5 h-3.5 text-amber-600" />
            <span>Review Pending Now ({needsReviewCount}) →</span>
          </button>
        </div>
      )}

      {/* Control Header & P2P Toggle */}
      <div className="flex flex-wrap items-center justify-between gap-3 bg-white p-3.5 rounded-2xl border border-slate-200 shadow-xs">
        <div className="flex items-center gap-2">
          <MonthPicker
            selectedMonth={selectedMonth}
            selectedYear={selectedYear}
            onMonthChange={handleMonthChange}
          />
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={openNeedsReviewModal}
            className="inline-flex items-center gap-1.5 px-3.5 py-2 rounded-xl font-bold text-xs bg-amber-50 text-amber-800 border border-amber-300 hover:bg-amber-100 transition-all cursor-pointer shadow-xs"
          >
            <Sparkles className="w-3.5 h-3.5 text-amber-600" />
            <span>Needs Review {needsReviewCount > 0 ? `(${needsReviewCount})` : ''}</span>
          </button>

          <button
            type="button"
            onClick={() => setIncludeTransfers(!includeTransfers)}
            className={`inline-flex items-center gap-2 px-3.5 py-2 rounded-xl font-bold text-xs border transition-all cursor-pointer ${
              includeTransfers
                ? 'bg-slate-900 text-white border-slate-900 shadow-xs'
                : 'bg-slate-100 text-slate-700 border-slate-200 hover:bg-slate-200'
            }`}
          >
            <span>Include Transfers</span>
            <span className={`w-2.5 h-2.5 rounded-full ${includeTransfers ? 'bg-emerald-400' : 'bg-slate-400'}`}></span>
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        <div className="bg-white border border-slate-200 p-5 rounded-2xl shadow-xs relative overflow-hidden flex flex-col justify-between">
          <div className="flex justify-between items-start">
            <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Total Outflow (Expenses)</span>
            <div className="p-2 bg-[#FFF0F0] text-[#EF4444] rounded-xl border border-rose-200">
              <ShoppingBag className="w-4 h-4" />
            </div>
          </div>

          <div className="mt-3">
            <div className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tight">
              ₹{totalSpent.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </div>

            <div className="mt-2 space-y-1 text-[11px]">
              <div className="flex justify-between font-semibold text-slate-600">
                <span>Spent on merchants:</span>
                <span className="text-slate-900 font-bold">₹{merchantSpent.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
              </div>
              <div className="flex justify-between font-medium text-slate-500">
                <span>Transferred to people:</span>
                <span className="text-slate-700 font-bold">₹{transferSent.toLocaleString('en-IN', { minimumFractionDigits: 2 })}</span>
              </div>
            </div>
          </div>
        </div>

        <div className="bg-white border border-slate-200 p-5 rounded-2xl shadow-xs relative overflow-hidden flex flex-col justify-between">
          <div className="flex justify-between items-start">
            <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Total Inflow (Income)</span>
            <div className="p-2 bg-[#EAF7EF] text-[#16803C] rounded-xl border border-emerald-200">
              <ArrowDownRight className="w-4 h-4" />
            </div>
          </div>

          <div className="mt-3">
            <div className="text-2xl sm:text-3xl font-black text-[#16803C] tracking-tight">
              ₹{totalIncome.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </div>
            <p className="text-[10px] text-slate-500 font-medium mt-2">Credits & Refunds for {monthName}</p>
          </div>
        </div>

        <div className="bg-white border border-slate-200 p-5 rounded-2xl shadow-xs relative overflow-hidden flex flex-col justify-between">
          <div className="flex justify-between items-start">
            <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Net Savings Flow</span>
            <div className={`p-2 rounded-xl border ${
              netCashFlow >= 0 ? 'bg-[#EAF7EF] text-[#16803C] border-emerald-200' : 'bg-rose-100 text-rose-600 border-rose-200'
            }`}>
              <Wallet className="w-4 h-4" />
            </div>
          </div>

          <div className="mt-3">
            <div className={`text-2xl sm:text-3xl font-black tracking-tight ${
              netCashFlow >= 0 ? 'text-slate-900' : 'text-rose-600'
            }`}>
              {netCashFlow >= 0 ? '+' : ''}₹{netCashFlow.toLocaleString('en-IN', { minimumFractionDigits: 2 })}
            </div>
            <p className="text-[10px] text-slate-500 font-medium mt-2">Income minus Outflow balance</p>
          </div>
        </div>

        <div className="bg-white border border-slate-200 p-5 rounded-2xl shadow-xs relative overflow-hidden flex flex-col justify-between">
          <div className="flex justify-between items-start">
            <span className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Transaction Logged</span>
            <div className="p-2 bg-indigo-50 text-indigo-600 rounded-xl border border-indigo-200">
              <Activity className="w-4 h-4" />
            </div>
          </div>

          <div className="mt-3">
            <div className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tight">
              {totalTxCount} <span className="text-xs text-slate-500 font-normal">items</span>
            </div>

            <div className="flex items-center gap-1.5 mt-2">
              <span className="text-[10px] text-slate-500 font-medium font-mono">
                {needsReviewCount > 0 ? `⚠️ ${needsReviewCount} Needs Review` : '✅ 100% Ingested'}
              </span>
            </div>
          </div>
        </div>
      </div>

      {/* Shared Header & Month Picker for Synced Charts */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 p-5 bg-white border border-slate-200 rounded-2xl shadow-xs">
        <div className="flex items-center gap-3">
          <div className="p-2.5 bg-[#EAF7EF] text-[#16803C] rounded-xl border border-emerald-200">
            <Calendar className="w-5 h-5" />
          </div>
          <div>
            <h2 className="text-base sm:text-lg font-black text-slate-900 tracking-tight">
              Monthly Analytics — <span className="text-[#16803C]">{monthYearLabel}</span>
            </h2>
            <p className="text-xs text-slate-500 font-medium mt-0.5">
              Daily spending curve and category share synchronized for {monthYearLabel}
            </p>
          </div>
        </div>

        <MonthPicker month={selectedMonth} year={selectedYear} onChange={handleMonthChange} />
      </div>

      {/* Synced Charts Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Chart 1: Daily Spending Bar Chart */}
        <div className="lg:col-span-2 bg-white border border-slate-200 p-6 rounded-2xl shadow-xs space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
            <div>
              <h3 className="text-base font-extrabold text-slate-900 flex items-center gap-2">
                <TrendingUp className="w-5 h-5 text-[#16803C]" />
                Daily Spending ({monthYearLabel})
              </h3>
              <p className="text-xs text-slate-500 font-medium mt-0.5">Click any day bar to drill down into that day's exact transactions</p>
            </div>

            {peakAmount > 0 && (
              <span className="inline-flex items-center gap-1 text-[11px] font-bold text-slate-700 bg-slate-100 px-3 py-1 rounded-full border border-slate-200">
                Peak Day: {monthName.slice(0, 3)} {peakDay} (₹{peakAmount.toLocaleString('en-IN')})
              </span>
            )}
          </div>

          <div className="h-64 w-full pt-4">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart
                data={barData}
                margin={{ top: 20, right: 10, left: -20, bottom: 0 }}
                onClick={(e) => {
                  if (e && e.activePayload && e.activePayload.length > 0) {
                    const dayClicked = e.activePayload[0].payload.day;
                    setSelectedDay(selectedDay === dayClicked ? null : dayClicked);
                  }
                }}
              >
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E2E8F0" />
                <XAxis dataKey="day" tick={{ fontSize: 10, fill: '#64748B' }} axisLine={false} tickLine={false} />
                <YAxis tick={{ fontSize: 10, fill: '#64748B' }} axisLine={false} tickLine={false} />
                <Tooltip
                  content={({ active, payload }) => {
                    if (active && payload && payload.length) {
                      const data = payload[0].payload;
                      return (
                        <div className="bg-slate-900 text-white p-2.5 rounded-xl text-xs font-sans shadow-lg border border-slate-700">
                          <div className="font-bold text-slate-300">
                            {monthName} {data.day}, {selectedYear} {data.isToday ? '(Today)' : ''}
                          </div>
                          <div className="text-sm font-black text-[#818CF8] mt-1">₹{data.amount.toLocaleString('en-IN')}</div>
                          {data.isSpike && (
                            <div className="text-[10px] text-amber-400 font-bold mt-1">⚠️ High Spend Day (Click to drill down)</div>
                          )}
                        </div>
                      );
                    }
                    return null;
                  }}
                />
                <Bar
                  dataKey="amount"
                  radius={[4, 4, 0, 0]}
                  className="cursor-pointer"
                >
                  {barData.map((entry, index) => {
                    let fillColor = '#16803C';
                    if (selectedDay === entry.day) {
                      fillColor = '#6366F1';
                    } else if (entry.isToday) {
                      fillColor = '#F59E0B'; // Accent Amber color for Today
                    } else if (entry.isSpike) {
                      fillColor = '#EF4444';
                    }
                    return <Cell key={`cell-${index}`} fill={fillColor} />;
                  })}
                  <LabelList
                    dataKey="todayLabel"
                    position="top"
                    style={{ fill: '#F59E0B', fontSize: 10, fontWeight: '800' }}
                  />
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Chart 2: Category Breakdown Donut Chart */}
        <div className="bg-white border border-slate-200 p-6 rounded-2xl shadow-xs space-y-4 flex flex-col justify-between">
          <div>
            <h3 className="text-base font-extrabold text-slate-900 flex items-center gap-2">
              <Layers className="w-5 h-5 text-[#16803C]" />
              Category Breakdown
            </h3>
            <p className="text-xs text-slate-500 font-medium mt-0.5">Top spending allocations for {monthYearLabel}</p>
          </div>

          <div className="h-44 w-full relative flex items-center justify-center">
            {pieData.length > 0 ? (
              <ResponsiveContainer width="100%" height="100%">
                <PieChart>
                  <Pie
                    data={pieData}
                    cx="50%"
                    cy="50%"
                    innerRadius={50}
                    outerRadius={75}
                    paddingAngle={3}
                    dataKey="value"
                  >
                    {pieData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(val) => [`₹${val.toLocaleString('en-IN')}`, 'Spent']}
                  />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="text-center text-xs text-slate-400 font-medium">No expenses logged for {monthYearLabel}</div>
            )}
          </div>

          <div className="space-y-2 pt-2 border-t border-slate-100 max-h-40 overflow-y-auto">
            {pieData.slice(0, 5).map((cat, idx) => {
              const shortVal = cat.value >= 100000
                ? `₹${(cat.value / 100000).toFixed(1).replace('.0', '')}L+`
                : cat.value >= 1000
                ? `₹${(cat.value / 1000).toFixed(1).replace('.0', '')}k+`
                : `₹${Math.round(cat.value)}+`;
              const pct = totalSpent > 0 ? Math.round((cat.value / totalSpent) * 100) : 0;
              return (
                <div key={idx} className="flex items-center justify-between text-xs font-semibold">
                  <div className="flex items-center gap-2">
                    <span className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: cat.color }}></span>
                    <span className="text-slate-700 capitalize">{cat.name}</span>
                  </div>
                  <span className="font-bold text-slate-900">{shortVal} <span className="text-slate-500 font-normal">({pct}%)</span></span>
                </div>
              );
            })}
          </div>
        </div>
      </div>

      <div className="bg-white border border-slate-200 p-6 rounded-2xl shadow-xs space-y-5">
        <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-4">
          <div>
            <h3 className="text-base font-extrabold text-slate-900 flex items-center gap-2 flex-wrap">
              Transactions Log
              {selectedDay !== null && (
                <span className="inline-flex items-center gap-1 text-xs font-bold text-indigo-700 bg-indigo-50 px-2.5 py-0.5 rounded-full border border-indigo-200">
                  <span>Filtered: {monthName.slice(0, 3)} {selectedDay}</span>
                  <button onClick={() => setSelectedDay(null)} className="hover:text-indigo-900 cursor-pointer">
                    <X className="w-3 h-3" />
                  </button>
                </span>
              )}
              {selectedCategory && (
                <span className="inline-flex items-center gap-1 text-xs font-bold text-emerald-700 bg-emerald-50 px-2.5 py-0.5 rounded-full border border-emerald-200">
                  <span>{selectedCategory}</span>
                  <button onClick={() => setSelectedCategory(null)} className="hover:text-emerald-900 cursor-pointer">
                    <X className="w-3 h-3" />
                  </button>
                </span>
              )}
            </h3>
            <p className="text-xs text-slate-500 font-medium">Latest transactions first · {sortedFiltered.length} matching</p>
          </div>

          <div className="flex flex-wrap items-center gap-3">
            <div className="relative">
              <Search className="w-4 h-4 text-slate-400 absolute left-3 top-2.5" />
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search merchant, bank..."
                className="pl-9 pr-3 py-1.5 bg-slate-50 border border-slate-200 rounded-xl text-xs font-medium text-slate-900 focus:outline-none focus:border-[#16803C] w-48"
              />
              {searchQuery && (
                <button onClick={() => setSearchQuery('')} className="absolute right-2.5 top-2 text-slate-400 hover:text-slate-600">
                  <X className="w-3.5 h-3.5" />
                </button>
              )}
            </div>

            <div className="flex items-center bg-slate-100 p-1 rounded-xl gap-1 text-xs font-bold">
              <button
                onClick={() => setActiveTab('all')}
                className={`px-3 py-1.5 rounded-lg transition-all cursor-pointer ${activeTab === 'all' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500 hover:text-slate-700'}`}
              >
                All ({transactions.length})
              </button>

              <button
                onClick={() => setActiveTab('needs_review')}
                className={`px-3 py-1.5 rounded-lg transition-all flex items-center gap-1.5 cursor-pointer ${
                  activeTab === 'needs_review' ? 'bg-amber-600 text-white shadow-xs' : 'text-amber-700 hover:text-amber-800'
                }`}
              >
                <span>Needs Review</span>
                {needsReviewCount > 0 && (
                  <span className="px-1.5 py-0.2 bg-amber-200 text-amber-900 text-[10px] rounded-full font-black">
                    {needsReviewCount}
                  </span>
                )}
              </button>

              <button
                onClick={() => setActiveTab('debit')}
                className={`px-3 py-1.5 rounded-lg transition-all cursor-pointer ${activeTab === 'debit' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500 hover:text-slate-700'}`}
              >
                Debits
              </button>

              <button
                onClick={() => setActiveTab('credit')}
                className={`px-3 py-1.5 rounded-lg transition-all cursor-pointer ${activeTab === 'credit' ? 'bg-white text-slate-900 shadow-xs' : 'text-slate-500 hover:text-slate-700'}`}
              >
                Credits
              </button>
            </div>

            <button
              onClick={exportToCSV}
              className="flex items-center gap-1.5 px-3 py-1.5 bg-slate-900 hover:bg-slate-800 text-white text-xs font-bold rounded-xl shadow-xs transition-colors cursor-pointer"
              title="Download CSV spreadsheet with formula injection protection"
            >
              <Download className="w-3.5 h-3.5" />
              <span>Export CSV</span>
            </button>
          </div>
        </div>

        <div className="relative">
          <div ref={txSentinelRef} className="h-px w-full" aria-hidden="true" />
          <div
            className="overflow-hidden transition-[max-height] duration-500 ease-in-out"
            style={{ maxHeight: visibleTransactions.length === 0 ? 240 : visibleTransactions.length * 200 }}
          >
            <div className="grid grid-cols-1 gap-3">
              {visibleTransactions.length > 0 ? (
                visibleTransactions.map(tx => (
                  <div key={tx.id} className="tx-list-item">
                    <TransactionCard
                      tx={tx}
                      anomaly={tx.merchant ? anomalyMap[tx.merchant.toLowerCase()] : null}
                      onRecategorize={handleRecategorize}
                      onUpdate={handleUpdateTransaction}
                      onDelete={handleDeleteTransaction}
                    />
                  </div>
                ))
              ) : (
                <div className="text-center py-12 text-slate-400 border border-dashed border-slate-200 rounded-xl bg-slate-50 text-xs">
                  No transactions match your active filters or search criteria.
                </div>
              )}
            </div>
          </div>
        </div>

        {remainingCount > 0 && (
          <div className="flex justify-center pt-1">
            <button
              type="button"
              onClick={() => setVisibleCount((count) => count + PAGE_SIZE)}
              className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-slate-50 hover:bg-slate-100 border border-slate-200 text-slate-800 text-xs font-extrabold transition-colors cursor-pointer"
            >
              <ChevronDown className="w-4 h-4 text-[#16803C]" />
              Show More ({remainingCount} remaining)
            </button>
          </div>
        )}
      </div>

      <NeedsReviewModal
        isOpen={isReviewModalOpen}
        onClose={() => setIsReviewModalOpen(false)}
        transactions={needsReviewItems}
        onFinished={() => {
          setIsReviewModalOpen(false);
          loadDashboardData(selectedMonth, selectedYear, false);
        }}
      />
    </div>
  );
};

export default Dashboard;
