import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080';

let client = null;

export function connect(onConnect) {
  client = new Client({
    webSocketFactory: () => new SockJS(`${BASE}/ws`),
    onConnect,
  });
  client.activate();
  return client;
}

export function subscribe(topic, callback) {
  return client.subscribe(topic, (msg) => callback(JSON.parse(msg.body)));
}

export function disconnect() {
  client?.deactivate();
}
