import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import Navbar from './components/Navbar';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import AddTransaction from './pages/AddTransaction';
import Budget from './pages/Budget';
import Insights from './pages/Insights';
import { Calendar, Bell, LogOut } from 'lucide-react';

/**
 * Header Top Bar component inside main content area.
 */
const TopHeader = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const now = new Date();
  const monthName = now.toLocaleString('default', { month: 'long', year: 'numeric' });

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4 mb-8">
      {/* Left: Greeting & Active Tracking */}
      <div>
        <h1 className="text-2xl sm:text-3xl font-black text-slate-900 tracking-tight flex items-center gap-2">
          <span>Hi, {user?.full_name || 'Test User'}</span>
          <span>👋</span>
        </h1>
        <p className="text-xs sm:text-sm font-semibold text-slate-500 mt-1 flex items-center gap-1.5">
          <Calendar className="w-4 h-4 text-[#16803C]" />
          <span>Active tracking for:</span>
          <span className="text-[#16803C] font-extrabold">{monthName}</span>
        </p>
      </div>

      {/* Right: Sync Badge, Notifications, Logout */}
      <div className="flex items-center gap-3">
        {/* Live Backend Synchronized Pill */}
        <div className="flex items-center gap-2 bg-white border border-slate-200 px-3.5 py-1.5 rounded-full shadow-xs">
          <div className="w-2.5 h-2.5 bg-[#22A447] rounded-full animate-pulse" />
          <span className="text-xs font-bold text-slate-600">Live Backend Synchronized</span>
        </div>

        {/* Bell Icon Button */}
        <button 
          type="button"
          aria-label="Notifications"
          className="p-2 rounded-full bg-white border border-slate-200 text-slate-600 hover:text-slate-900 hover:bg-slate-50 shadow-xs transition-all cursor-pointer"
        >
          <Bell className="w-4 h-4" />
        </button>

        {/* Logout Button */}
        <button
          onClick={handleLogout}
          className="flex items-center gap-1.5 px-3.5 py-1.5 rounded-xl text-xs font-bold text-[#EF4444] border border-rose-200 bg-white hover:bg-rose-50 shadow-xs transition-all cursor-pointer"
        >
          <LogOut className="w-3.5 h-3.5" />
          <span>Log out</span>
        </button>
      </div>
    </div>
  );
};

/**
 * Global authenticated page layout structure.
 */
const Layout = ({ children }) => {
  return (
    <div className="min-h-screen bg-[#FAFAF8] text-slate-900 flex font-sans antialiased">
      <Navbar />
      <main className="flex-1 md:ml-64 p-4 sm:p-6 lg:p-8 min-h-screen">
        <TopHeader />
        {children}
      </main>
    </div>
  );
};

/**
 * Root Application Router entry.
 */
function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          {/* Public Auth routes */}
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />

          {/* Protected routes */}
          <Route 
            path="/" 
            element={
              <ProtectedRoute>
                <Layout>
                  <Dashboard />
                </Layout>
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/add" 
            element={
              <ProtectedRoute>
                <Layout>
                  <AddTransaction />
                </Layout>
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/budget" 
            element={
              <ProtectedRoute>
                <Layout>
                  <Budget />
                </Layout>
              </ProtectedRoute>
            } 
          />
          <Route 
            path="/insights" 
            element={
              <ProtectedRoute>
                <Layout>
                  <Insights />
                </Layout>
              </ProtectedRoute>
            } 
          />

          {/* Global Fallback Route */}
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  );
}

export default App;
