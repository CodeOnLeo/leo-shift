const headers = {
  'Content-Type': 'application/json'
};

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers,
    credentials: 'same-origin',
    ...options
  });
  if (!response.ok) {
    const message = await response.text();
    throw new Error(message || 'Request failed');
  }
  if (response.status === 204) {
    return null;
  }
  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    return response.json();
  }
  return response.text();
}

export const api = {
  getSettings: () => request('/api/settings'),
  saveSettings: (payload) => request('/api/settings', { method: 'PUT', body: JSON.stringify(payload) }),
  getToday: () => request('/api/today'),
  getCalendar: (year, month) => request(`/api/calendar?year=${year}&month=${month}`),
  getDay: (date) => request(`/api/days/${date}`),
  saveDay: (date, payload) => request(`/api/days/${date}`, { method: 'PUT', body: JSON.stringify(payload) }),
  getNotificationSettings: () => request('/api/settings/notifications'),
  updateNotificationSettings: (payload) => request('/api/settings/notifications', { method: 'PUT', body: JSON.stringify(payload) }),
  saveSubscription: (payload) => request('/api/push/subscriptions', { method: 'POST', body: JSON.stringify(payload) }),
  getPublicKey: () => request('/api/push/public-key'),
  sendTestReminder: () => request('/api/push/test-reminder', { method: 'POST' })
};
