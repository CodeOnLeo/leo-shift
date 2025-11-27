import { api } from './api.js';
import { renderCalendar } from './calendar.js';
import { initPatternForm } from './pattern.js';
import { enablePushSubscription } from './notifications.js';
import { setLoadingHooks } from './api.js';

const calendarSection = document.getElementById('calendar-section');
const calendarTitle = document.getElementById('calendarTitle');
const calendarGrid = document.getElementById('calendarGrid');
const summaryList = document.getElementById('summaryList');
const prevMonthBtn = document.getElementById('prevMonth');
const nextMonthBtn = document.getElementById('nextMonth');
const legendToggle = document.getElementById('legendToggle');
const legendTooltip = document.getElementById('legendTooltip');
const settingsMenuButton = document.getElementById('settingsMenuButton');
const settingsModal = document.getElementById('settingsModal');
const settingsModalClose = document.getElementById('settingsModalClose');
const dayModal = document.getElementById('dayModal');
const modalClose = document.getElementById('modalClose');
const dayDetailPanel = document.getElementById('dayDetailPanel');
const dayDetailForm = document.getElementById('dayDetailForm');
const detailCode = document.getElementById('detailCode');
const detailMemo = document.getElementById('detailMemo');
const repeatYearly = document.getElementById('repeatYearly');
const anniversaryMemo = document.getElementById('anniversaryMemo');
const clearMemoButton = document.getElementById('clearMemo');
const clearAnniversaryButton = document.getElementById('clearAnniversary');
const patternDisabledHint = document.getElementById('patternDisabledHint');
const toast = document.getElementById('toast');
let toastTimer = null;
let loadingCounter = 0;
const loadingOverlay = document.getElementById('loadingOverlay');
const notificationForm = document.getElementById('notificationForm');
const notificationHoursInput = document.getElementById('notificationHours');
const notificationMinutesInput = document.getElementById('notificationMinutes');
const subscribePushButton = document.getElementById('subscribePush');
const resetPatternButton = document.getElementById('resetPattern');
const calendarSelector = document.getElementById('calendarSelector');
const calendarSelectorButton = document.getElementById('calendarSelectorButton');
const calendarSelectorList = document.getElementById('calendarSelectorList');
const shareSection = document.getElementById('shareSection');
const shareEmailInput = document.getElementById('shareEmail');
const sharePermissionSelect = document.getElementById('sharePermission');
const shareInviteButton = document.getElementById('shareInviteButton');
const shareList = document.getElementById('shareList');
const inviteList = document.getElementById('inviteList');
const patternFields = document.querySelectorAll('[data-pattern="true"]');
const colorPicker = document.getElementById('colorPicker');
const saveColorButton = document.getElementById('saveColorButton');
const createCalendarForm = document.getElementById('createCalendarForm');
const newCalendarNameInput = document.getElementById('newCalendarName');
const newCalendarPatternEnabledInput = document.getElementById('newCalendarPatternEnabled');
const editCalendarForm = document.getElementById('editCalendarForm');
const editCalendarNameInput = document.getElementById('editCalendarName');
const editCalendarPatternEnabledInput = document.getElementById('editCalendarPatternEnabled');
const editCalendarSaveButton = document.getElementById('editCalendarSaveButton');
const editCalendarHint = document.getElementById('editCalendarHint');
const deleteCalendarButton = document.getElementById('deleteCalendarButton');

const patternManager = initPatternForm({
  sectionEl: document.getElementById('pattern-setup'),
  formEl: document.getElementById('patternForm'),
  patternInput: document.getElementById('patternInput'),
  startInput: document.getElementById('patternStartDate'),
  hoursInput: document.getElementById('patternHours'),
  minutesInput: document.getElementById('patternMinutes'),
  applyRetroactiveInput: document.getElementById('applyPatternRetroactive'),
  onSave: async (payload) => {
    if (!state.calendarId) {
      alert('캘린더를 먼저 선택하세요.');
      return;
    }
    await api.saveSettings(payload, state.calendarId);
    await bootstrap();
  }
});

const state = {
  year: new Date().getFullYear(),
  month: new Date().getMonth() + 1,
  patternConfigured: false,
  selectedDate: null,
  calendarId: null,
  calendars: [],
  me: null
};

async function bootstrap() {
  await loadCalendars();
  await loadMeColor();
  const settings = await loadPatternSettings();
  if (!state.patternConfigured) {
    patternManager.show(settings.defaultNotificationMinutes || 60, settings);
    calendarSection.hidden = true;
    settingsMenuButton.hidden = true;
    dayModal.hidden = true;
    settingsModal.hidden = true;
    return;
  }
  patternManager.hide();
  calendarSection.hidden = false;
  settingsMenuButton.hidden = false;
  await Promise.all([loadCalendar(state.year, state.month), loadNotificationSettings(), loadShares()]);
}

async function loadCalendars() {
  const res = await api.listCalendars();
  state.calendars = res.calendars || [];
  state.calendarId = res.defaultCalendarId || (state.calendars[0] ? state.calendars[0].id : null);
  renderCalendarSelector();
  renderInvites();
}

async function loadMeColor() {
  if (!colorPicker) return;
  try {
    const me = await api.me();
    state.me = me;
    if (me && me.colorTag) {
      colorPicker.value = me.colorTag;
    }
  } catch (e) {
    // ignore
  }
}

async function loadPatternSettings() {
  const settings = await api.getSettings(state.calendarId);
  const currentCalendar = state.calendars.find((c) => c.id === state.calendarId);
  const usePattern = currentCalendar ? currentCalendar.patternEnabled !== false : true;
  state.patternConfigured = usePattern ? settings.configured : true;
  state.usePattern = usePattern;
  return settings;
}

function syncCalendarEditForm() {
  if (!editCalendarForm || !editCalendarNameInput || !editCalendarPatternEnabledInput) return;
  const current = state.calendars.find((c) => c.id === state.calendarId);
  const isOwner = !!current && current.owned;
  if (current) {
    editCalendarNameInput.value = current.name || '';
    editCalendarPatternEnabledInput.checked = current.patternEnabled !== false;
  } else {
    editCalendarNameInput.value = '';
    editCalendarPatternEnabledInput.checked = true;
  }
  const disableForm = !isOwner;
  [editCalendarNameInput, editCalendarPatternEnabledInput, editCalendarSaveButton, deleteCalendarButton].forEach((el) => {
    if (el) {
      el.disabled = disableForm;
    }
  });
  if (editCalendarHint) {
    if (!current) {
      editCalendarHint.hidden = false;
      editCalendarHint.textContent = '캘린더를 선택하세요.';
    } else {
      editCalendarHint.hidden = isOwner;
      if (!isOwner) {
        editCalendarHint.textContent = '소유한 캘린더만 수정할 수 있습니다.';
      }
    }
  }
}

function renderCalendarSelector() {
  if (!calendarSelector || !calendarSelectorList) return;
  calendarSelectorList.innerHTML = '';
  state.calendars.forEach((cal) => {
    const item = document.createElement('button');
    item.type = 'button';
    item.className = 'calendar-selector-item';
    if (cal.id === state.calendarId) {
      item.classList.add('active');
    }
    const label = `${cal.name}${cal.owned ? '' : ` · ${cal.ownerName || ''}`}`;
    item.textContent = label;
    item.addEventListener('click', async () => {
      state.calendarId = cal.id;
      calendarSelectorList.hidden = true;
      const settings = await loadPatternSettings();
      if (!state.patternConfigured) {
        patternManager.show(settings.defaultNotificationMinutes || 60, settings);
        calendarSection.hidden = true;
        settingsMenuButton.hidden = true;
        return;
      }
      patternManager.hide();
      calendarSection.hidden = false;
      settingsMenuButton.hidden = false;
      await loadCalendar(state.year, state.month);
      await loadShares();
      renderCalendarSelector();
    });
    calendarSelectorList.appendChild(item);
  });
  const current = state.calendars.find(c => c.id === state.calendarId);
  if (current) {
    state.usePattern = current.patternEnabled !== false;
  }
  calendarSelectorButton.textContent = current?.name || '캘린더 선택';
  syncCalendarEditForm();
}

async function loadShares() {
  if (!shareList || !state.calendarId) return;
  const shares = await api.listShares(state.calendarId);
  shareList.innerHTML = '';
  shares.forEach((s) => {
    const item = document.createElement('div');
    item.className = 'share-item';
    const meta = document.createElement('div');
    meta.className = 'meta';
    meta.innerHTML = `<strong>${s.userName}</strong><span>${s.userEmail}</span>`;
    const badges = document.createElement('div');
    badges.className = 'badges';
    const perm = document.createElement('span');
    perm.className = 'badge';
    perm.textContent = s.permission === 'EDIT' ? '편집' : '보기';
    const status = document.createElement('span');
    status.className = 'badge';
    status.dataset.tone = s.status === 'ACCEPTED' ? 'success' : s.status === 'PENDING' ? 'warning' : 'danger';
    status.textContent = s.status === 'ACCEPTED' ? '수락됨' : s.status === 'PENDING' ? '대기' : '거절';
    badges.append(perm, status);
    item.append(meta, badges);
    shareList.appendChild(item);
  });
}

function renderInvites() {
  if (!inviteList) return;
  inviteList.innerHTML = '';
  const pending = state.calendars.filter(c => !c.owned && c.status === 'PENDING');
  if (pending.length === 0) {
    inviteList.textContent = '받은 초대가 없습니다.';
    inviteList.className = 'invite-list empty';
    return;
  }
  inviteList.className = 'invite-list';
  pending.forEach((cal) => {
    const item = document.createElement('div');
    item.className = 'invite-item';
    item.innerHTML = `
      <div class="meta">
        <strong>${cal.name}</strong>
        <span>${cal.ownerName || ''}</span>
      </div>
    `;
    const actions = document.createElement('div');
    actions.className = 'invite-actions';
    const acceptBtn = document.createElement('button');
    acceptBtn.type = 'button';
    acceptBtn.className = 'success';
    acceptBtn.textContent = '수락';
    acceptBtn.addEventListener('click', async () => {
      try {
        await api.respondShare(cal.id, { accept: true });
        showToast('초대를 수락했습니다.');
        await loadCalendars();
        await loadShares();
      } catch (e) {
        showToast('수락 실패: ' + (e.message || '오류'));
      }
    });
    const rejectBtn = document.createElement('button');
    rejectBtn.type = 'button';
    rejectBtn.className = 'danger';
    rejectBtn.textContent = '거절';
    rejectBtn.addEventListener('click', async () => {
      try {
        await api.respondShare(cal.id, { accept: false });
        showToast('초대를 거절했습니다.');
        await loadCalendars();
        await loadShares();
      } catch (e) {
        showToast('거절 실패: ' + (e.message || '오류'));
      }
    });
    actions.append(acceptBtn, rejectBtn);
    item.appendChild(actions);
    inviteList.appendChild(item);
  });
}

async function loadCalendar(year, month) {
  const data = await api.getCalendar(year, month, state.calendarId);
  state.year = data.year;
  state.month = data.month;
  calendarTitle.textContent = `${data.year}년 ${data.month}월`;
  renderCalendar({
    gridEl: calendarGrid,
    summaryEl: summaryList,
    data,
    today: new Date().toISOString().split('T')[0],
    selectedDate: state.selectedDate,
    onSelectDay: (date) => selectDay(date)
  });
}

async function selectDay(date) {
  state.selectedDate = date;
  const detail = await api.getDay(date, state.calendarId);
  dayModal.hidden = false;
  const hasMemo = detail.memo || detail.anniversaryMemo || (detail.yearlyMemos && detail.yearlyMemos.length > 0);
  dayDetailPanel.innerHTML = `
    <strong>${formatKoreanDate(date)}</strong>
    <span>기본 근무: ${detail.baseCode || '-'}</span>
    <span>실제 근무: ${detail.effectiveCode || '-'}</span>
    <span>${detail.shiftLabel || ''} · ${detail.timeRange || ''}</span>
    <small>${[detail.memo, detail.anniversaryMemo, ...(detail.yearlyMemos || [])].filter(Boolean).join(' • ')}</small>
    ${hasMemo && detail.memoAuthor ? `<div class="memo-author-line">작성: ${detail.memoAuthor.name}${detail.updatedAt ? ` · ${formatDateTime(detail.updatedAt)}` : ''}</div>` : ''}
  `;
  detailCode.value = detail.effectiveCode || '';

  // 일반 메모와 기념일 메모를 각각 표시
  detailMemo.value = detail.memo || '';

  // 기념일 메모: 당일 것이 있으면 우선, 없으면 yearlyMemos에서 첫 번째 것 사용
  if (detail.anniversaryMemo) {
    anniversaryMemo.value = detail.anniversaryMemo;
    repeatYearly.checked = detail.repeatYearly;
  } else if (detail.yearlyMemos && detail.yearlyMemos.length > 0) {
    anniversaryMemo.value = detail.yearlyMemos[0];
    repeatYearly.checked = true; // yearlyMemos에 있다는 것은 반복 설정된 것
  } else {
    anniversaryMemo.value = '';
    repeatYearly.checked = false;
  }

  // 메모 지우기 버튼 표시 여부
  clearMemoButton.style.display = detail.memo ? 'block' : 'none';

  // 기념일 지우기 버튼 표시 여부
  clearAnniversaryButton.style.display = (detail.anniversaryMemo || (detail.yearlyMemos && detail.yearlyMemos.length > 0)) ? 'block' : 'none';

  // 패턴 사용 안 하는 캘린더면 코드 선택 비활성
  if (state.usePattern === false) {
    detailCode.value = '';
    detailCode.disabled = true;
    patternFields.forEach(el => el.setAttribute('disabled', 'true'));
    if (patternDisabledHint) {
      patternDisabledHint.hidden = false;
    }
  } else {
    detailCode.disabled = false;
    patternFields.forEach(el => el.removeAttribute('disabled'));
    if (patternDisabledHint) {
      patternDisabledHint.hidden = true;
    }
  }

  await loadCalendar(state.year, state.month);
}

function closeModal() {
  dayModal.hidden = true;
  state.selectedDate = null;
  loadCalendar(state.year, state.month);
}

async function loadNotificationSettings() {
  const data = await api.getNotificationSettings();
  const totalMinutes = data.minutes;
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  notificationHoursInput.value = hours;
  notificationMinutesInput.value = minutes;
}

settingsMenuButton.addEventListener('click', () => {
  settingsModal.hidden = false;
});

settingsModalClose.addEventListener('click', () => {
  settingsModal.hidden = true;
});

if (calendarSelectorButton) {
  calendarSelectorButton.addEventListener('click', (e) => {
    e.stopPropagation();
    calendarSelectorList.hidden = !calendarSelectorList.hidden;
  });
}

// 캘린더 선택 드롭다운 외부 클릭 시 닫기
document.addEventListener('click', (e) => {
  if (calendarSelector && calendarSelectorList && !calendarSelector.contains(e.target) && !calendarSelectorList.hidden) {
    calendarSelectorList.hidden = true;
  }
});

settingsModal.addEventListener('click', (e) => {
  if (e.target === settingsModal) {
    settingsModal.hidden = true;
  }
});

if (shareInviteButton) {
  shareInviteButton.addEventListener('click', async () => {
    if (!state.calendarId) {
      alert('캘린더를 먼저 선택하세요.');
      return;
    }
    const email = (shareEmailInput.value || '').trim();
    const permission = sharePermissionSelect.value;
    if (!email) {
      alert('이메일을 입력하세요.');
      return;
    }
    try {
      await api.shareCalendar(state.calendarId, { email, permission });
      shareEmailInput.value = '';
      showToast('초대가 전송되었습니다.');
      await loadShares();
    } catch (e) {
      showToast('초대 전송 실패: ' + (e.message || '오류'));
    }
  });
}

if (saveColorButton && colorPicker) {
  saveColorButton.addEventListener('click', async () => {
    const color = colorPicker.value;
    try {
      await api.updateColor({ color });
      showToast('색상이 저장되었습니다.');
      await loadCalendar(state.year, state.month);
    } catch (e) {
      showToast('색상 저장 실패: ' + (e.message || '오류'));
    }
  });
}

if (createCalendarForm) {
  createCalendarForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const name = (newCalendarNameInput.value || '').trim();
    if (!name) {
      alert('캘린더 이름을 입력하세요.');
      return;
    }
    const patternEnabled = newCalendarPatternEnabledInput.checked;
    try {
      const res = await api.createCalendar({ name, patternEnabled });
      state.calendars = res.calendars || [];
      state.calendarId = res.defaultCalendarId || (state.calendars[0] ? state.calendars[0].id : null);
      renderCalendarSelector();
      renderInvites();
      newCalendarNameInput.value = '';
      newCalendarPatternEnabledInput.checked = true;
      showToast('캘린더가 생성되었습니다.');
      const settings = await loadPatternSettings();
      if (!state.patternConfigured) {
        patternManager.show(settings.defaultNotificationMinutes || 60, settings);
        calendarSection.hidden = true;
        settingsMenuButton.hidden = true;
        return;
      }
      patternManager.hide();
      calendarSection.hidden = false;
      settingsMenuButton.hidden = false;
      await loadCalendar(state.year, state.month);
      await loadShares();
    } catch (e) {
      showToast('캘린더 생성 실패: ' + (e.message || '오류'));
    }
  });
}

if (editCalendarForm) {
  editCalendarForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const current = state.calendars.find((c) => c.id === state.calendarId);
    if (!current) {
      alert('캘린더를 먼저 선택하세요.');
      return;
    }
    if (!current.owned) {
      alert('소유한 캘린더만 수정할 수 있습니다.');
      return;
    }
    const name = (editCalendarNameInput.value || '').trim();
    if (!name) {
      alert('캘린더 이름을 입력하세요.');
      return;
    }
    const patternEnabled = editCalendarPatternEnabledInput.checked;
    try {
      const res = await api.updateCalendar(state.calendarId, { name, patternEnabled });
      state.calendars = res.calendars || [];
      const stillExists = state.calendars.find(c => c.id === state.calendarId);
      if (!stillExists) {
        state.calendarId = res.defaultCalendarId || (state.calendars[0] ? state.calendars[0].id : null);
      }
      renderCalendarSelector();
      renderInvites();
      showToast('캘린더가 수정되었습니다.');
      const settings = await loadPatternSettings();
      if (!state.patternConfigured) {
        patternManager.show(settings.defaultNotificationMinutes || 60, settings);
        calendarSection.hidden = true;
        settingsMenuButton.hidden = true;
        return;
      }
      patternManager.hide();
      calendarSection.hidden = false;
      settingsMenuButton.hidden = false;
      await loadCalendar(state.year, state.month);
      await loadShares();
    } catch (e) {
      showToast('캘린더 수정 실패: ' + (e.message || '오류'));
    }
  });
}

if (deleteCalendarButton) {
  deleteCalendarButton.addEventListener('click', async () => {
    const current = state.calendars.find((c) => c.id === state.calendarId);
    if (!current) {
      alert('캘린더를 먼저 선택하세요.');
      return;
    }
    if (!current.owned) {
      alert('소유한 캘린더만 삭제할 수 있습니다.');
      return;
    }
    if (!confirm(`"${current.name}" 캘린더를 삭제하시겠습니까?\n공유·메모 등 모든 데이터가 삭제됩니다.`)) {
      return;
    }
    try {
      const res = await api.deleteCalendar(state.calendarId);
      state.calendars = res.calendars || [];
      state.calendarId = res.defaultCalendarId || (state.calendars[0] ? state.calendars[0].id : null);
      renderCalendarSelector();
      renderInvites();
      showToast('캘린더가 삭제되었습니다.');
      if (state.calendarId) {
        const settings = await loadPatternSettings();
        if (!state.patternConfigured) {
          patternManager.show(settings.defaultNotificationMinutes || 60, settings);
          calendarSection.hidden = true;
          settingsMenuButton.hidden = true;
        } else {
          patternManager.hide();
          calendarSection.hidden = false;
          settingsMenuButton.hidden = false;
          await loadCalendar(state.year, state.month);
          await loadShares();
        }
      } else {
        calendarGrid.innerHTML = '';
        summaryList.innerHTML = '';
        if (shareList) shareList.innerHTML = '';
      }
    } catch (e) {
      showToast('캘린더 삭제 실패: ' + (e.message || '오류'));
    }
  });
}

modalClose.addEventListener('click', closeModal);

dayModal.addEventListener('click', (e) => {
  if (e.target === dayModal) {
    closeModal();
  }
});

// 기념일 체크박스는 별도 기능 없음 (매년 반복 여부만 결정)

// 메모 지우기 버튼
clearMemoButton.addEventListener('click', () => {
  if (confirm('메모를 삭제하시겠습니까?')) {
    detailMemo.value = '';
  }
});

// 기념일 지우기 버튼
clearAnniversaryButton.addEventListener('click', async () => {
  const detail = await api.getDay(state.selectedDate);

  // yearlyMemos가 있고 현재 날짜에 anniversaryMemo가 없으면, 다른 년도에서 반복되는 것
  const isYearlyFromOtherYear = !detail.anniversaryMemo && detail.yearlyMemos && detail.yearlyMemos.length > 0;

  if (isYearlyFromOtherYear) {
    // 다른 년도의 반복 기념일인 경우
    const selectedDate = new Date(state.selectedDate);
    const currentYear = selectedDate.getFullYear();

    if (confirm(`이 기념일은 다른 년도에 등록된 매년 반복 기념일입니다.\n\n삭제 옵션:\n확인: ${currentYear}년 이후로 더 이상 표시 안 함\n취소: 이 날짜(${currentYear}년)에서만 숨김`)) {
      // 이 날짜 이후로 반복 중지
      alert(`원본 기념일이 등록된 년도로 이동하여 "매년 반복"을 해제해야 ${currentYear}년 이후 반복이 중지됩니다.\n\n또는 이 날짜에서 빈 값으로 저장하면 이 날짜에서만 숨겨집니다.`);
    } else {
      // 해당 날짜만 숨기기
      anniversaryMemo.value = '';
      repeatYearly.checked = false;
      alert('변경사항을 저장하면 이 날짜에서만 숨겨집니다.');
    }
  } else if (repeatYearly.checked) {
    // 매년 반복이 설정된 기념일 (원본)
    const selectedDate = new Date(state.selectedDate);
    const currentYear = selectedDate.getFullYear();

    const choice = confirm(`이 기념일을 삭제하시겠습니까?\n\n확인: ${currentYear}년부터 이후 모든 연도에서 삭제\n취소: ${currentYear}년에만 삭제`);

    anniversaryMemo.value = '';
    repeatYearly.checked = false;

    if (choice) {
      alert(`변경사항을 저장하면 ${currentYear}년부터 모든 연도에서 삭제됩니다.\n(${currentYear}년 이전 과거 기록은 유지됩니다)`);
    } else {
      alert(`변경사항을 저장하면 ${currentYear}년에서만 삭제됩니다.`);
    }
  } else {
    if (confirm('기념일을 삭제하시겠습니까?')) {
      anniversaryMemo.value = '';
      repeatYearly.checked = false;
    }
  }
});

dayDetailForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!state.selectedDate) {
    alert('날짜를 먼저 선택하세요.');
    return;
  }

  try {
    // 일반 메모와 기념일 메모를 각각 저장
    await api.saveDay(state.selectedDate, {
      customCode: detailCode.value || null,
      memo: detailMemo.value || null,
      anniversaryMemo: anniversaryMemo.value || null,
      repeatYearly: repeatYearly.checked
    }, state.calendarId);

    // 캘린더 그리드 즉시 업데이트
    await loadCalendar(state.year, state.month);

    // 모달 내용도 즉시 업데이트 (저장된 내용 다시 로드)
    const detail = await api.getDay(state.selectedDate, state.calendarId);
    const hasMemo = detail.memo || detail.anniversaryMemo || (detail.yearlyMemos && detail.yearlyMemos.length > 0);
    dayDetailPanel.innerHTML = `
      <strong>${formatKoreanDate(state.selectedDate)}</strong>
      <span>기본 근무: ${detail.baseCode || '-'}</span>
      <span>실제 근무: ${detail.effectiveCode || '-'}</span>
      <span>${detail.shiftLabel || ''} · ${detail.timeRange || ''}</span>
      <small>${[detail.memo, detail.anniversaryMemo, ...(detail.yearlyMemos || [])].filter(Boolean).join(' • ')}</small>
      ${hasMemo && detail.memoAuthor ? `<div class="memo-author-line">작성: ${detail.memoAuthor.name}${detail.updatedAt ? ` · ${formatDateTime(detail.updatedAt)}` : ''}</div>` : ''}
    `;

    // 메모 지우기 버튼 표시 여부 업데이트
    clearMemoButton.style.display = detail.memo ? 'block' : 'none';
    clearAnniversaryButton.style.display = (detail.anniversaryMemo || (detail.yearlyMemos && detail.yearlyMemos.length > 0)) ? 'block' : 'none';

    showToast('저장되었습니다.');
  } catch (e) {
    showToast('저장 실패: ' + (e.message || '오류'));
  }
});

notificationForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  const hours = Number(notificationHoursInput.value) || 0;
  const minutes = Number(notificationMinutesInput.value) || 0;
  const totalMinutes = hours * 60 + minutes;

  if (totalMinutes < 5) {
    alert('알림 시간은 최소 5분 이상이어야 합니다.');
    return;
  }
  if (totalMinutes > 240) {
    alert('알림 시간은 최대 4시간(240분)까지 설정 가능합니다.');
    return;
  }

  await api.updateNotificationSettings({ minutes: totalMinutes });
  alert('알림 설정이 저장되었습니다.');
});

subscribePushButton.addEventListener('click', () => enablePushSubscription());

resetPatternButton.addEventListener('click', async () => {
  if (!confirm('근무 패턴을 재설정하시겠습니까?')) return;
  const settings = await api.getSettings();
  settingsModal.hidden = true;
  calendarSection.hidden = true;
  settingsMenuButton.hidden = true;
  dayModal.hidden = true;
  patternManager.show(settings.defaultNotificationMinutes || 60, settings);
});

prevMonthBtn.addEventListener('click', () => {
  const date = new Date(state.year, state.month - 2, 1);
  loadCalendar(date.getFullYear(), date.getMonth() + 1);
});

nextMonthBtn.addEventListener('click', () => {
  const date = new Date(state.year, state.month, 1);
  loadCalendar(date.getFullYear(), date.getMonth() + 1);
});

legendToggle.addEventListener('click', () => {
  legendTooltip.hidden = !legendTooltip.hidden;
});

// 툴팁 외부 클릭시 닫기
document.addEventListener('click', (e) => {
  if (!legendTooltip.hidden && !legendToggle.contains(e.target) && !legendTooltip.contains(e.target)) {
    legendTooltip.hidden = true;
  }
});

function formatDateTime(isoString) {
  try {
    const d = new Date(isoString);
    const hours = String(d.getHours()).padStart(2, '0');
    const minutes = String(d.getMinutes()).padStart(2, '0');
    return `${d.getMonth() + 1}/${d.getDate()} ${hours}:${minutes}`;
  } catch (e) {
    return '';
  }
}

function formatKoreanDate(date) {
  const d = new Date(date);
  const year = d.getFullYear();
  const month = d.getMonth() + 1;
  const day = d.getDate();
  const weekdays = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일'];
  const weekday = weekdays[d.getDay()];
  return `${year}년 ${month}월 ${day}일 ${weekday}`;
}

// 인증 체크
function checkAuth() {
  const token = localStorage.getItem('accessToken');
  if (!token) {
    window.location.href = '/login.html';
    return false;
  }
  return true;
}

// 로그아웃 함수
window.logout = async function() {
  if (confirm('로그아웃 하시겠습니까?')) {
    try {
      // 서버에 로그아웃 요청
      await api.logout();
    } catch (error) {
      console.error('로그아웃 요청 실패:', error);
    } finally {
      // 로컬 스토리지 정리 및 로그인 페이지로 이동
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      window.location.href = '/login.html';
    }
  }
};

if ('serviceWorker' in navigator) {
  navigator.serviceWorker.register('/sw.js');
}

if (checkAuth()) {
  bootstrap();
}

function showToast(message) {
  if (!toast) return;
  toast.textContent = message;
  toast.hidden = false;
  if (toastTimer) {
    clearTimeout(toastTimer);
  }
  toastTimer = setTimeout(() => {
    toast.hidden = true;
  }, 3000);
}

setLoadingHooks({
  onStart: () => {
    // 임시 조치: 로그인 이후 로딩이 멈추지 않는 현상이 있어 캘린더 진입 시 오버레이 비활성화
    loadingCounter = 0;
    if (loadingOverlay) {
      loadingOverlay.hidden = true;
    }
  },
  onEnd: () => {
    loadingCounter = 0;
    if (loadingOverlay) {
      loadingOverlay.hidden = true;
    }
  }
});
