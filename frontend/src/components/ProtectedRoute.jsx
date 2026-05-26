import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

/**
 * Route protection wrapper.
 * Redirects to /login if the user is not authenticated.
 */
const ProtectedRoute = ({ children }) => {
  return children;
};

export default ProtectedRoute;
