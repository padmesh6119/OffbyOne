import { useState, useEffect } from 'react';
import { api } from '../lib/api';
import { connect, subscribe } from '../lib/ws';

export default function Room() {
  const [mode, setMode] = useState('home');
  const [roomName, setRoomName] = useState('');
  const [joinCode, setJoinCode] = useState('');
  const [room, setRoom] = useState(null);
  const [leaderboard, setLeaderboard] = useState([]);

  async function createRoom() {
    const r = await api.createRoom({ name: roomName });
    setRoom(r);
    setMode('lobby');
    connectRoom(r.id);
  }

  async function joinRoom() {
    const r = await api.joinRoom(joinCode);
    setRoom(r);
    setMode('lobby');
    connectRoom(r.roomId);
  }

  function connectRoom(roomId) {
    connect(() => {
      subscribe(`/topic/room/${roomId}/submission`, (data) => {
        api.leaderboard(roomId).then(setLeaderboard);
      });
    });
  }

  if (mode === 'home') return (
    <div className="page">
      <h2>Rooms</h2>
      <div className="room-actions">
        <div>
          <input placeholder="Room name" value={roomName}
            onChange={e => setRoomName(e.target.value)} />
          <button onClick={createRoom}>Create Room</button>
        </div>
        <div>
          <input placeholder="6-char code" value={joinCode}
            onChange={e => setJoinCode(e.target.value.toUpperCase())} />
          <button onClick={joinRoom}>Join Room</button>
        </div>
      </div>
    </div>
  );

  return (
    <div className="page">
      <h2>{room?.name}</h2>
      <p>Join code: <strong>{room?.joinCode || joinCode}</strong></p>
      <h3>Live Leaderboard</h3>
      <table>
        <thead><tr><th>Player</th><th>Score</th></tr></thead>
        <tbody>
          {leaderboard.map((row, i) => (
            <tr key={i}><td>{row.username}</td><td>{row.score}</td></tr>
          ))}
        </tbody>
      </table>
      <p>Go to <a href="/problems">Problems</a> and submit — scores update live.</p>
    </div>
  );
}
