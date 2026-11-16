import { useState } from 'react';
import BadgeMedal from './BadgeMedal';
export default function BadgeCelebration({badges,onClose}) {
 const [index,setIndex]=useState(0), badge=badges[index];
 return <aside className="medal-celebration" aria-label="Achievement unlocked" role="status"><button className="medal-dismiss" aria-label="Dismiss badge notification" onClick={onClose}>×</button><div className="medal-reveal" key={badge.id}><div className="medal-halo"/><BadgeMedal id={badge.id} size={140}/><i/><i/><i/></div><p className="medal-kicker">Achievement unlocked</p><h3>{badge.title}</h3><p>{badge.description}</p><div className="medal-actions"><span>{index+1} of {badges.length}</span><button className="arena-secondary" onClick={() => index+1<badges.length?setIndex(index+1):onClose()}>{index+1<badges.length?'Next badge →':'Keep going →'}</button></div></aside>;
}
