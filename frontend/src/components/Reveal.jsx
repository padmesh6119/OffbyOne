import { useEffect, useRef, useState } from 'react';

// Content stays visible without observers; motion never controls access to it.
export default function Reveal({ as: Tag = 'div', children, className = '', delay = 0, ...props }) {
  const ref = useRef(null);
  const [entered, setEntered] = useState(false);
  useEffect(() => {
    if (!('IntersectionObserver' in window)) return;
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) {
        setEntered(true);
        observer.disconnect();
      }
    }, { threshold: 0.08 });
    observer.observe(ref.current);
    return () => observer.disconnect();
  }, []);
  return <Tag ref={ref} {...props} className={`${className} reveal ${entered ? 'reveal-entered' : ''}`} style={{ '--reveal-delay': `${delay}ms` }}>{children}</Tag>;
}
