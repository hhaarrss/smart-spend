import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { transactionService } from '../services/api';
import { 
  PlusCircle, Sparkles, MessageSquare, Landmark,
  DollarSign, Tag, Calendar, User,
  CheckCircle2, AlertCircle, Loader2
} from 'lucide-react';

/**
 * Add Transaction Protected Page matching light fintech aesthetic.
 */
const AddTransaction = () => {
  const navigate = useNavigate();

  // Form states
  const [amount, setAmount] = useState('');
  const [type, setType] = useState('debit');
  const [category, setCategory] = useState('Food');
  const [merchant, setMerchant] = useState('');
  const [date, setDate] = useState('');
  
  const [bank, setBank] = useState('');
  const [accountLast4, setAccountLast4] = useState('');

  const [loading, setLoading] = useState(false);
  const [successMsg, setSuccessMsg] = useState('');
  const [errorMsg, setErrorMsg] = useState('');

  // SMS Parsing states
  const [rawSMS, setRawSMS] = useState('');
  const [sender, setSender] = useState('HDFCBK');
  const [parsing, setParsing] = useState(false);
  const [parseFeedback, setParseFeedback] = useState({ type: '', msg: '' });

  const CATEGORIES = [
    'Food', 'Travel', 'Shopping', 'Utilities', 'Entertainment',
    'Healthcare', 'Education', 'Fuel', 'Groceries', 'Other'
  ];

  useEffect(() => {
    const today = new Date().toISOString().substring(0, 10);
    setDate(today);
  }, []);

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
        const tx = data.transaction;
        setAmount(tx.amount);
        setType(tx.type);
        setCategory(tx.category);
        setMerchant(tx.merchant || '');
        setBank(tx.bank || '');
        setAccountLast4(tx.account_last4 || '');
        
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
    <div className="space-y-8 font-sans">
      
      {/* Title */}
      <div>
        <h1 className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tight flex items-center gap-2.5">
          <PlusCircle className="w-7 h-7 text-[#16803C]" />
          Log Transaction
        </h1>
        <p className="text-xs sm:text-sm text-slate-500 font-medium mt-1">Manual logging or automated bank SMS text ingestion</p>
      </div>

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
        
        {/* Manual Form (Panel 1) */}
        <div className="bg-white border border-slate-200 p-6 sm:p-8 rounded-2xl shadow-xs space-y-6">
          <div>
            <h3 className="text-base font-extrabold text-slate-900">Transaction Details</h3>
            <p className="text-xs text-slate-500 mt-0.5">Complete transaction fields below to manually log</p>
          </div>

          {errorMsg && (
            <div className="flex items-center gap-2 p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-700 font-medium">
              <AlertCircle className="w-4 h-4 flex-shrink-0" />
              <span>{errorMsg}</span>
            </div>
          )}

          {successMsg && (
            <div className="flex items-center gap-2 p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-[#16803C] font-semibold">
              <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
              <span>{successMsg}</span>
            </div>
          )}

          <form onSubmit={handleFormSubmit} className="space-y-5">
            {/* Amount & Type */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <div>
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-2">Amount (INR)</label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                    <DollarSign className="w-4 h-4" />
                  </div>
                  <input
                    type="number"
                    step="0.01"
                    required
                    value={amount}
                    onChange={(e) => setAmount(e.target.value)}
                    placeholder="250.00"
                    className="block w-full pl-10 pr-3.5 py-3 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 placeholder-slate-400 focus:outline-none focus:border-[#16803C] focus:bg-white transition-all text-xs font-bold"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-2">Type</label>
                <div className="flex bg-slate-100 p-1 rounded-xl border border-slate-200">
                  <button
                    type="button"
                    onClick={() => setType('debit')}
                    className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all cursor-pointer ${
                      type === 'debit' 
                        ? 'bg-white text-[#EF4444] shadow-xs border border-rose-200' 
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    Debit (Expense)
                  </button>
                  <button
                    type="button"
                    onClick={() => setType('credit')}
                    className={`flex-1 py-2 text-xs font-bold rounded-lg transition-all cursor-pointer ${
                      type === 'credit' 
                        ? 'bg-white text-[#16803C] shadow-xs border border-emerald-200' 
                        : 'text-slate-600 hover:text-slate-900'
                    }`}
                  >
                    Credit (Income)
                  </button>
                </div>
              </div>
            </div>

            {/* Category & Date */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-5">
              <div>
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-2">Category</label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                    <Tag className="w-4 h-4" />
                  </div>
                  <select
                    value={category}
                    onChange={(e) => setCategory(e.target.value)}
                    className="block w-full pl-10 pr-3.5 py-3 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 focus:outline-none focus:border-[#16803C] focus:bg-white transition-all text-xs font-semibold capitalize appearance-none cursor-pointer"
                  >
                    {CATEGORIES.map(cat => (
                      <option key={cat} value={cat} className="capitalize">{cat}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div>
                <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-2">Transaction Date</label>
                <div className="relative">
                  <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                    <Calendar className="w-4 h-4" />
                  </div>
                  <input
                    type="date"
                    required
                    value={date}
                    onChange={(e) => setDate(e.target.value)}
                    className="block w-full pl-10 pr-3.5 py-3 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 focus:outline-none focus:border-[#16803C] focus:bg-white transition-all text-xs font-mono font-semibold"
                  />
                </div>
              </div>
            </div>

            {/* Merchant */}
            <div>
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-2">Merchant Name</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                  <User className="w-4 h-4" />
                </div>
                <input
                  type="text"
                  value={merchant}
                  onChange={(e) => setMerchant(e.target.value)}
                  placeholder="Swiggy, Amazon, Airtel"
                  className="block w-full pl-10 pr-3.5 py-3 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 placeholder-slate-400 focus:outline-none focus:border-[#16803C] focus:bg-white transition-all text-xs font-semibold"
                />
              </div>
            </div>

            {/* Bank details info */}
            {(bank || accountLast4) && (
              <div className="p-3.5 rounded-xl bg-[#EAF7EF] border border-emerald-200 text-xs space-y-1">
                <span className="font-bold text-[#16803C] flex items-center gap-1.5">
                  <Landmark className="w-3.5 h-3.5" />
                  Bank metadata auto-extracted from SMS:
                </span>
                <div className="grid grid-cols-2 gap-4 text-slate-600 font-mono text-[11px]">
                  <div>Bank: <span className="font-bold text-slate-900">{bank}</span></div>
                  <div>Account: <span className="font-bold text-slate-900">*{accountLast4}</span></div>
                </div>
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3.5 px-4 rounded-xl font-bold text-white bg-[#16803C] hover:bg-[#136e33] shadow-xs flex items-center justify-center gap-2 cursor-pointer transition-all disabled:opacity-50"
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

        {/* SMS Parser (Panel 2) */}
        <div className="bg-white border border-slate-200 p-6 sm:p-8 rounded-2xl shadow-xs flex flex-col justify-between space-y-6">
          <div>
            <div className="flex items-center gap-2 mb-0.5">
              <h3 className="text-base font-extrabold text-slate-900">AI SMS Auto-Ingest</h3>
              <Sparkles className="w-4 h-4 text-[#16803C]" />
            </div>
            <p className="text-xs text-slate-500 font-medium">Paste transactional messages from Indian banks to parse immediately</p>
          </div>

          {parseFeedback.msg && (
            <div className={`flex items-center gap-2 p-3.5 rounded-xl text-xs font-semibold ${
              parseFeedback.type === 'success' 
                ? 'bg-emerald-50 border border-emerald-200 text-[#16803C]' 
                : 'bg-rose-50 border border-rose-200 text-rose-700'
            }`}>
              {parseFeedback.type === 'success' ? (
                <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
              ) : (
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
              )}
              <span>{parseFeedback.msg}</span>
            </div>
          )}

          <div className="space-y-5 flex-grow flex flex-col justify-between">
            <div>
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-2">Sender Code / Bank Keyword</label>
              <div className="relative">
                <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                  <Landmark className="w-4 h-4" />
                </div>
                <input
                  type="text"
                  value={sender}
                  onChange={(e) => setSender(e.target.value)}
                  placeholder="HDFCBK, SBI, ICICI"
                  className="block w-full pl-10 pr-3.5 py-3 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 placeholder-slate-400 focus:outline-none focus:border-[#16803C] focus:bg-white transition-all text-xs font-mono font-bold uppercase"
                />
              </div>
            </div>

            <div className="flex-grow flex flex-col">
              <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-2">Paste raw SMS content</label>
              <div className="relative flex-grow">
                <div className="absolute top-3.5 left-3.5 pointer-events-none text-slate-400">
                  <MessageSquare className="w-4 h-4" />
                </div>
                <textarea
                  value={rawSMS}
                  onChange={(e) => setRawSMS(e.target.value)}
                  rows="6"
                  placeholder="e.g. Alert: You've made a payment of Rs. 150.00 to Swiggy using HDFC Bank Card ending 1234 on 26-05-26."
                  className="block w-full pl-10 pr-3.5 py-3.5 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 placeholder-slate-400 focus:outline-none focus:border-[#16803C] focus:bg-white transition-all text-xs font-mono resize-none h-full"
                />
              </div>
            </div>

            <button
              type="button"
              onClick={handleSMSParse}
              disabled={parsing}
              className="w-full py-3.5 px-4 rounded-xl font-bold text-[#16803C] bg-[#EAF7EF] border border-emerald-200 hover:bg-[#16803C] hover:text-white shadow-xs flex items-center justify-center gap-2 cursor-pointer transition-all disabled:opacity-50"
            >
              {parsing ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin text-[#16803C]" />
                  <span>Parsing SMS parameters...</span>
                </>
              ) : (
                <>
                  <Sparkles className="w-4 h-4 text-[#16803C]" />
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
