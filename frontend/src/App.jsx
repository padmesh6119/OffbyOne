import { HashRouter as BrowserRouter, Routes, Route, Link, Navigate } from 'react-router-dom';
import Join from './pages/Join';
import Problems from './pages/Problems';
import ProblemDetail from './pages/ProblemDetail';
import Room from './pages/Room';
import SqlPractice from './pages/SqlPractice';
import Profile from './pages/Profile';
import './index.css';

function Nav() {
  const username = localStorage.getItem('username');
  return (
    <nav>
      <Link to="/">OffByOne</Link>
      <Link to="/sql">SQL Duels</Link>
      <Link to="/problems">Practice</Link>
      <Link to="/profile">Profile</Link>
      {username && <span className="nav-user">👤 {username}</span>}
      <button onClick={() => { localStorage.clear(); location.href = '/'; }}>Leave</button>
    </nav>
  );
}

function Guard({ children }) {
  return localStorage.getItem('token') ? children : <Navigate to="/join" />;
}

export default function App() {
  return (
    <BrowserRouter>
      <Nav />
      <Routes>
        <Route path="/" element={<Guard><Room /></Guard>} />
        <Route path="/join" element={<Join />} />
        <Route path="/sql" element={<Guard><SqlPractice /></Guard>} />
        <Route path="/problems" element={<Guard><Problems /></Guard>} />
        <Route path="/problem/:slug" element={<Guard><ProblemDetail /></Guard>} />
        <Route path="/profile" element={<Guard><Profile /></Guard>} />
      </Routes>
    </BrowserRouter>
  );
}
