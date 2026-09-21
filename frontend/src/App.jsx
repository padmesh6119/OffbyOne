import { BrowserRouter, Routes, Route, Link, Navigate } from 'react-router-dom';
import Login from './pages/Login';
import Register from './pages/Register';
import Problems from './pages/Problems';
import ProblemDetail from './pages/ProblemDetail';
import Room from './pages/Room';
import './index.css';

function Nav() {
  const token = localStorage.getItem('token');
  return (
    <nav>
      <Link to="/">OffByOne</Link>
      <Link to="/problems">Problems</Link>
      <Link to="/room">Room</Link>
      {token
        ? <button onClick={() => { localStorage.removeItem('token'); location.reload(); }}>Logout</button>
        : <Link to="/login">Login</Link>}
    </nav>
  );
}

function Guard({ children }) {
  return localStorage.getItem('token') ? children : <Navigate to="/login" />;
}

export default function App() {
  return (
    <BrowserRouter>
      <Nav />
      <Routes>
        <Route path="/" element={<Navigate to="/problems" />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/problems" element={<Guard><Problems /></Guard>} />
        <Route path="/problem/:slug" element={<Guard><ProblemDetail /></Guard>} />
        <Route path="/room" element={<Guard><Room /></Guard>} />
      </Routes>
    </BrowserRouter>
  );
}
