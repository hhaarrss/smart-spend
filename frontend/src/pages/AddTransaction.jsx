import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { transactionService } from '../services/api';
import { 
  PlusCircle, Sparkles, MessageSquare, Landmark,
  DollarSign, ListFilter, Tag, Calendar, User,
  CheckCircle2, AlertCircle, Loader2
} from 'lucide-react';

/**
 * Add Transaction Protected Page.
 * Supports manual logging and automated SMS parsing.
 */
const AddTransaction = () => {
  const navigate = useNavigate();

  // Form states
  const [amount, setAmount] = useState('');
  const [type, setType] = useState('debit');
  const [category, setCategory] = useState('Food');
  const [merchant, setMerchant] = useState('');
  const [date, setDate] = useState('');
  
  // Custom states matching db schema but set manually
  const [bank, setBank] = useState('');
  const [accountLast4, setAccountLast4] = useState('');

  // Interface feedback states
  const [loading, setLoading] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');
  const [errorMsg, setErrorMsg] = useState('');

  // SMS Parsing states
  const [rawSMS, setRawSMS] = useState('');
  const [sender, setSender] = useState('HDFCBK');
  const [parsing, setParsing] = useState(false);
  const [parseFeedback, setParseFeedback] = useState({ type: '', msg: '' });

  // Standard category options
  const CATEGORIES = [
    'Food', 'Travel', 'Shopping', 'Utilities', 'Entertainment',
    'Healthcare', 'Education', 'Fuel', 'Groceries', 'Other'
  ];

  // Set default date to today
  useEffect(() => {
    const today = new Date().toISOString().substring(0, 10);
    setDate(today);
  }, []);

  // Form submit manual logging
  const handleFormSubmit = async (e) => {
    e.preventDefault();
    if (!amount || parseFloat(amount) <= 0) {
      setErrorMsg('Please specify a valid positive transaction amount.');
      return;
    }

    setLoading(true);
    setErrorMsg('');
    setSuccessMsg('');

    try {
      // Create transaction via service
      const dateISO = new Date(date).toISOString();
      await transactionService.createTransaction({
        amount: parseFloat(amount),
        type,
        category,
        merchant: merchant || 'Unknown Merchant',
        bank: bank || null,
        account_last4: accountLast4 || null,
        date: dateISO,
        source: 'manual'
      });

      setSuccessMsg('Transaction logged successfully! Redirecting...');
      setLoading(false);
      
      setTimeout(() => {
        navigate('/');
      }, 1500);
    } catch (err) {
      setLoading(false);
      setErrorMsg(err.response?.data?.detail || 'Failed to save transaction.');
    }
  };

  // Ingest raw SMS for auto-filling and saving
  const handleSMSParse = async () => {
    if (!rawSMS.trim()) {
      setParseFeedback({ type: 'error', msg: 'Please paste a valid raw SMS alert.' });
      return;
    }

    setParsing(true);
    setParseFeedback({ type: '', msg: '' });

    try {
      const data = await transactionService.ingestSMS(rawSMS, sender);
      setParsing(false);

      if (data.success) {
        // Auto fill form with extracted params
        const tx = data.transaction;
        setAmount(tx.amount);
        setType(tx.type);
        setCategory(tx.category);
        setMerchant(tx.merchant || '');
        setBank(tx.bank || '');
        setAccountLast4(tx.account_last4 || '');
        
        // Format ISO Date (YYYY-MM-DD) for form input
        if (tx.date) {
          const dateOnly = tx.date.substring(0, 10);
          setDate(dateOnly);
        }

        setParseFeedback({ 
          type: 'success', 
          msg: `SMS successfully parsed! Extracted ₹${tx.amount} to ${tx.merchant || 'Unknown'}. Form fields auto-filled.`
        });
      } else {
        setParseFeedback({ 
          type: 'error', 
          msg: data.message || 'SMS matches format but could not be parsed.' 
        });
      }
    } catch (err) {
      setParsing(false);
      setParseFeedback({ 
        type: 'error', 
        msg: err.response?.data?.detail || 'Failed to communicate with ingestion engine.' 
      });
    }
  };

  return (
    <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-8 bg-slate-950 min-h-screen text-slate-100 font-sans">
      
      {/* Title */}
      <div>
        <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight flex items-center gap-2">
          <PlusCircle className="w-8 h-8 text-indigo-500" />
          Log Transaction
        </h1>
        <p className="text-sm text-slate-400 mt-1">Manual logging or automated bank SMS text ingestion</p>
      </div>

      {/* Main Grid split on desktop */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        
        {/* Panel 1: Logger Form */}
        <div className="bg-slate-900 border border-slate-850 p-6 sm:p-8 rounded-3xl relative overflow-hidden shadow-2xl">
          <div className="absolute top-0 left-0 w-full h-[3px] bg-gradient-to-r from-indigo-500 to-violet-500" />
          
          <h3 className="text-lg font-bold text-white mb-1">Transaction Details</h3>
          <p className="text-xs text-slate-400 mb-6">Complete transaction fields below to manually log</p>

          {/* Feedback alerts */}
          {errorMsg && (
            <div className="flex items-center gap-2 p-3 bg-rose-500/10 border border-rose-500/20 rounded-2xl text-xs text-rose-400 mb-5">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          {successMsg && (
            <div className="flex items-center gap-2 p-3 bg-emerald-500/10 border border-emerald-500/20 rounded-2xl text-xs text-emerald-400 mb-5">
              <CheckCircle2 className="w-4 h-4 flex-shrink-0 animate-bounce" />
              <span>{successMsg}</span>
            </div>
          )}

          <form onSubmit={handleFormSubmit} className="space-y-5">
            {/* Grid for Amount & Type */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
              {/* Amount */}
              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Amount (INR)</label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                    <DollarSign className="w-4 h-4" />
                  </div>
                  <input
                    type="number"
                    step="0.01"
                    required
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    placeholder="250.00"
                    className="block w-full pl-10 pr-3.5 py-3 rounded-2xl bg-slate-950 border border-slate-800 text-white placeholder-slate-700 focus:outline-none focus:border-indigo-500 transition-all text-sm font-semibold"
                  />
                </div>
              </div>

              {/* Type Toggle */}
              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Type</label>
                <div className="flex bg-slate-950 p-1.5 rounded-2xl border border-slate-800">
                  <button
                    type="button"
                    onClick={() => setType('debit')}
                    className={`flex-1 py-2 text-xs font-bold rounded-xl transition-all cursor-pointer ${
                      type === 'debit' 
                        ? 'bg-rose-500/10 text-rose-400 border border-rose-500/20 shadow-md shadow-rose-950/20' 
                        : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    Debit (Expense)
                  </button>
                  <button
                    type="button"
                    onClick={() => setType('credit')}
                    className={`flex-1 py-2 text-xs font-bold rounded-xl transition-all cursor-pointer ${
                      type === 'credit' 
                        ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 shadow-md shadow-emerald-950/20' 
                        : 'text-slate-400 hover:text-slate-200'
                    }`}
                  >
                    Credit (Income)
                  </button>
                </div>
              </div>
            </div>

            {/* Grid for Category & Date */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
              {/* Category Dropdown */}
              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Category</label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                    <Tag className="w-4 h-4" />
                  </div>
                  <select
                    value={category}
                    onChange={(e) => setCategory(e.target.value)}
                    className="block w-full pl-10 pr-3.5 py-3 rounded-2xl bg-slate-950 border border-slate-800 text-white placeholder-slate-700 focus:outline-none focus:border-indigo-500 transition-all text-sm capitalize appearance-none cursor-pointer"
                  >
                    {CATEGORIES.map(cat => (
                      <option key={cat} value={cat} className="capitalize">{cat}</option>
                    ))}
                  </select>
                </div>
              </div>

              {/* Date Picker */}
              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Transaction Date</label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                    <Calendar className="w-4 h-4" />
                  </div>
                  <input
                    type="date"
                    required
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    className="block w-full pl-10 pr-3.5 py-3 rounded-2xl bg-slate-950 border border-slate-800 text-white focus:outline-none focus:border-indigo-500 transition-all text-sm font-mono"
                  />
                </div>
              </div>
            </div>

            {/* Merchant */}
            <div>
              <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Merchant Name</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                  <User className="w-4 h-4" />
                </div>
                <input
                  type="text"
                  value={merchant}
                  onChange={(e) => setMerchant(e.target.value)}
                  placeholder="Swiggy, Amazon, Airtel"
                  className="block w-full pl-10 pr-3.5 py-3 rounded-2xl bg-slate-950 border border-slate-800 text-white placeholder-slate-700 focus:outline-none focus:border-indigo-500 transition-all text-sm"
                />
              </div>
            </div>

            {/* Collapsible/Optional Bank Info (Hidden behind manual source for clean form, populated automatically on parse) */}
            {(bank || accountLast4) && (
              <div className="p-4 rounded-2xl bg-indigo-950/10 border border-indigo-500/20 text-xs space-y-2">
                <span className="font-semibold text-indigo-400 flex items-center gap-1.5">
                  <Landmark className="w-3.5 h-3.5" />
                  Bank metadata auto-extracted from SMS:
                </span>
                <div className="grid grid-cols-2 gap-4 text-slate-400">
                  <div>
                    <span className="font-medium text-slate-500">Bank: </span>
                    <span className="font-mono text-white">{bank}</span>
                  </div>
                  <div>
                    <span className="font-medium text-slate-500">Card/Ac ending: </span>
                    <span className="font-mono text-white">*{accountLast4}</span>
                  </div>
                </div>
              </div>
            )}

            {/* Submit Button */}
            <button
              type="submit"
              disabled={loading}
              className="w-full py-3.5 px-4 rounded-2xl font-semibold text-white bg-indigo-600 hover:bg-indigo-500 border border-indigo-500/20 hover:border-indigo-500/40 shadow-lg shadow-indigo-650/15 hover:shadow-indigo-650/25 flex items-center justify-center gap-2 cursor-pointer transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {loading ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  <span>Logging entry...</span>
                </>
              ) : (
                <span>Save Transaction</span>
              )}
            </button>
          </form>
        </div>

        {/* Panel 2: SMS Ingestion Parser */}
        <div className="bg-slate-900 border border-slate-850 p-6 sm:p-8 rounded-3xl relative overflow-hidden shadow-2xl flex flex-col">
          <div className="absolute top-0 left-0 w-full h-[3px] bg-gradient-to-r from-violet-500 to-indigo-500" />
          
          <div className="flex items-center gap-2 mb-1">
            <h3 className="text-lg font-bold text-white">AI SMS Auto-Ingest</h3>
            <Sparkles className="w-4 h-4 text-indigo-400 animate-pulse" />
          </div>
          <p className="text-xs text-slate-400 mb-6">Paste transactional messages from Indian banks to parse immediately</p>

          {/* SMS Parsing Alerts */}
          {parseFeedback.msg && (
            <div className={`flex items-center gap-2 p-3.5 rounded-2xl text-xs mb-5 ${
              parseFeedback.type === 'success' 
                ? 'bg-emerald-500/10 border border-emerald-500/20 text-emerald-400' 
                : 'bg-rose-500/10 border border-rose-500/20 text-rose-400'
            }`}>
              {parseFeedback.type === 'success' ? (
                <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
              ) : (
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
              )}
              <span>{parseFeedback.msg}</span>
            </div>
          )}

          <div className="space-y-5 flex-grow flex flex-col">
            {/* Sender input */}
            <div>
              <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Sender Code / Bank Keyword</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-500">
                  <Landmark className="w-4 h-4" />
                </div>
                <input
                  type="text"
                  value={sender}
                  onChange={(e) => setSender(e.target.value)}
                  placeholder="HDFCBK, SBI, ICICI"
                  className="block w-full pl-10 pr-3.5 py-3 rounded-2xl bg-slate-950 border border-slate-800 text-white placeholder-slate-700 focus:outline-none focus:border-indigo-500 transition-all text-sm uppercase"
                />
              </div>
              <p className="text-[10px] text-slate-500 mt-1.5 font-mono">Usually matches the SMS header code (e.g. AD-HDFCBK)</p>
            </div>

            {/* Paste SMS content box */}
            <div className="flex-grow flex flex-col">
              <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-2">Paste raw SMS content</label>
              <div className="relative flex-grow flex flex-col">
                <div className="absolute top-3.5 left-3.5 pointer-events-none text-slate-500">
                  <MessageSquare className="w-4 h-4" />
                </div>
                <textarea
                  value={rawSMS}
                  onChange={(e) => setRawSMS(e.target.value)}
                  rows="6"
                  placeholder="e.g. Alert: You've made a payment of Rs. 150.00 to Swiggy using HDFC Bank Card ending 1234 on 26-05-26. Bal: Rs. 12000.00"
                  className="block w-full pl-10 pr-3.5 py-3.5 rounded-2xl bg-slate-950 border border-slate-800 text-white placeholder-slate-750 focus:outline-none focus:border-indigo-500 transition-all text-xs font-mono resize-none flex-grow"
                />
              </div>
            </div>

            {/* Parse trigger button */}
            <button
              type="button"
              onClick={handleSMSParse}
              disabled={parsing}
              className="w-full py-3.5 px-4 rounded-2xl font-semibold text-white bg-indigo-600/10 border border-indigo-500/35 hover:bg-indigo-600 hover:text-white hover:border-indigo-500/40 shadow-lg shadow-indigo-950/20 flex items-center justify-center gap-2 cursor-pointer transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              {parsing ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin text-indigo-400" />
                  <span className="text-slate-300">Parsing SMS parameters...</span>
                </>
              ) : (
                <>
                  <Sparkles className="w-4 h-4 text-indigo-400" />
                  <span>Parse SMS & Auto-Fill Form</span>
                </>
              )}
            </button>
          </div>
        </div>

      </div>
    </div>
  );
};

export default AddTransaction;
