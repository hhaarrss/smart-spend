import React, { useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { 
  LayoutGrid, PlusCircle, Wallet, LogOut, Menu, X, 
  Landmark, User, BarChart2, Bell, Crown, ChevronDown
} from 'lucide-react';

/**
 * Mobile-responsive Sidebar and Header navigation matching light fintech design system.
 */
const Navbar = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const navLinks = [
    { to: '/', label: 'Dashboard', icon: <LayoutGrid className="w-4 h-4" /> },
    { to: '/add', label: 'Add Transaction', icon: <PlusCircle className="w-4 h-4" /> },
    { to: '/budget', label: 'Budget Limits', icon: <Wallet className="w-4 h-4" /> },
    { to: '/insights', label: 'Insights', icon: <BarChart2 className="w-4 h-4" /> },
  ];

  const activeClass = "flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-bold text-[#16803C] bg-[#EAF7EF] transition-all";
  const inactiveClass = "flex items-center gap-3 px-3.5 py-2.5 rounded-xl text-sm font-medium text-slate-600 hover:text-slate-900 hover:bg-slate-100 transition-all";

  return (
    <>
      {/* Desktop / Tablet Left Sidebar (Hidden on Mobile) */}
      <aside className="hidden md:flex flex-col fixed top-0 left-0 bottom-0 w-64 bg-[#FAFAF8] border-r border-slate-200/90 z-30 p-5 justify-between">
        <div className="space-y-8">
          {/* Brand Logo */}
          <div className="flex items-center gap-3 cursor-pointer pl-1" onClick={() => navigate('/')}>
            <div className="p-2.5 bg-[#16803C] rounded-xl text-white shadow-sm flex items-center justify-center">
              <Landmark className="w-5 h-5" />
            </div>
            <span className="text-xl font-extrabold text-slate-900 tracking-tight">
              SmartSpend
            </span>
          </div>

          {/* Navigation Items */}
          <nav className="space-y-1.5">
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
          </nav>
        </div>

        {/* Bottom Section: Upgrade CTA Box & User Profile */}
        <div className="space-y-4">
          {/* Upgrade Banner Card */}
          <div className="p-4 rounded-2xl bg-[#EAF7EF]/60 border border-emerald-200/70 space-y-2.5">
            <div className="flex items-center gap-2">
              <div className="p-1.5 bg-[#16803C]/10 text-[#16803C] rounded-lg">
                <Crown className="w-4 h-4" />
              </div>
              <h4 className="text-xs font-bold text-slate-900">Take control of your finances</h4>
            </div>
            <p className="text-[11px] text-slate-600 leading-snug">
              Set budgets, track spending and achieve your goals.
            </p>
            <button 
              type="button"
              className="w-full py-2.5 px-3 rounded-xl bg-[#16803C] hover:bg-[#136e33] text-white font-bold text-xs shadow-sm transition-all cursor-pointer"
            >
              Upgrade Now
            </button>
          </div>

          {/* User Profile Pill at bottom */}
          <div className="flex items-center justify-between pt-2 border-t border-slate-200">
            <div className="flex items-center gap-2.5 min-w-0">
              <div className="w-9 h-9 rounded-full bg-[#16803C] text-white flex items-center justify-center font-bold text-sm flex-shrink-0">
                {user?.full_name ? user.full_name[0].toUpperCase() : 'T'}
              </div>
              <div className="min-w-0">
                <h4 className="text-xs font-bold text-slate-900 truncate">{user?.full_name || 'Test User'}</h4>
                <p className="text-[10px] text-slate-500 truncate">{user?.email || 'testuser@gmail.com'}</p>
              </div>
            </div>
            <ChevronDown className="w-4 h-4 text-slate-400 flex-shrink-0 cursor-pointer" />
          </div>
        </div>
      </aside>

      {/* Mobile Top Header (Visible only on Mobile screens < md) */}
      <div className="md:hidden sticky top-0 z-40 bg-white border-b border-slate-200 px-4 py-3 flex items-center justify-between">
        <div className="flex items-center gap-2.5 cursor-pointer" onClick={() => navigate('/')}>
          <div className="p-2 bg-[#16803C] rounded-xl text-white shadow-sm">
            <Landmark className="w-4 h-4" />
          </div>
          <span className="text-lg font-extrabold text-slate-900">SmartSpend</span>
        </div>

        <button
          onClick={() => setMobileOpen(!mobileOpen)}
          className="p-2 rounded-xl text-slate-600 hover:bg-slate-100 border border-slate-200"
        >
          {mobileOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
        </button>
      </div>

      {/* Mobile Menu Drawer */}
      {mobileOpen && (
        <div className="md:hidden fixed inset-x-0 top-[57px] z-40 bg-white border-b border-slate-200 p-4 space-y-3 shadow-lg">
          <nav className="space-y-1">
            {navLinks.map((link) => (
              <NavLink
                key={link.to}
                to={link.to}
                onClick={() => setMobileOpen(false)}
                className={({ isActive }) => (isActive ? activeClass : inactiveClass)}
              >
                {link.icon}
                <span>{link.label}</span>
              </NavLink>
            ))}
          </nav>

          <div className="pt-3 border-t border-slate-200 flex items-center justify-between">
            <div className="flex items-center gap-2">
              <div className="w-7 h-7 rounded-full bg-[#16803C] text-white flex items-center justify-center font-bold text-xs">
                {user?.full_name ? user.full_name[0].toUpperCase() : 'T'}
              </div>
              <span className="text-xs font-bold text-slate-900">{user?.full_name || 'Test User'}</span>
            </div>
            
            <button
              onClick={handleLogout}
              className="flex items-center gap-1 px-3 py-1.5 rounded-lg text-xs font-semibold text-rose-600 border border-rose-200 bg-rose-50"
            >
              <LogOut className="w-3.5 h-3.5" />
              <span>Logout</span>
            </button>
          </div>
        </div>
      )}
    </>
  );
};

export default Navbar;
