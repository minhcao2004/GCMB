import React from 'react';
import ReactDOM from 'react-dom/client';
import './assets/styles/tokens.css';
import './assets/styles/landing.css';
import './shared/styles/shared-ui.css';
import App from './App';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
