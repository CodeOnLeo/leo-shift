function getHeaders() {
  const headers = {
    'Content-Type': 'application/json'
  };

  const token = localStorage.getItem('accessToken');
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  return headers;
}

async function request(url, options = {}) {
  const response = await fetch(url, {
    headers: getHeaders(),
    credentials: 'same-origin',
    ...options
  });
  if (!response.ok) {
    if (response.status === 401) {
      // 인증 실패 시 로그인 페이지로 이동
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login.html';
      return;
    }
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
