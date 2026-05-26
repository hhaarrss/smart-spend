import React, { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { LayoutDashboard, PlusCircle, Wallet, LogOut, Menu, X, Landmark, UserCircle, LineChart } from 'lucide-react';

/**
 * Mobile-responsive navigation header with glassmorphism styling.
 */
const Navbar = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [isOpen, setIsOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const navLinks = [
    { to: '/', label: 'Dashboard', icon: <LayoutDashboard className="w-4 h-4" /> },
    { to: '/add', label: 'Add Transaction', icon: <PlusCircle className="w-4 h-4" /> },
    { to: '/budget', label: 'Budget limits', icon: <Wallet className="w-4 h-4" /> },
    { to: '/insights', label: 'Insights', icon: <LineChart className="w-4 h-4" /> },
  ];

  const activeClass = "flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-semibold text-white bg-indigo-600/25 border border-indigo-500/30 shadow-[0_0_15px_rgba(79,70,229,0.15)] transition-all";
  const inactiveClass = "flex items-center gap-2 px-4 py-2 rounded-xl text-sm font-medium text-slate-400 border border-transparent hover:text-slate-200 hover:bg-slate-800/50 hover:border-slate-700/50 transition-all";

  return (
    <nav className="sticky top-0 z-50 bg-slate-950/80 backdrop-blur-lg border-b border-slate-900 shadow-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Logo */}
          <div className="flex items-center gap-2 cursor-pointer" onClick={() => navigate('/')}>
            <div className="p-2 bg-gradient-to-tr from-indigo-600 to-violet-500 rounded-xl text-white shadow-lg shadow-indigo-550/20">
              <Landmark className="w-5 h-5" />
            </div>
            <span className="text-base font-bold bg-gradient-to-r from-white via-slate-100 to-indigo-400 bg-clip-text text-transparent">
              SmartSpend
            </span>
          </div>

          {/* Desktop Navigation Links */}
          <div className="hidden md:flex items-center gap-2">
            {navLinks.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                className={({ isActive }) => (isActive ? activeClass : inactiveClass)}
              >
                {link.icon}
                <span>{link.label}</span>
              </NavLink>
            ))}
          </div>

          {/* User Section (Desktop) */}
          <div className="hidden md:flex items-center gap-4">
            {user && (
              <div className="flex items-center gap-2 bg-slate-900/60 pl-3 pr-2.5 py-1.5 rounded-full border border-slate-800">
                <span className="text-xs font-semibold text-slate-300 font-sans">{user.full_name}</span>
                <div className="w-6 h-6 rounded-full bg-indigo-500/20 border border-indigo-500/40 text-indigo-400 flex items-center justify-center font-bold text-xs uppercase">
                  {user.full_name ? user.full_name[0] : <UserCircle className="w-4 h-4" />}
                </div>
              </div>
            )}
            
            <button
              onClick={handleLogout}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold text-rose-400 border border-rose-500/20 bg-rose-500/5 hover:bg-rose-500/10 hover:border-rose-500/30 transition-all cursor-pointer"
            >
              <LogOut className="w-3.5 h-3.5" />
              <span>Log out</span>
            </button>
          </div>

          {/* Mobile menu trigger */}
          <div className="md:hidden">
            <button
              onClick={() => setIsOpen(!isOpen)}
              className="p-2 rounded-xl text-slate-400 hover:text-white hover:bg-slate-900 border border-slate-800"
            >
              {isOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
            </button>
          </div>
        </div>
      </div>

      {/* Mobile Drawer menu */}
      {isOpen && (
        <div className="md:hidden border-t border-slate-900 bg-slate-950/95 backdrop-blur-lg px-4 pt-3 pb-4 space-y-3">
          <div className="flex flex-col gap-2">
            {navLinks.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                onClick={() => setIsOpen(false)}
                className={({ isActive }) => (isActive ? activeClass : inactiveClass)}
              >
                {link.icon}
                <span>{link.label}</span>
              </NavLink>
            ))}
          </div>

          {/* User profile (Mobile Drawer) */}
          <div className="pt-4 border-t border-slate-900 flex items-center justify-between">
            {user && (
              <div className="flex items-center gap-2.5">
                <div className="w-8 h-8 rounded-full bg-indigo-500/20 border border-indigo-500/40 text-indigo-400 flex items-center justify-center font-bold text-sm uppercase">
                  {user.full_name ? user.full_name[0] : <UserCircle className="w-4 h-4" />}
                </div>
                <div>
                  <h4 className="text-sm font-semibold text-white">{user.full_name}</h4>
                  <p className="text-[10px] text-slate-500 font-mono">{user.email}</p>
                </div>
              </div>
            )}
            
            <button
              onClick={handleLogout}
              className="flex items-center gap-1.5 px-3 py-2 rounded-xl text-xs font-semibold text-rose-400 border border-rose-500/20 bg-rose-500/5 hover:bg-rose-500/10 hover:border-rose-500/30 transition-all cursor-pointer"
            >
              <LogOut className="w-3.5 h-3.5" />
              <span>Log out</span>
            </button>
          </div>
        </div>
      )}
    </nav>
  );
};

export default Navbar;
