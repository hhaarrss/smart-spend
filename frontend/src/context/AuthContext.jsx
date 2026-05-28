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
 * Retains JWT state in localStorage to persist across refreshes.
 */
export const AuthProvider = ({ children }) => {
  const [token, setToken] = useState(() => {
    const savedToken = localStorage.getItem('jwt_token');
    if (savedToken) {
      setInMemoryToken(savedToken);
      return savedToken;
    }
    return null;
  });

  const [user, setUser] = useState(() => {
    const savedUser = localStorage.getItem('user');
    return savedUser ? JSON.parse(savedUser) : null;
  });

  const [loading, setLoading] = useState(false);
  const [authError, setAuthError] = useState(null);

  // Verify stored session on mount
  useEffect(() => {
    const verifySession = async () => {
      if (token) {
        try {
          const profile = await authService.getMe();
          setUser(profile);
          localStorage.setItem('user', JSON.stringify(profile));
        } catch (err) {
          // Stored token is invalid or expired
          logout();
        }
      }
    };
    verifySession();
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
      // Store in memory state immediately before other state updates
      setInMemoryToken(accessToken);
      setToken(accessToken);
      localStorage.setItem('jwt_token', accessToken);

      // Load user profile details
      const profile = await authService.getMe();
      setUser(profile);
      localStorage.setItem('user', JSON.stringify(profile));
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
   * Log out user, wiping memory variables and localStorage.
   */
  const logout = () => {
    setToken(null);
    setUser(null);
    setInMemoryToken(null);
    localStorage.removeItem('jwt_token');
    localStorage.removeItem('user');
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
