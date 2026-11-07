import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export function getWsUrl() {
  const raw = import.meta.env.VITE_API_URL || '';
  const base = raw.replace(/\/+$/, '');
  return base ? `${base}/ws` : '/ws';
}

let client = null;
const activeSubscriptions = new Map(); // id -> { topic, callback, stompSub }
let subCounter = 0;

export function getClient() {
  if (!client) {
    client = new Client({
      webSocketFactory: () => new SockJS(getWsUrl()),
      reconnectDelay: 2500,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        // Re-subscribe all active topics on connection or reconnection
        activeSubscriptions.forEach((entry) => {
          try {
            entry.stompSub = client.subscribe(entry.topic, (message) => {
              try {
                const body = JSON.parse(message.body);
                entry.callback(body);
              } catch (e) {
                console.warn('[ws] Failed to parse message body', e);
              }
            });
          } catch (e) {
            console.warn('[ws] Subscription failed on connect', e);
          }
        });
      },
      onStompError: (frame) => {
        console.warn('[ws] STOMP protocol error:', frame?.headers?.message);
      },
      onWebSocketError: (event) => {
        console.warn('[ws] WebSocket error:', event);
      },
      onWebSocketClose: () => {
        activeSubscriptions.forEach((entry) => {
          entry.stompSub = null;
        });
      },
    });
    client.activate();
  }
  return client;
}

export function subscribe(topic, callback) {
  if (!topic || typeof callback !== 'function') return () => {};

  const id = ++subCounter;
  const entry = { topic, callback, stompSub: null };
  activeSubscriptions.set(id, entry);

  const c = getClient();
  if (c.connected) {
    try {
      entry.stompSub = c.subscribe(topic, (message) => {
        try {
          const body = JSON.parse(message.body);
          callback(body);
        } catch (e) {
          console.warn('[ws] Failed to parse message body', e);
        }
      });
    } catch (e) {
      console.warn('[ws] Subscribe error:', e);
    }
  }

  return () => {
    activeSubscriptions.delete(id);
    if (entry.stompSub) {
      try {
        entry.stompSub.unsubscribe();
      } catch (_) {}
      entry.stompSub = null;
    }
  };
}

export function subscribeRoom(roomId, handlers = {}) {
  if (!roomId) return () => {};

  const unsubs = [];
  const { onLobby, onSubmission, onFinished, onAny } = handlers;

  if (onLobby || onAny) {
    unsubs.push(
      subscribe(`/topic/room/${roomId}/lobby`, (msg) => {
        onLobby?.(msg);
        onAny?.(msg, 'lobby');
      })
    );
  }

  if (onSubmission || onAny) {
    unsubs.push(
      subscribe(`/topic/room/${roomId}/submission`, (msg) => {
        onSubmission?.(msg);
        onAny?.(msg, 'submission');
      })
    );
  }

  if (onFinished || onAny) {
    unsubs.push(
      subscribe(`/topic/room/${roomId}/finished`, (msg) => {
        onFinished?.(msg);
        onAny?.(msg, 'finished');
      })
    );
  }

  return () => {
    unsubs.forEach((fn) => fn());
  };
}

export function connect(onConnect) {
  const c = getClient();
  if (onConnect && c.connected) {
    onConnect(c);
  }
  return c;
}

export function disconnect() {
  activeSubscriptions.clear();
  if (client) {
    try {
      client.deactivate();
    } catch (_) {}
    client = null;
  }
}
