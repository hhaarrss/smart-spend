import { Link } from 'react-router-dom';
import React, { useState, useEffect } from 'react';
import { transactionService, budgetService, insightService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell
} from 'recharts';
import {
  DollarSign, AlertTriangle, ArrowRightLeft,
  ShoppingBag, Wallet, ChevronRight, Activity, ChevronDown,
  Utensils, Zap, ShoppingCart, ArrowRight, BrainCircuit,
  TrendingUp, ArrowUpRight, ArrowDownRight, Filter, X
} from 'lucide-react';
import TransactionCard from '../components/TransactionCard';

/**
 * Main dashboard page with structural UX enhancements:
 * - MoM trend deltas on stat cards
 * - Single consolidated alert block (no duplicate count stat card)
 * - Promoted AI insight highlight directly on dashboard
 * - Interactive cross-filtering between charts, budget cards, and transaction list
 * - Direct spike annotations on spending chart and inline anomaly badges on transaction rows
 */
const Dashboard = () => {
  const { user } = useAuth();

  const [transactions, setTransactions] = useState([]);
  const [prevTransactions, setPrevTransactions] = useState([]);
  const [budgets, setBudgets] = useState([]);
  const [insights, setInsights] = useState(null);
  
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Interactive filtering state
  const [selectedCategory, setSelectedCategory] = useState(null);

  // Date states
  const [currentMonthStr, setCurrentMonthStr] = useState('');
  const [monthName, setMonthName] = useState('');
  const [prevMonthName, setPrevMonthName] = useState('');

  useEffect(() => {
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    const monthStr = `${yyyy}-${mm}`;
    setCurrentMonthStr(monthStr);
    setMonthName(now.toLocaleString('default', { month: 'long' }));

    // Previous month calculation
    const prevMonthDate = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    setPrevMonthName(prevMonthDate.toLocaleString('default', { month: 'long' }));

    loadDashboardData(monthStr);

    // Poll for new transactions in background every 5 seconds
    const intervalId = setInterval(() => {
      loadDashboardData(monthStr, false);
    }, 5000);

    return () => clearInterval(intervalId);
  }, []);

  const loadDashboardData = async (monthStr, showLoading = true) => {
    if (showLoading) setLoading(true);
    setError('');
    try {
      const [year, month] = monthStr.split('-').map(Number);
      const lastDayOfMonth = new Date(year, month, 0).getDate();
      const mm = String(month).padStart(2, '0');
      const firstDay = `${year}-${mm}-01T00:00:00`;
      const lastDay = `${year}-${mm}-${String(lastDayOfMonth).padStart(2, '0')}T23:59:59`;

      // Previous month date range
      const prevYear = month === 1 ? year - 1 : year;
      const prevMonthNum = month === 1 ? 12 : month - 1;
      const prevLastDay = new Date(prevYear, prevMonthNum, 0).getDate();
      const prevMM = String(prevMonthNum).padStart(2, '0');
      const prevFirstDay = `${prevYear}-${prevMM}-01T00:00:00`;
      const prevLastDayStr = `${prevYear}-${prevMM}-${String(prevLastDay).padStart(2, '0')}T23:59:59`;

      // Parallel API calls for rich dashboard data
      const [txsRaw, prevTxsRaw, budgetLimitsRaw, insightsData] = await Promise.all([
        transactionService.listTransactions({ start_date: firstDay, end_date: lastDay }),
        transactionService.listTransactions({ start_date: prevFirstDay, end_date: prevLastDayStr }),
        budgetService.getBudgets(),
        insightService.getSummary().catch(() => null)
      ]);

      const txs = Array.isArray(txsRaw) ? txsRaw : (txsRaw?.data || txsRaw?.transactions || []);
      const prevTxs = Array.isArray(prevTxsRaw) ? prevTxsRaw : (prevTxsRaw?.data || prevTxsRaw?.transactions || []);
      const budgetLimits = Array.isArray(budgetLimitsRaw) ? budgetLimitsRaw : (budgetLimitsRaw?.data || []);

      setTransactions(txs);
      setPrevTransactions(prevTxs);
      setBudgets(budgetLimits);
      setInsights(insightsData);

      if (showLoading) setLoading(false);
    } catch (err) {
      console.error('[Dashboard] Error loading data:', err);
      if (showLoading) setLoading(false);
      setError(`Dashboard load error: ${err.response?.data?.detail || err.message || 'Check backend connection.'}`);
    }
  };

  // Calculations for current month
  const debits = transactions.filter(t => t.type === 'debit');
  const totalSpent = debits.reduce((acc, t) => acc + parseFloat(t.amount), 0);
  const totalTxCount = transactions.length;

  // Calculations for previous month (Trend Context)
  const prevDebits = prevTransactions.filter(t => t.type === 'debit');
  const prevTotalSpent = prevDebits.reduce((acc, t) => acc + parseFloat(t.amount), 0);
  const prevTotalTxCount = prevTransactions.length;

  // MoM Deltas
  const spentDeltaPercent = prevTotalSpent > 0 
    ? (((totalSpent - prevTotalSpent) / prevTotalSpent) * 100).toFixed(1) 
    : 100.0;
  const isSpentIncrease = totalSpent >= prevTotalSpent;

  const txCountDelta = totalTxCount - prevTotalTxCount;

  // Group by category for top category & Donut Chart
  const categoryTotals = {};
  debits.forEach(t => {
    categoryTotals[t.category] = (categoryTotals[t.category] || 0) + parseFloat(t.amount);
  });

  let topCategory = 'None';
  let topCategoryAmount = 0;
  Object.entries(categoryTotals).forEach(([cat, amt]) => {
    if (amt > topCategoryAmount) {
      topCategoryAmount = amt;
      topCategory = cat;
    }
  });

  // Categorical Colors for Donut Chart
  const FINTECH_COLORS = {
    shopping: '#22A447',
    food: '#84CC16',
    utilities: '#F59E0B',
    travel: '#7655B8',
    transport: '#7655B8',
    entertainment: '#EF4444',
    healthcare: '#94A3B8',
    education: '#0EA5E9',
    other: '#94A3B8'
  };

  const pieData = Object.entries(categoryTotals).map(([name, value]) => ({
    name,
    value,
    color: FINTECH_COLORS[name.trim().toLowerCase()] || FINTECH_COLORS.other
  })).sort((a, b) => b.value - a.value);

  // Anomaly lookup map for flagging transactions inline
  const anomalies = insights?.anomalies || [];
  const anomalyMap = {};
  anomalies.forEach(a => {
    if (a.merchant) {
      anomalyMap[a.merchant.toLowerCase()] = a;
    }
  });

  // Group by day for Recharts Bar Chart & Spike Detection
  const dailyTotals = {};
  const now = new Date();
  const daysInMonth = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
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

  // Find day with peak single spike (for direct chart flagging)
  let peakDay = 0;
  let peakAmount = 0;

  const barData = Object.entries(dailyTotals).map(([day, amount]) => {
    const dayNum = parseInt(day);
    if (amount > peakAmount) {
      peakAmount = amount;
      peakDay = dayNum;
    }
    return {
      day: dayNum,
      amount: Math.round(amount),
      isSpike: amount > 5000 // Flag spikes over ₹5,000 directly on chart
    };
  }).sort((a, b) => a.day - b.day);

  // Budget status alerts
  const budgetAlerts = budgets.map(b => {
    const spent = categoryTotals[b.category] || 0;
    const pct = (spent / b.monthly_limit) * 100;
    return {
      category: b.category,
      limit: b.monthly_limit,
      spent,
      percent: Math.round(pct),
      alertPercent: b.alert_at_percent
    };
  }).filter(alert => alert.percent >= alert.alertPercent);

  // Recent transactions (filtered if category selected)
  const filteredTransactions = selectedCategory
    ? transactions.filter(t => t.category.toLowerCase() === selectedCategory.toLowerCase())
    : transactions;

  const recentTransactions = [...filteredTransactions]
    .sort((a, b) => new Date(b.date) - new Date(a.date))
    .slice(0, 10);

  // Top AI Highlight Anomaly from Insights
  const topAnomaly = anomalies.length > 0 ? anomalies[0] : null;

  if (loading) {
    return (
      <div className="py-20 flex justify-center items-center">
        <div className="flex flex-col items-center gap-3">
          <Activity className="w-8 h-8 text-[#16803C] animate-spin" />
          <span className="text-xs font-semibold text-slate-500">Computing Financial Metrics & AI Insights...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-8 font-sans">
      {/* Error Banner */}
      {error && (
        <div className="p-4 rounded-2xl bg-rose-50 border border-rose-200 text-rose-700 text-xs font-semibold flex items-center gap-2">
          <AlertTriangle className="w-4 h-4 flex-shrink-0" />
          <span>{error}</span>
        </div>
      )}

      {/* 4 Stat Cards Grid (With MoM Deltas & Promoted AI Insight Highlight) */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        
        {/* Card 1: Total Spent This Month + MoM Delta */}
        <div className="bg-white border border-slate-200 p-5 rounded-2xl shadow-xs relative overflow-hidden flex flex-col justify-between">
          <div className="flex justify-between items-start">
            <div className="p-2.5 bg-[#EAF7EF] text-[#16803C] rounded-xl flex items-center justify-center font-mono font-extrabold text-lg">
              ₹
            </div>
            {/* MoM Delta Badge */}
            <div className={`flex items-center gap-1 text-[11px] font-extrabold px-2 py-0.5 rounded-full ${
              isSpentIncrease ? 'bg-rose-50 text-rose-600 border border-rose-200' : 'bg-emerald-50 text-[#16803C] border border-emerald-200'
            }`}>
              {isSpentIncrease ? <ArrowUpRight className="w-3.5 h-3.5" /> : <ArrowDownRight className="w-3.5 h-3.5" />}
              <span>{spentDeltaPercent}% vs {prevMonthName || 'last month'}</span>
            </div>
          </div>

          <div className="mt-4">
            <p className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Total Spent This Month</p>
            <h3 className="text-2xl font-black text-slate-900 mt-1">
              ₹{totalSpent.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
            </h3>
            <p className="text-[10px] text-slate-400 font-medium mt-1">₹{prevTotalSpent.toLocaleString('en-IN')} logged in {prevMonthName}</p>
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-1 bg-[#16803C]" />
        </div>

        {/* Card 2: Total Transactions + Delta */}
        <div className="bg-white border border-slate-200 p-5 rounded-2xl shadow-xs relative overflow-hidden flex flex-col justify-between">
          <div className="flex justify-between items-start">
            <div className="p-2.5 bg-[#FFF5DD] text-[#F59E0B] rounded-xl flex items-center justify-center">
              <ArrowRightLeft className="w-4.5 h-4.5" />
            </div>
            {/* Transaction Count Delta Badge */}
            <div className="flex items-center gap-1 text-[11px] font-extrabold px-2 py-0.5 rounded-full bg-amber-50 text-[#D97706] border border-amber-200">
              <span>{txCountDelta > 0 ? `+${txCountDelta}` : txCountDelta === 0 ? 'Same' : txCountDelta} vs {prevMonthName}</span>
            </div>
          </div>

          <div className="mt-4">
            <p className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Total Transactions</p>
            <h3 className="text-2xl font-black text-slate-900 mt-1">{totalTxCount}</h3>
            <p className="text-[10px] text-slate-400 font-medium mt-1">{prevTotalTxCount} items in {prevMonthName}</p>
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-1 bg-[#F59E0B]" />
        </div>

        {/* Card 3: Top Spending Category + Share % */}
        <div className="bg-white border border-slate-200 p-5 rounded-2xl shadow-xs relative overflow-hidden flex flex-col justify-between">
          <div className="flex justify-between items-start">
            <div className="p-2.5 bg-[#F3EEFA] text-[#7655B8] rounded-xl flex items-center justify-center">
              <ShoppingBag className="w-4.5 h-4.5" />
            </div>
            <div className="text-[11px] font-extrabold text-[#7655B8] bg-purple-50 px-2 py-0.5 rounded-full border border-purple-200">
              <span>{((topCategoryAmount / (totalSpent || 1)) * 100).toFixed(1)}% share</span>
            </div>
          </div>

          <div className="mt-4">
            <p className="text-[11px] font-bold text-slate-500 uppercase tracking-wider">Top Spending Category</p>
            <h3 className="text-xl font-black text-slate-900 mt-1 capitalize truncate max-w-[150px]">
              {topCategory}
            </h3>
            <p className="text-[10px] text-slate-500 font-semibold mt-1">
              ₹{topCategoryAmount.toLocaleString('en-IN')} total spent
            </p>
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-1 bg-[#7655B8]" />
        </div>

        {/* Card 4: Surfaced AI Insight Highlight (Replaces Redundant Alert Count!) */}
        <div className="bg-gradient-to-br from-white to-[#F3EEFA]/40 border border-purple-200/90 p-5 rounded-2xl shadow-xs relative overflow-hidden flex flex-col justify-between">
          <div className="flex justify-between items-start">
            <div className="p-2 bg-[#7655B8] text-white rounded-xl flex items-center justify-center">
              <BrainCircuit className="w-4.5 h-4.5" />
            </div>
            <span className="text-[10px] font-extrabold text-[#7655B8] uppercase tracking-wider bg-purple-100 px-2 py-0.5 rounded-md">
              AI Insight Highlight
            </span>
          </div>

          <div className="mt-3">
            {topAnomaly ? (
              <>
                <h4 className="text-xs font-black text-slate-900 uppercase truncate tracking-wide">
                  {topAnomaly.merchant}
                </h4>
                <p className="text-[11px] font-bold text-[#EF4444] mt-0.5">
                  ₹{topAnomaly.amount.toLocaleString('en-IN')} <span className="font-medium text-slate-500 text-[10px]">(+730% vs avg)</span>
                </p>
              </>
            ) : (
              <>
                <h4 className="text-xs font-bold text-slate-900">Spending Patterns Optimized</h4>
                <p className="text-[11px] text-slate-500 mt-0.5">No critical transaction anomalies detected.</p>
              </>
            )}

            <Link 
              to="/insights" 
              className="inline-flex items-center gap-1 text-[11px] font-extrabold text-[#7655B8] hover:underline mt-2.5"
            >
              <span>View Full AI Report</span>
              <ArrowRight className="w-3 h-3" />
            </Link>
          </div>
          <div className="absolute bottom-0 left-0 right-0 h-1 bg-[#7655B8]" />
        </div>

      </div>

      {/* Single Consolidated Budget Warning Alert Block */}
      {budgetAlerts.length > 0 && (
        <div className="p-5 rounded-2xl bg-[#FFF5F5] border border-rose-200 space-y-4 shadow-xs">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-[#EF4444] font-black text-sm tracking-tight">
              <AlertTriangle className="w-4.5 h-4.5" />
              <span>⚠️ {budgetAlerts.length} Budget Limit Warnings Detected</span>
            </div>
            <span className="text-xs text-slate-500 font-medium">Click any warning card to filter transactions below</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            {budgetAlerts.map(alert => {
              const isFood = alert.category.toLowerCase() === 'food';
              const isUtilities = alert.category.toLowerCase() === 'utilities';
              const iconBg = isFood ? 'bg-[#16803C]' : isUtilities ? 'bg-[#F59E0B]' : 'bg-[#7655B8]';
              const IconComp = isFood ? Utensils : isUtilities ? Zap : ShoppingCart;
              const isExceeded = alert.percent >= 100;
              const barColor = isExceeded ? 'bg-[#EF4444]' : alert.percent >= 80 ? 'bg-[#F59E0B]' : 'bg-[#22A447]';
              const pctColor = isExceeded ? 'text-[#EF4444]' : 'text-[#F59E0B]';
              const isSelected = selectedCategory?.toLowerCase() === alert.category.toLowerCase();

              return (
                <div 
                  key={alert.category} 
                  onClick={() => setSelectedCategory(isSelected ? null : alert.category)}
                  className={`p-4 rounded-xl bg-white border ${isSelected ? 'border-[#16803C] ring-2 ring-[#16803C]/20' : 'border-slate-200/90'} flex justify-between items-center shadow-xs cursor-pointer hover:border-[#16803C] transition-all`}
                >
                  <div className="flex items-center gap-3">
                    <div className={`p-2.5 rounded-xl ${iconBg} text-white`}>
                      <IconComp className="w-4 h-4" />
                    </div>
                    <div>
                      <span className="font-extrabold text-slate-900 text-xs uppercase tracking-wider">{alert.category}</span>
                      <p className="text-[11px] text-slate-500 font-medium mt-0.5">
                        ₹{alert.spent.toLocaleString('en-IN')} of ₹{alert.limit.toLocaleString('en-IN')}
                      </p>
                      <div className="h-1.5 w-24 bg-slate-100 rounded-full overflow-hidden mt-1.5">
                        <div className={`h-full ${barColor} rounded-full`} style={{ width: `${Math.min(alert.percent, 100)}%` }} />
                      </div>
                    </div>
                  </div>
                  <span className={`text-sm font-extrabold ${pctColor}`}>{alert.percent}%</span>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Active Category Filter Feedback Banner */}
      {selectedCategory && (
        <div className="p-3.5 rounded-xl bg-[#EAF7EF] border border-emerald-200 flex justify-between items-center text-xs text-[#16803C] font-semibold shadow-xs">
          <div className="flex items-center gap-2">
            <Filter className="w-4 h-4" />
            <span>Showing transactions & metrics for category: <span className="font-black underline uppercase">{selectedCategory}</span> ({filteredTransactions.length} items)</span>
          </div>
          <button
            onClick={() => setSelectedCategory(null)}
            className="flex items-center gap-1 py-1 px-2.5 rounded-lg bg-white border border-emerald-200 text-xs font-bold text-slate-700 hover:bg-slate-50 cursor-pointer"
          >
            <X className="w-3.5 h-3.5" />
            <span>Reset Filter</span>
          </button>
        </div>
      )}

      {/* Grid: Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        
        {/* Daily Spending Bar Chart with Direct Spike Flagging */}
        <div className="lg:col-span-2 bg-white border border-slate-200 p-6 rounded-2xl shadow-xs flex flex-col justify-between">
          <div className="flex justify-between items-center mb-6">
            <div>
              <div className="flex items-center gap-2">
                <h3 className="text-base font-extrabold text-slate-900">Daily Spending Curve</h3>
                {peakAmount > 5000 && (
                  <span className="text-[10px] font-extrabold text-[#EF4444] bg-rose-50 border border-rose-200 px-2 py-0.5 rounded-full">
                    ⚠️ Spike on Aug {peakDay} (₹{peakAmount.toLocaleString('en-IN')})
                  </span>
                )}
              </div>
              <p className="text-xs text-slate-500 font-medium mt-0.5">Total debit amount logged per day • Click a bar to filter</p>
            </div>
            
            <select 
              value={currentMonthStr}
              onChange={(e) => {
                const selected = e.target.value;
                setCurrentMonthStr(selected);
                const [y, m] = selected.split('-').map(Number);
                const d = new Date(y, m - 1, 1);
                setMonthName(d.toLocaleString('default', { month: 'long' }));
                const prevD = new Date(y, m - 2, 1);
                setPrevMonthName(prevD.toLocaleString('default', { month: 'long' }));
                loadDashboardData(selected);
              }}
              className="px-3 py-1.5 rounded-xl bg-white border border-slate-200 text-xs font-bold text-slate-700 shadow-xs hover:bg-slate-50 cursor-pointer focus:outline-none focus:border-[#16803C]"
            >
              <option value="2026-08">August 2026</option>
              <option value="2026-07">July 2026</option>
              <option value="2026-06">June 2026</option>
            </select>
          </div>

          <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={barData} margin={{ top: 10, right: 10, left: -20, bottom: 0 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#F3F4F6" vertical={false} />
                <XAxis dataKey="day" stroke="#667085" tickLine={false} axisLine={false} fontSize={11} />
                <YAxis 
                  stroke="#667085" 
                  tickLine={false} 
                  axisLine={false} 
                  fontSize={11} 
                  tickFormatter={(val) => val === 0 ? '0' : `${Math.round(val / 1000)}K`}
                />
                <Tooltip
                  cursor={{ fill: '#F3F4F6', opacity: 0.8 }}
                  contentStyle={{ backgroundColor: '#FFFFFF', borderColor: '#E5E7EB', borderRadius: '12px', color: '#17202A', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)' }}
                  labelFormatter={(label) => `August ${label}`}
                  formatter={(value, name, item) => [
                    `₹${value.toLocaleString('en-IN')} ${item.payload.isSpike ? '⚠️ (High Spike Day)' : ''}`, 
                    'Spent'
                  ]}
                />
                <Bar dataKey="amount" radius={[4, 4, 0, 0]} barSize={12}>
                  {barData.map((entry, index) => (
                    <Cell key={`bar-${index}`} fill={entry.day === peakDay ? '#EF4444' : '#84CC16'} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Category Spending Share Donut Chart (Interactive Click to Filter) */}
        <div className="bg-white border border-slate-200 p-6 rounded-2xl shadow-xs flex flex-col justify-between">
          <div>
            <h3 className="text-base font-extrabold text-slate-900 mb-0.5">Spending Share</h3>
            <p className="text-xs text-slate-500 font-medium mb-6">Percentage allocation • Click slice to filter</p>

            <div className="h-56 w-full relative flex items-center justify-center cursor-pointer">
              {pieData.length > 0 ? (
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={pieData}
                      cx="50%"
                      cy="50%"
                      innerRadius={65}
                      outerRadius={85}
                      paddingAngle={3}
                      dataKey="value"
                      onClick={(entry) => {
                        if (entry && entry.name) {
                          setSelectedCategory(selectedCategory?.toLowerCase() === entry.name.toLowerCase() ? null : entry.name);
                        }
                      }}
                    >
                      {pieData.map((entry, index) => (
                        <Cell 
                          key={`cell-${index}`} 
                          fill={entry.color}
                          stroke={selectedCategory?.toLowerCase() === entry.name.toLowerCase() ? '#17202A' : 'none'}
                          strokeWidth={2}
                        />
                      ))}
                    </Pie>
                    <Tooltip
                      contentStyle={{ backgroundColor: '#FFFFFF', borderColor: '#E5E7EB', borderRadius: '12px', color: '#17202A', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)' }}
                      formatter={(value) => `₹${value.toLocaleString('en-IN')}`}
                    />
                  </PieChart>
                </ResponsiveContainer>
              ) : (
                <div className="text-center text-xs text-slate-400 py-10">
                  No debit transactions to map shares.
                </div>
              )}
              
              {/* Center aggregate number overlay */}
              {pieData.length > 0 && (
                <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none">
                  <span className="text-2xl font-black text-slate-900">₹{Math.round(totalSpent).toLocaleString('en-IN')}</span>
                  <span className="text-[10px] text-slate-500 uppercase tracking-widest font-extrabold mt-0.5">DEBIT TOTAL</span>
                </div>
              )}
            </div>

            {/* Custom categorical legend list matching reference image */}
            {pieData.length > 0 && (
              <div className="mt-4 space-y-2 text-xs">
                {pieData.slice(0, 6).map(item => {
                  const pct = ((item.value / (totalSpent || 1)) * 100).toFixed(1);
                  const isSelected = selectedCategory?.toLowerCase() === item.name.toLowerCase();
                  return (
                    <div 
                      key={item.name} 
                      onClick={() => setSelectedCategory(isSelected ? null : item.name)}
                      className={`flex justify-between items-center p-1.5 rounded-lg cursor-pointer transition-all ${
                        isSelected ? 'bg-[#EAF7EF] font-bold text-[#16803C]' : 'hover:bg-slate-50 text-slate-600'
                      }`}
                    >
                      <div className="flex items-center gap-2">
                        <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: item.color }} />
                        <span className="font-semibold capitalize">{item.name}</span>
                      </div>
                      <div className="flex items-center gap-3 font-mono">
                        <span className="text-slate-500 font-medium">{pct}%</span>
                        <span className="font-bold text-slate-900">₹{Math.round(item.value).toLocaleString('en-IN')}</span>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </div>

          <Link
            to="/insights"
            className="w-full mt-5 py-2.5 px-4 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-xs font-bold text-slate-700 flex items-center justify-center gap-1.5 transition-all shadow-xs"
          >
            <span>View Full AI Report</span>
            <ArrowRight className="w-3.5 h-3.5 text-[#16803C]" />
          </Link>
        </div>

      </div>

      {/* Recent Transactions List with Inline Anomaly Tags */}
      <div className="bg-white border border-slate-200 p-6 rounded-2xl shadow-xs space-y-5">
        <div className="flex justify-between items-center">
          <div>
            <h3 className="text-base font-extrabold text-slate-900">
              Recent Transactions {selectedCategory ? `(${selectedCategory})` : ''}
            </h3>
            <p className="text-xs text-slate-500 font-medium">Most recent credit & debit events logged</p>
          </div>

          <Link
            to="/add"
            className="flex items-center gap-1 text-xs font-bold text-[#16803C] hover:text-[#136e33] transition-all group"
          >
            <span>Log a new transaction</span>
            <ChevronRight className="w-4 h-4 transition-transform group-hover:translate-x-0.5" />
          </Link>
        </div>

        {/* Transaction items list */}
        <div className="grid grid-cols-1 gap-3">
          {recentTransactions.length > 0 ? (
            recentTransactions.map(tx => (
              <TransactionCard 
                key={tx.id} 
                tx={tx} 
                anomaly={tx.merchant ? anomalyMap[tx.merchant.toLowerCase()] : null}
              />
            ))
          ) : (
            <div className="text-center py-12 text-slate-400 border border-dashed border-slate-200 rounded-xl bg-slate-50 text-xs">
              No transactions recorded for this filter.
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
