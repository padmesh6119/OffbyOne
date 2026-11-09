import { useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';

export default function CreatorSecret() {
  const navigate = useNavigate(), location = useLocation();
  const taps = useRef({ count: 0, time: 0 });
  const [step, setStep] = useState(0);
  function discover() {
    const now = Date.now();
    const count = now - taps.current.time < 1800 ? taps.current.count + 1 : 1;
    taps.current = { count, time: now };
    setStep(count);
    if (count === 3) {
      taps.current = { count: 0, time: 0 }; setStep(0);
      navigate('/creators', { state: { from: location.pathname + location.search } });
    }
  }
  return <button className="creator-secret" data-step={step} onClick={discover}
    aria-label="Discover the creators. Activate three times." title="Three taps. Two builders."><span aria-hidden="true">.</span></button>;
}
