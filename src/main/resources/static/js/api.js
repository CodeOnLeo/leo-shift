let isRefreshing = false;
let failedQueue = [];

const processQueue = (error, token = null) => {
  failedQueue.forEach(prom => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });
  failedQueue = [];
};

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

async function refreshAccessToken() {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) {
    throw new Error('No refresh token available');
  }

  const response = await fetch('/api/auth/refresh', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({ refreshToken })
  });

  if (!response.ok) {
    throw new Error('Failed to refresh token');
  }

  const data = await response.json();
  localStorage.setItem('accessToken', data.accessToken);
  localStorage.setItem('refreshToken', data.refreshToken);
  return data.accessToken;
}

let loadingHooks = {
  onStart: () => {},
  onEnd: () => {}
};

export function setLoadingHooks({ onStart, onEnd }) {
  loadingHooks.onStart = typeof onStart === 'function' ? onStart : () => {};
  loadingHooks.onEnd = typeof onEnd === 'function' ? onEnd : () => {};
}

async function request(url, options = {}) {
  loadingHooks.onStart();
  const response = await fetch(url, {
    headers: getHeaders(),
    credentials: 'same-origin',
    ...options
  });

  if (!response.ok) {
    if (response.status === 401) {
      // 토큰이 없으면 바로 로그인 페이지로 이동
      if (!localStorage.getItem('accessToken')) {
        loadingHooks.onEnd();
        window.location.href = '/login.html';
        return;
      }

      // 401 에러 발생 시 토큰 갱신 시도
      if (isRefreshing) {
        // 이미 토큰 갱신 중이면 대기열에 추가
        return new Promise((resolve, reject) => {
          failedQueue.push({ resolve, reject });
        })
          .then(token => {
            options.headers = { ...options.headers, Authorization: `Bearer ${token}` };
            return fetch(url, { ...options, credentials: 'same-origin' }).then(res => {
              if (res.ok) {
                return res.status === 204 ? null : res.json();
              }
              throw new Error('Request failed after token refresh');
            });
          })
          .catch(err => {
            throw err;
          });
      }

      isRefreshing = true;

      try {
        const newToken = await refreshAccessToken();
        processQueue(null, newToken);
        isRefreshing = false;

        // 원래 요청 재시도
        const retryHeaders = getHeaders();
        const retryResponse = await fetch(url, {
          ...options,
          headers: retryHeaders,
          credentials: 'same-origin'
        });

        if (retryResponse.ok) {
          loadingHooks.onEnd();
          if (retryResponse.status === 204) {
            return null;
          }
          const contentType = retryResponse.headers.get('content-type') || '';
          if (contentType.includes('application/json')) {
            return retryResponse.json();
          }
          return retryResponse.text();
        }

        throw new Error('Request failed after token refresh');
      } catch (error) {
        processQueue(error, null);
        isRefreshing = false;
        loadingHooks.onEnd();
        // 토큰 갱신 실패 시 로그인 페이지로 이동
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        window.location.href = '/login.html';
        return;
      }
    }

    const message = await response.text();
    loadingHooks.onEnd();
    throw new Error(message || 'Request failed');
  }

  if (response.status === 204) {
    loadingHooks.onEnd();
    return null;
  }

  const contentType = response.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    const data = await response.json();
    loadingHooks.onEnd();
    return data;
  }
  const text = await response.text();
  loadingHooks.onEnd();
  return text;
}

export const api = {
  getSettings: () => request('/api/settings'),
  saveSettings: (payload) => request('/api/settings', { method: 'PUT', body: JSON.stringify(payload) }),
  getToday: (calendarId) => request(`/api/today${calendarId ? `?calendarId=${calendarId}` : ''}`),
  getCalendar: (year, month, calendarId) => request(`/api/calendar?year=${year}&month=${month}${calendarId ? `&calendarId=${calendarId}` : ''}`),
  getDay: (date, calendarId) => request(`/api/days/${date}${calendarId ? `?calendarId=${calendarId}` : ''}`),
  saveDay: (date, payload, calendarId) => request(`/api/days/${date}${calendarId ? `?calendarId=${calendarId}` : ''}`, { method: 'PUT', body: JSON.stringify(payload) }),
  getNotificationSettings: () => request('/api/settings/notifications'),
  updateNotificationSettings: (payload) => request('/api/settings/notifications', { method: 'PUT', body: JSON.stringify(payload) }),
  saveSubscription: (payload) => request('/api/push/subscriptions', { method: 'POST', body: JSON.stringify(payload) }),
  getPublicKey: () => request('/api/push/public-key'),
  sendTestReminder: () => request('/api/push/test-reminder', { method: 'POST' }),
  logout: () => request('/api/auth/logout', { method: 'POST' }),
  listCalendars: () => request('/api/calendars'),
  setDefaultCalendar: (calendarId) => request(`/api/calendars/${calendarId}/default`, { method: 'PUT' }),
  shareCalendar: (calendarId, payload) => request(`/api/calendars/${calendarId}/share`, { method: 'POST', body: JSON.stringify(payload) }),
  respondShare: (calendarId, payload) => request(`/api/calendars/${calendarId}/shares/respond`, { method: 'POST', body: JSON.stringify(payload) }),
  listShares: (calendarId) => request(`/api/calendars/${calendarId}/shares`),
  updateColor: (payload) => request('/api/settings/color', { method: 'PUT', body: JSON.stringify(payload) }),
  me: () => request('/api/auth/me')
};
