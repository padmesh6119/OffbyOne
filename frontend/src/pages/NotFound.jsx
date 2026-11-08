import { Link } from 'react-router-dom';
export default function NotFound() {
  return <section className="not-found"><span className="arena-eyebrow">404 / NO ROWS FOUND</span><h1>This page came<br/>back empty.</h1><p>The link may be outdated or the address may be mistyped. Your saved drafts are still in this browser.</p><div><Link className="arena-primary" to="/">Back to the arena →</Link><Link className="arena-secondary" to="/help">Get help</Link></div></section>;
}
