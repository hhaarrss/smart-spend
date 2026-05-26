import { Link } from 'react-router-dom';
import React, { useState, useEffect } from 'react';
import { transactionService, budgetService } from '../services/api';
import { useAuth } from '../context/AuthContext';
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, Legend
} from 'recharts';
import {
  DollarSign, TrendingUp, AlertTriangle, ArrowRightLeft,
  Layers, Wallet, ChevronRight, Activity, Calendar
} from 'lucide-react';
import TransactionCard from '../components/TransactionCard';

/**
 * Main dashboard protected page.
 */
const Dashboard = () => {
  const { user } = useAuth();

  const [transactions, setTransactions] = useState([]);
  const [budgets, setBudgets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  // Date states
  const [currentMonthStr, setCurrentMonthStr] = useState('');
  const [monthName, setMonthName] = useState('');

  useEffect(() => {
    const now = new Date();
    const yyyy = now.getFullYear();
    const mm = String(now.getMonth() + 1).padStart(2, '0');
    setCurrentMonthStr(`${yyyy}-${mm}`);
    setMonthName(now.toLocaleString('default', { month: 'long', year: 'numeric' }));

    loadDashboardData(`${yyyy}-${mm}`);
  }, []);

  const loadDashboardData = async (monthStr) => {
    setLoading(true);
    setError('');
    try {
      // Calculate start and end date of current month
      const [year, month] = monthStr.split('-').map(Number);
      const firstDay = new Date(year, month - 1, 1).toISOString();
      const lastDay = new Date(year, month, 0, 23, 59, 59, 999).toISOString();

      // Parallel API calls for performance
      const [txs, budgetLimits] = await Promise.all([
        transactionService.listTransactions({ start_date: firstDay, end_date: lastDay }),
        budgetService.getBudgets()
      ]);

      setTransactions(txs);
      setBudgets(budgetLimits);
      setLoading(false);
    } catch (err) {
      setLoading(false);
      setError('Could not load dashboard data. Check backend connection.');
    }
  };

  // Calculations for current month
  const debits = transactions.filter(t => t.type === 'debit');
  const totalSpent = debits.reduce((acc, t) => acc + parseFloat(t.amount), 0);
  const totalTxCount = transactions.length;

  // Group by category to find top category and build Pie Chart
  const categoryTotals = {};
  debits.forEach(t => {
    categoryTotals[t.category] = (categoryTotals[t.category] || 0) + parseFloat(t.amount);
  });

  // Top category computation
  let topCategory = 'None';
  let topCategoryAmount = 0;
  Object.entries(categoryTotals).forEach(([cat, amt]) => {
    if (amt > topCategoryAmount) {
      topCategoryAmount = amt;
      topCategory = cat;
    }
  });

  // Format Recharts Pie Chart Data
  const COLORS = {
    food: '#f43f5e',
    travel: '#0ea5e9',
    shopping: '#f59e0b',
    utilities: '#a855f7',
    entertainment: '#6366f1',
    healthcare: '#10b981',
    education: '#14b8a6',
    fuel: '#f97316',
    groceries: '#84cc16',
    other: '#64748b'
  };

  const pieData = Object.entries(categoryTotals).map(([name, value]) => ({
    name,
    value,
    color: COLORS[name.trim().toLowerCase()] || COLORS.other
  })).sort((a, b) => b.value - a.value);

  // Group by day to build Recharts Bar Chart Data
  const dailyTotals = {};
  // Initialize all days of the month with 0 so the chart looks continuous
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

  const barData = Object.entries(dailyTotals).map(([day, amount]) => ({
    day: parseInt(day),
    amount: Math.round(amount)
  })).sort((a, b) => a.day - b.day);

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

  // Recent 10 transactions
  const recentTransactions = [...transactions]
    .sort((a, b) => new Date(b.date) - new Date(a.date))
    .slice(0, 10);

  if (loading) {
    return (
      <div className="min-h-screen bg-slate-950 flex justify-center items-center">
        <div className="flex flex-col items-center gap-3">
          <Activity className="w-10 h-10 text-indigo-500 animate-pulse" />
          <span className="text-sm font-semibold text-slate-400">Loading Dashboard Metrics...</span>
        </div>
      </div>
    );
  }

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8 bg-slate-950 min-h-screen text-slate-100 font-sans">
      {/* Welcome Banner */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
        <div>
          <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
            Hi, {user?.full_name || 'User'}
          </h1>
          <p className="text-sm text-slate-400 mt-1 flex items-center gap-1.5 font-mono">
            <Calendar className="w-4 h-4 text-indigo-400" />
            Active tracking for: <span className="text-white font-semibold">{monthName}</span>
          </p>
        </div>

        {/* Sync Status Badge */}
        <div className="flex items-center gap-2 bg-slate-900 border border-slate-800 px-3.5 py-2 rounded-2xl">
          <div className="w-2.5 h-2.5 bg-emerald-500 rounded-full animate-ping" />
          <span className="text-xs font-semibold text-slate-300">Live Backend Synchronized</span>
        </div>
      </div>

      {/* Grid: 4 Top Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-5">
        {/* Card 1: Total Spent */}
        <div className="bg-slate-900/60 border border-slate-850 p-6 rounded-3xl relative overflow-hidden backdrop-blur-md">
          <div className="absolute top-0 left-0 w-full h-[2px] bg-rose-500/50" />
          <div className="flex justify-between items-start">
            <div>
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Total Spent This Month</p>
              <h3 className="text-2xl font-bold text-white mt-2">
                ₹{totalSpent.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}
              </h3>
            </div>
            <div className="p-2.5 bg-rose-500/10 border border-rose-500/20 text-rose-400 rounded-2xl">
              <DollarSign className="w-5 h-5" />
            </div>
          </div>
          <p className="text-[10px] text-rose-400 mt-3 font-semibold">Total Debit Transactions</p>
        </div>

        {/* Card 2: Transactions Count */}
        <div className="bg-slate-900/60 border border-slate-850 p-6 rounded-3xl relative overflow-hidden backdrop-blur-md">
          <div className="absolute top-0 left-0 w-full h-[2px] bg-indigo-500/50" />
          <div className="flex justify-between items-start">
            <div>
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Total Transactions</p>
              <h3 className="text-2xl font-bold text-white mt-2">{totalTxCount}</h3>
            </div>
            <div className="p-2.5 bg-indigo-500/10 border border-indigo-500/20 text-indigo-400 rounded-2xl">
              <ArrowRightLeft className="w-5 h-5" />
            </div>
          </div>
          <p className="text-[10px] text-indigo-400 mt-3 font-semibold">Credit & Debit total items</p>
        </div>

        {/* Card 3: Top Category */}
        <div className="bg-slate-900/60 border border-slate-850 p-6 rounded-3xl relative overflow-hidden backdrop-blur-md">
          <div className="absolute top-0 left-0 w-full h-[2px] bg-amber-500/50" />
          <div className="flex justify-between items-start">
            <div>
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Top Spending Category</p>
              <h3 className="text-xl font-bold text-white mt-2.5 capitalize truncate max-w-[150px]">{topCategory}</h3>
            </div>
            <div className="p-2.5 bg-amber-500/10 border border-amber-500/20 text-amber-400 rounded-2xl">
              <Layers className="w-5 h-5" />
            </div>
          </div>
          {topCategoryAmount > 0 ? (
            <p className="text-[10px] text-amber-400 mt-3 font-semibold">
              ₹{topCategoryAmount.toLocaleString('en-IN')} total spent
            </p>
          ) : (
            <p className="text-[10px] text-slate-500 mt-3">No debits recorded</p>
          )}
        </div>

        {/* Card 4: Budget Alerts Status */}
        <div className="bg-slate-900/60 border border-slate-850 p-6 rounded-3xl relative overflow-hidden backdrop-blur-md">
          <div className="absolute top-0 left-0 w-full h-[2px] bg-purple-500/50" />
          <div className="flex justify-between items-start">
            <div>
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Active Budget Alerts</p>
              <h3 className="text-2xl font-bold text-white mt-2">{budgetAlerts.length}</h3>
            </div>
            <div className="p-2.5 bg-purple-500/10 border border-purple-500/20 text-purple-400 rounded-2xl">
              <Wallet className="w-5 h-5" />
            </div>
          </div>
          <p className="text-[10px] text-purple-400 mt-3 font-semibold">Exceeded / nearing limits</p>
        </div>
      </div>

      {/* Budget warnings box if alerts triggered */}
      {budgetAlerts.length > 0 && (
        <div className="p-5 rounded-3xl bg-rose-500/5 border border-rose-550/20 space-y-3">
          <div className="flex items-center gap-2 text-rose-400 font-semibold text-sm">
            <AlertTriangle className="w-4 h-4 animate-bounce" />
            <span>Budget Limit Warnings Detected!</span>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
            {budgetAlerts.map(alert => (
              <div key={alert.category} className="p-3.5 rounded-2xl bg-slate-950 border border-rose-500/20 text-xs flex justify-between items-center">
                <div>
                  <span className="font-bold text-white uppercase tracking-wider">{alert.category}</span>
                  <p className="text-[10px] text-slate-500 mt-0.5">₹{alert.spent.toLocaleString('en-IN')} of ₹{alert.limit.toLocaleString('en-IN')}</p>
                </div>
                <span className="font-bold text-rose-400">{alert.percent}%</span>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Grid: Charts Section */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Daily Spending Bar Chart (2/3 width on desktop) */}
        <div className="lg:col-span-2 bg-slate-900/40 border border-slate-850 p-6 rounded-3xl shadow-xl flex flex-col">
          <div className="flex justify-between items-center mb-6">
            <div>
              <h3 className="text-lg font-bold text-white">Daily Spending Curve</h3>
              <p className="text-xs text-slate-400">Total debit amount logged per day</p>
            </div>
            <TrendingUp className="w-4.5 h-4.5 text-indigo-400" />
          </div>

          <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={barData} margin={{ top: 10, right: 10, left: -25, bottom: 0 }}>
                <defs>
                  <linearGradient id="barGlow" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor="#4f46e5" stopOpacity={0.8} />
                    <stop offset="100%" stopColor="#818cf8" stopOpacity={0.15} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#1e293b" />
                <XAxis dataKey="day" stroke="#64748b" tickLine={false} fontSize={11} />
                <YAxis stroke="#64748b" tickLine={false} fontSize={11} />
                <Tooltip
                  cursor={{ fill: '#334155', opacity: 0.15 }}
                  contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '12px', color: '#fff' }}
                  labelFormatter={(label) => `Day ${label}`}
                  formatter={(value) => [`₹${value.toLocaleString('en-IN')}`, 'Spent']}
                />
                <Bar dataKey="amount" fill="url(#barGlow)" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Category Spending Share Pie Chart (1/3 width) */}
        <div className="bg-slate-900/40 border border-slate-850 p-6 rounded-3xl shadow-xl flex flex-col">
          <h3 className="text-lg font-bold text-white mb-1">Spending Share</h3>
          <p className="text-xs text-slate-400 mb-6">Percentage allocation by category</p>

          <div className="h-64 w-full relative flex-grow flex items-center justify-center">
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
                  >
                    {pieData.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip
                    contentStyle={{ backgroundColor: '#0f172a', borderColor: '#334155', borderRadius: '12px', color: '#fff' }}
                    formatter={(value) => `₹${value.toLocaleString('en-IN')}`}
                  />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="text-center text-xs text-slate-500 py-10">
                No debit transactions to map shares.
              </div>
            )}
            {/* Center aggregate number overlay */}
            {pieData.length > 0 && (
              <div className="absolute inset-0 flex flex-col items-center justify-center pointer-events-none mt-[-5px]">
                <span className="text-2xl font-black text-white">₹{Math.round(totalSpent).toLocaleString('en-IN')}</span>
                <span className="text-[10px] text-slate-500 uppercase tracking-widest font-semibold mt-0.5">Debit total</span>
              </div>
            )}
          </div>

          {/* Custom micro legends below pie chart */}
          {pieData.length > 0 && (
            <div className="flex flex-wrap gap-2.5 justify-center mt-3 text-[10px] font-semibold text-slate-400">
              {pieData.slice(0, 5).map(item => (
                <div key={item.name} className="flex items-center gap-1.5">
                  <div className="w-2 h-2 rounded-full" style={{ backgroundColor: item.color }} />
                  <span className="capitalize">{item.name}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Recent Transactions List */}
      <div className="bg-slate-900/20 border border-slate-850 p-6 rounded-3xl shadow-xl space-y-5">
        <div className="flex justify-between items-center">
          <div>
            <h3 className="text-lg font-bold text-white">Recent Transactions</h3>
            <p className="text-xs text-slate-400">Most recent credit & debit events logged</p>
          </div>

          {/* Navigates to Add Page */}
          <Link
            to="/add"
            className="flex items-center gap-1 text-xs font-semibold text-indigo-400 hover:text-indigo-300 transition-all group"
          >
            <span>Log a new transaction</span>
            <ChevronRight className="w-4 h-4 transition-transform group-hover:translate-x-0.5" />
          </Link>
        </div>

        {/* Transaction items list */}
        <div className="grid grid-cols-1 gap-3.5">
          {recentTransactions.length > 0 ? (
            recentTransactions.map(tx => (
              <TransactionCard key={tx.id} tx={tx} />
            ))
          ) : (
            <div className="text-center py-12 text-slate-500 border border-dashed border-slate-800 rounded-2xl bg-slate-950/40 text-sm">
              No transactions recorded for this month. Log manually or parse SMS to start.
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default Dashboard;
