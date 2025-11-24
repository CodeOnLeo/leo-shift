import { api } from './api.js';

export async function enablePushSubscription() {
  if (!('serviceWorker' in navigator)) {
    alert('Service worker is not supported in this browser.');
    return;
  }
  const permission = await Notification.requestPermission();
  if (permission !== 'granted') {
    alert('Notification permission denied.');
    return;
  }
  const registration = await navigator.serviceWorker.register('/sw.js');
  const { publicKey } = await api.getPublicKey();
  if (!publicKey) {
    alert('Push keys are not configured on the server.');
    return;
  }
  const existing = await registration.pushManager.getSubscription();
  const subscription = existing || (await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: urlBase64ToUint8Array(publicKey)
  }));
  await api.saveSubscription(subscription.toJSON());
  alert('Push notifications enabled.');
}

function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const rawData = atob(base64);
  const outputArray = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}
