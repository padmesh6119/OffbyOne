import { useId } from 'react';
import './badges.css';
const ids=['first','five','ten','precise','joins','daily','twentyfive','fifty','foundation','climb','summit','versatile','sharp','xp1000','xp2500'];
const marks=[
 'M46 22 29 48h15l-5 22 22-30H46Z',
 'M25 30 40 65 55 30M35 30 50 65 65 30',
 'M29 28 61 65M61 28 29 65',
 'M45 26v38M26 45h38M35 35h20v20H35Z',
 'M23 34h26v26H23ZM41 26h26v26H41Z',
 'M45 20v8m0 34v8M20 45h8m34 0h8M27 27l6 6m24 24 6 6M63 27l-6 6M33 57l-6 6M35 35h20v20H35Z',
 'M25 35 45 23 65 35v24L45 71 25 59ZM25 35l20 12 20-12M45 47v24',
 'M25 29h40L55 47l10 18H25l10-18Z',
 'M22 63h46M27 63V43h13v20m5 0V28h17v35',
 'M24 64 45 25 66 64M34 47l11 8 11-8',
 'M20 64 39 32 48 48 57 21 73 64Z',
 'M45 22 65 34v23L45 69 25 57V34ZM25 34l40 23M65 34 25 57M45 22v47',
 'M26 48 39 61 65 27M26 32l8 8',
 'M27 30h36v30H27ZM36 24v42m18-42v42M21 40h48m-48 10h48',
 'M20 35 32 47 45 24 58 47 70 35 62 66H28Z'
];
export default function BadgeMedal({ id, unlocked=true, size=80 }) {
 const uid=useId().replace(/:/g,''), index=Math.max(0,ids.indexOf(id));
 const color=index>=12?'#d9b0ff':index>=6?'#ffcd78':'#9ee9d2';
 return <svg className={`badge-medal ${unlocked?'earned':'locked'}`} width={size} height={size} viewBox="0 0 90 100" aria-hidden="true" style={{'--medal-color':color}}>
  <defs><linearGradient id={uid} x1="0" y1="0" x2="1" y2="1"><stop stopColor={color}/><stop offset=".5" stopColor="#f5f2de"/><stop offset="1" stopColor={color}/></linearGradient></defs>
  <path className="medal-ribbon" d="m26 66-4 30 23-10 23 10-4-30"/>
  <path className="medal-body" d="m45 3 36 21v43L45 88 9 67V24Z"/>
  <path className="medal-rim" d="m45 9 30 18v36L45 81 15 63V27Z" stroke={`url(#${uid})`}/>
  <path className="medal-detail" d="m45 14 25 15v31L45 75 20 60V29Z"/>
  <path className="medal-glyph" d={marks[index]} stroke={`url(#${uid})`}/>
  <path className="medal-glint" d="m13 25 18-10m28 57 13-8"/>
 </svg>;
}
