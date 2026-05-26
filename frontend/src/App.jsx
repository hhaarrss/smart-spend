import React from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/ProtectedRoute';
import Navbar from './components/Navbar';
import Login from './pages/Login';
import Register from './pages/Register';
import Dashboard from './pages/Dashboard';
import AddTransaction from './pages/AddTransaction';
import Budget from './pages/Budget';
import Insights from './pages/Insights';

/**
 * Global authenticated page layout structure.
 */
const Layout = ({ children }) => {
  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans antialiased">
      <Navbar />
      <main className="flex-grow">
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
