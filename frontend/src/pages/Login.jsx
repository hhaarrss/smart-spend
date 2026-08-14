import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { Landmark, Mail, Lock, AlertCircle, Loader } from 'lucide-react';

/**
 * Authentication Login Page matching light fintech design system.
 */
const Login = () => {
  const { login } = useAuth();
  const navigate = useNavigate();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!email || !password) {
      setError('Please provide all credentials');
      return;
    }

    setLoading(true);
    setError('');

    try {
      await login(email, password);
      setLoading(false);
      navigate('/');
    } catch (err) {
      setLoading(false);
      setError(err.message || 'Login failed. Please check your credentials.');
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

      {/* Login Card */}
      <div className="w-full max-w-md bg-white border border-slate-200 rounded-2xl p-8 shadow-xs relative overflow-hidden">
        <div className="absolute top-0 left-0 w-full h-1 bg-[#16803C]" />
        
        <h2 className="text-xl font-black text-slate-900 mb-1">Welcome back</h2>
        <p className="text-xs text-slate-500 font-medium mb-6">Enter your credentials to access your financial tracker</p>

        {/* Error Feedback */}
        {error && (
          <div className="flex items-center gap-2 p-3 bg-rose-50 border border-rose-200 rounded-xl text-xs text-rose-700 font-medium mb-5">
            <AlertCircle className="w-4 h-4 flex-shrink-0" />
            <span>{error}</span>
          </div>
        )}

        <form onSubmit={handleSubmit} className="space-y-5">
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
                <span>Logging in...</span>
              </>
            ) : (
              <span>Sign In</span>
            )}
          </button>
        </form>

        {/* Link to Register */}
        <p className="text-center text-xs text-slate-500 font-medium mt-6">
          Don't have an account?{' '}
          <Link to="/register" className="font-bold text-[#16803C] hover:underline">
            Sign up
          </Link>
        </p>
      </div>
    </div>
  );
};

export default Login;
