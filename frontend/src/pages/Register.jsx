import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { authService } from '../services/api';
import { Landmark, Mail, Lock, User, AlertCircle, Loader, CheckCircle2 } from 'lucide-react';

/**
 * Authentication Register Page matching light fintech design system.
 */
const Register = () => {
  const navigate = useNavigate();

  const [fullName, setFullName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!fullName || !email || !password) {
      setError('Please provide all details');
      return;
    }

    setLoading(true);
    setError('');
    setSuccess('');

    try {
      await authService.register(fullName, email, password);
      setSuccess('Account created successfully! Redirecting to login...');
      setLoading(false);
      
      setTimeout(() => {
        navigate('/login');
      }, 2000);
    } catch (err) {
      setLoading(false);
      setError(err.response?.data?.detail || 'Registration failed. Email might already exist.');
    }
  };

  return (
    <div className="min-h-screen bg-[#FAFAF8] flex flex-col justify-center items-center px-4 sm:px-6 lg:px-8 font-sans">
      {/* Brand Header */}
      <div className="flex items-center gap-3 mb-8">
        <div className="p-3 bg-[#16803C] rounded-2xl text-white shadow-sm">
          <Landmark className="w-6 h-6" />
        </div>
        <span className="text-2xl font-black text-slate-900 tracking-tight">
          SmartSpend
        </span>
      </div>

      {/* Register Card */}
      <div className="w-full max-w-md bg-white border border-slate-200 rounded-2xl p-8 shadow-xs relative overflow-hidden">
        <div className="absolute top-0 left-0 w-full h-1 bg-[#16803C]" />
        
        <h2 className="text-xl font-black text-slate-900 mb-1">Create an account</h2>
        <p className="text-xs text-slate-500 font-medium mb-6">Join us and start tracking your spendings</p>

        {/* Feedback alerts */}
        {error && (
          <div className="flex items-center gap-2 p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-700 font-medium mb-5">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        {success && (
          <div className="flex items-center gap-2 p-3 bg-emerald-50 border border-emerald-200 rounded-xl text-xs text-[#16803C] font-semibold mb-5">
            <CheckCircle2 className="w-4 h-4 flex-shrink-0" />
            <span>{success}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
          {/* Full Name input */}
          <div>
            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-2">Full Name</label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                <User className="w-4 h-4" />
              </div>
              <input
                type="text"
                required
                value={fullName}
                onChange={(e) => setFullName(e.target.value)}
                placeholder="John Doe"
                className="block w-full pl-10 pr-3.5 py-3 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 placeholder-slate-400 focus:outline-none focus:border-[#16803C] focus:bg-white transition-all text-xs font-semibold"
              />
            </div>
          </div>

          {/* Email input */}
          <div>
            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-2">Email Address</label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                <Mail className="w-4 h-4" />
              </div>
              <input
                type="email"
                required
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                className="block w-full pl-10 pr-3.5 py-3 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 placeholder-slate-400 focus:outline-none focus:border-[#16803C] focus:bg-white transition-all text-xs font-semibold"
              />
            </div>
          </div>

          {/* Password input */}
          <div>
            <label className="block text-xs font-bold text-slate-600 uppercase tracking-wider mb-2">Password</label>
            <div className="relative">
              <div className="absolute inset-y-0 left-0 pl-3.5 flex items-center pointer-events-none text-slate-400">
                <Lock className="w-4 h-4" />
              </div>
              <input
                type="password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                className="block w-full pl-10 pr-3.5 py-3 rounded-xl bg-slate-50 border border-slate-200 text-slate-900 placeholder-slate-400 focus:outline-none focus:border-[#16803C] focus:bg-white transition-all text-xs font-semibold"
              />
            </div>
          </div>

          {/* Submit Button */}
          <button
            type="submit"
            disabled={loading}
            className="w-full py-3.5 px-4 rounded-xl font-bold text-white bg-[#16803C] hover:bg-[#136e33] shadow-xs flex items-center justify-center gap-2 cursor-pointer transition-all disabled:opacity-50"
          >
            {loading ? (
              <>
                <Loader className="w-4 h-4 animate-spin" />
                <span>Registering...</span>
              </>
            ) : (
              <span>Register Now</span>
            )}
          </button>
        </form>

        {/* Link to Login */}
        <p className="text-center text-xs text-slate-500 font-medium mt-6">
          Already have an account?{' '}
          <Link to="/login" className="font-bold text-[#16803C] hover:underline">
            Sign in
          </Link>
        </p>
      </div>
    </div>
  );
};

export default Register;
