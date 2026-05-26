import React, { createContext, useContext, useState, useEffect } from 'react';
import { authService, setInMemoryToken } from '../services/api';

const AuthContext = createContext(null);

/**
 * Custom hook to consume the AuthContext session.
 */
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};

/**
 * Provider component to wrap the React application tree.
 * Retains JWT state in-memory only.
 */
export const AuthProvider = ({ children }) => {
  const mockToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ0ZXN0QGV4YW1wbGUuY29tIiwidXNlcl9pZCI6MSwiZXhwIjoxNzc5Nzg0MDkxfQ.is4ui-kAEC2y5OvUqMVgTazOTzqhbfjPc40Dx2n6OFQ';
  const mockUser = { id: 1, email: "test@example.com", full_name: "Mock Test User" };

  const [token, setToken] = useState(mockToken);
  const [user, setUser] = useState(mockUser);
  const [loading, setLoading] = useState(false);
  const [authError, setAuthError] = useState(null);

  useEffect(() => {
    setInMemoryToken(mockToken);
  }, []);

  /**
   * Log in user and fetch current profile details.
   */
  const login = async (email, password) => {
    setLoading(true);
    setAuthError(null);
    try {
      const data = await authService.login(email, password);
      const accessToken = data.access_token;
      
      // Store in memory state
      setToken(accessToken);
      setInMemoryToken(accessToken);

      // Load user profile details
      const profile = await authService.getMe();
      setUser(profile);
      setLoading(false);
      return profile;
    } catch (err) {
      setLoading(false);
      const msg = err.response?.data?.detail || 'Incorrect email or password';
      setAuthError(msg);
      throw new Error(msg);
    }
  };

  /**
   * Log out user, wiping memory variables.
   */
  const logout = () => {
    setToken(null);
    setUser(null);
    setInMemoryToken(null);
  };

  const value = {
    token,
    user,
    loading,
    authError,
    isAuthenticated: !!token,
    login,
    logout,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};
