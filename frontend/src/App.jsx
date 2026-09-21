import { BrowserRouter, Routes, Route, Link, Navigate } from 'react-router-dom';
import Join from './pages/Join';
import Problems from './pages/Problems';
import ProblemDetail from './pages/ProblemDetail';
import Room from './pages/Room';
import './index.css';

function Nav() {
  const username = localStorage.getItem('username');
  return (
    <nav>
      <Link to="/">OffByOne</Link>
      <Link to="/problems">Problems</Link>
      <Link to="/room">Room</Link>
      {username && <span className="nav-user">👤 {username}</span>}
      <button onClick={() => { localStorage.clear(); location.reload(); }}>Leave</button>
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
        <Route path="/" element={<Navigate to="/problems" />} />
        <Route path="/join" element={<Join />} />
        <Route path="/problems" element={<Guard><Problems /></Guard>} />
        <Route path="/problem/:slug" element={<Guard><ProblemDetail /></Guard>} />
        <Route path="/room" element={<Guard><Room /></Guard>} />
      </Routes>
    </BrowserRouter>
  );
}
