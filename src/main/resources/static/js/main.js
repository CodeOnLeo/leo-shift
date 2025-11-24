import { api } from './api.js';
import { renderCalendar } from './calendar.js';
import { renderToday } from './today.js';
import { initPatternForm } from './pattern.js';
import { enablePushSubscription } from './notifications.js';

const todaySection = document.getElementById('today-section');
const todayCard = document.getElementById('todayCard');
const upcomingList = document.getElementById('upcomingList');
const calendarSection = document.getElementById('calendar-section');
const calendarTitle = document.getElementById('calendarTitle');
const calendarGrid = document.getElementById('calendarGrid');
const summaryList = document.getElementById('summaryList');
const prevMonthBtn = document.getElementById('prevMonth');
const nextMonthBtn = document.getElementById('nextMonth');
const dayDetailSection = document.getElementById('day-detail');
const dayDetailPanel = document.getElementById('dayDetailPanel');
const dayDetailForm = document.getElementById('dayDetailForm');
const detailCode = document.getElementById('detailCode');
const detailMemo = document.getElementById('detailMemo');
const repeatYearly = document.getElementById('repeatYearly');
const notificationsSection = document.getElementById('notifications');
const notificationForm = document.getElementById('notificationForm');
const notificationMinutesInput = document.getElementById('notificationMinutes');
const subscribePushButton = document.getElementById('subscribePush');

const patternManager = initPatternForm({
  sectionEl: document.getElementById('pattern-setup'),
  formEl: document.getElementById('patternForm'),
  patternInput: document.getElementById('patternInput'),
  startInput: document.getElementById('patternStartDate'),
  minutesInput: document.getElementById('patternMinutes'),
  onSave: async (payload) => {
    await api.saveSettings(payload);
    await bootstrap();
  }
});

const state = {
  year: new Date().getFullYear(),
  month: new Date().getMonth() + 1,
  patternConfigured: false,
  selectedDate: null
};

async function bootstrap() {
  const settings = await api.getSettings();
  state.patternConfigured = settings.configured;
  if (!settings.configured) {
    patternManager.show(settings.defaultNotificationMinutes || 60);
    todaySection.hidden = true;
    calendarSection.hidden = true;
    notificationsSection.hidden = true;
    dayDetailSection.hidden = true;
    return;
  }
  patternManager.hide();
  calendarSection.hidden = false;
  notificationsSection.hidden = false;
  todaySection.hidden = false;
  await Promise.all([loadToday(), loadCalendar(state.year, state.month), loadNotificationSettings()]);
}

async function loadToday() {
  const data = await api.getToday();
  renderToday({ sectionEl: todaySection, cardEl: todayCard, upcomingEl: upcomingList, data });
}

async function loadCalendar(year, month) {
  const data = await api.getCalendar(year, month);
  state.year = data.year;
  state.month = data.month;
  calendarTitle.textContent = `${data.year}년 ${data.month}월`;
  renderCalendar({
    gridEl: calendarGrid,
    summaryEl: summaryList,
    data,
    today: new Date().toISOString().split('T')[0],
    onSelectDay: (date) => selectDay(date)
  });
}

async function selectDay(date) {
  state.selectedDate = date;
  const detail = await api.getDay(date);
  dayDetailSection.hidden = false;
  dayDetailPanel.innerHTML = `
    <strong>${formatFullDate(date)}</strong>
    <span>Base: ${detail.baseCode || '-'}</span>
    <span>Effective: ${detail.effectiveCode || '-'}</span>
    <span>${detail.shiftLabel || ''} · ${detail.timeRange || ''}</span>
    <small>${[detail.memo, ...(detail.yearlyMemos || [])].filter(Boolean).join(' \u2022 ')}</small>
  `;
  detailCode.value = detail.effectiveCode || '';
  detailMemo.value = detail.memo || '';
  repeatYearly.checked = detail.repeatYearly;
}

async function loadNotificationSettings() {
  const data = await api.getNotificationSettings();
  notificationMinutesInput.value = data.minutes;
}

dayDetailForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!state.selectedDate) {
    alert('날짜를 먼저 선택하세요.');
    return;
  }
  await api.saveDay(state.selectedDate, {
    customCode: detailCode.value,
    memo: detailMemo.value,
    repeatYearly: repeatYearly.checked
  });
  await Promise.all([loadCalendar(state.year, state.month), selectDay(state.selectedDate)]);
});

notificationForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  await api.updateNotificationSettings({ minutes: Number(notificationMinutesInput.value) });
  alert('Notification settings saved.');
});

subscribePushButton.addEventListener('click', () => enablePushSubscription());

prevMonthBtn.addEventListener('click', () => {
  const date = new Date(state.year, state.month - 2, 1);
  loadCalendar(date.getFullYear(), date.getMonth() + 1);
});

nextMonthBtn.addEventListener('click', () => {
  const date = new Date(state.year, state.month, 1);
  loadCalendar(date.getFullYear(), date.getMonth() + 1);
});

function formatFullDate(date) {
  return new Date(date).toLocaleDateString(undefined, { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric' });
}

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js');
}

bootstrap();
