import { api } from './api.js';
import { renderCalendar } from './calendar.js';
import { initPatternForm } from './pattern.js';
import { setLoadingHooks } from './api.js';
import { getLocalTodayIso, parseIsoDateLocal } from './date-utils.js';

const calendarSection = document.getElementById('calendar-section');
const calendarTitle = document.getElementById('calendarTitle');
const calendarGrid = document.getElementById('calendarGrid');
const calendarEmpty = document.getElementById('calendarEmpty');
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
const repeatYearly = document.getElementById('repeatYearly');
const anniversaryMemo = document.getElementById('anniversaryMemo');
const clearAnniversaryButton = document.getElementById('clearAnniversary');
const patternDisabledHint = document.getElementById('patternDisabledHint');
const memoList = document.getElementById('memoList');
const memoAddForm = document.getElementById('memoAddForm');
const newMemoText = document.getElementById('newMemoText');
const toast = document.getElementById('toast');
let toastTimer = null;
let loadingCounter = 0;
let loadingHideTimer = null;
let loadingFailSafeTimer = null;
const loadingOverlay = document.getElementById('loadingOverlay');
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
const newCalendarTemplatePicker = document.getElementById('newCalendarTemplatePicker');
const newCalendarTemplateInput = document.getElementById('newCalendarTemplate');
const newCalendarTemplateHint = document.getElementById('newCalendarTemplateHint');
const editCalendarForm = document.getElementById('editCalendarForm');
const editCalendarNameInput = document.getElementById('editCalendarName');
const editCalendarPatternEnabledInput = document.getElementById('editCalendarPatternEnabled');
const editCalendarSaveButton = document.getElementById('editCalendarSaveButton');
const editCalendarHint = document.getElementById('editCalendarHint');
const deleteCalendarButton = document.getElementById('deleteCalendarButton');
const patternOnboardingModal = document.getElementById('patternOnboardingModal');
const patternOnboardingUse = document.getElementById('patternOnboardingUse');
const patternOnboardingSkip = document.getElementById('patternOnboardingSkip');
const patternOnboardingNoCalendar = document.getElementById('patternOnboardingNoCalendar');
const patternOnboardingClose = document.getElementById('patternOnboardingClose');
const patternCalendarModal = document.getElementById('patternCalendarModal');
const patternCalendarForm = document.getElementById('patternCalendarForm');
const patternCalendarNameInput = document.getElementById('patternCalendarName');
const patternCalendarBack = document.getElementById('patternCalendarBack');
const patternCalendarClose = document.getElementById('patternCalendarClose');
const patternCalendarTitle = document.getElementById('patternCalendarTitle');
const patternCalendarSubtitle = document.getElementById('patternCalendarSubtitle');
const patternCalendarSubmit = document.getElementById('patternCalendarSubmit');
const calendarPickerSection = document.getElementById('calendarPickerSection');
const calendarPickerList = document.getElementById('calendarPickerList');
const scheduleTypeEditorSection = document.getElementById('scheduleTypeEditorSection');
const scheduleTypeEditorList = document.getElementById('scheduleTypeEditorList');
const addScheduleTypeButton = document.getElementById('addScheduleTypeButton');
const saveScheduleTypesButton = document.getElementById('saveScheduleTypesButton');
const weeklyRuleEditorSection = document.getElementById('weeklyRuleEditorSection');
const weeklyRuleEditorList = document.getElementById('weeklyRuleEditorList');
const saveWeeklyRulesButton = document.getElementById('saveWeeklyRulesButton');

const state = {
  year: new Date().getFullYear(),
  month: new Date().getMonth() + 1,
  patternConfigured: false,
  usePattern: true,
  selectedDate: null,
  calendarId: null,
  calendars: [],
  calendarGroups: {
    owned: [],
    shared: []
  },
  me: null,
  calendarData: null,
  scheduleTypes: [],
  weeklyRules: []
};

const patternManager = initPatternForm({
  sectionEl: document.getElementById('pattern-setup'),
  formEl: document.getElementById('patternForm'),
  patternInput: document.getElementById('patternInput'),
  startInput: document.getElementById('patternStartDate'),
  applyRetroactiveInput: document.getElementById('applyPatternRetroactive'),
  getScheduleTypes: () => getScheduleTypes(),
  onSave: async (payload) => {
    if (!state.calendarId) {
      alert('캘린더를 먼저 선택하세요.');
      return;
    }
    await api.saveSettings(payload, state.calendarId);
    invalidateCache('settings', state.calendarId);
    invalidateCache('calendar');
    await bootstrap();
  }
});

function getScheduleTypes(preferred = null) {
  if (Array.isArray(preferred) && preferred.length > 0) {
    return preferred;
  }
  if (!state) {
    return [];
  }
  return Array.isArray(state.scheduleTypes) ? state.scheduleTypes : [];
}

function setScheduleTypes(scheduleTypes = []) {
  state.scheduleTypes = Array.isArray(scheduleTypes) ? scheduleTypes : [];
  renderLegend();
  syncDetailCodeOptions();
  renderScheduleTypeEditor();
  renderWeeklyRuleEditor();
}

function setWeeklyRules(weeklyRules = []) {
  state.weeklyRules = Array.isArray(weeklyRules) ? weeklyRules : [];
  renderWeeklyRuleEditor();
}

function getScheduleType(code, scheduleTypes = null) {
  if (!code) return null;
  return getScheduleTypes(scheduleTypes).find((type) => type.code === code) || null;
}

function formatScheduleTypeLabel(type) {
  if (!type) return '';
  if (!type.name || type.name === type.code) {
    return type.code;
  }
  return `${type.name} (${type.code})`;
}

function formatScheduleValue(code, scheduleTypes = null) {
  if (!code) return '-';
  const type = getScheduleType(code, scheduleTypes);
  return formatScheduleTypeLabel(type) || code;
}

function renderLegend(scheduleTypes = state.scheduleTypes) {
  if (!legendTooltip) return;
  legendTooltip.innerHTML = '';
  getScheduleTypes(scheduleTypes).forEach((type) => {
    const item = document.createElement('div');
    item.className = 'legend-item';
    const parts = [formatScheduleTypeLabel(type)];
    if (type.timeRangeLabel && type.timeRangeLabel !== type.name) {
      parts.push(type.timeRangeLabel);
    }
    item.textContent = parts.join(' · ');
    legendTooltip.appendChild(item);
  });
}

function syncDetailCodeOptions(scheduleTypes = state.scheduleTypes, selectedValue = detailCode?.value || '') {
  if (!detailCode) return;
  detailCode.innerHTML = '';

  const defaultOption = document.createElement('option');
  defaultOption.value = '';
  defaultOption.textContent = '패턴대로';
  detailCode.appendChild(defaultOption);

  getScheduleTypes(scheduleTypes).forEach((type) => {
    const option = document.createElement('option');
    option.value = type.code;
    option.textContent = formatScheduleTypeLabel(type);
    detailCode.appendChild(option);
  });

  if (selectedValue && !getScheduleType(selectedValue, scheduleTypes)) {
    const fallbackOption = document.createElement('option');
    fallbackOption.value = selectedValue;
    fallbackOption.textContent = selectedValue;
    detailCode.appendChild(fallbackOption);
  }

  detailCode.value = selectedValue || '';
}

function renderDayDetailPanel(detail, date) {
  const activeTypes = detail?.scheduleTypes || state.scheduleTypes;
  const hasMemo = detail.memo || detail.anniversaryMemo || (detail.yearlyMemos && detail.yearlyMemos.length > 0);
  dayDetailPanel.innerHTML = `
    <strong>${formatKoreanDate(date)}</strong>
    <span>기본 일정: ${formatScheduleValue(detail.baseCode, activeTypes)}</span>
    <span>실제 일정: ${formatScheduleValue(detail.effectiveCode, activeTypes)}</span>
    <span>${detail.shiftLabel || ''}${detail.timeRange ? ` · ${detail.timeRange}` : ''}</span>
    <small>${[detail.memo, detail.anniversaryMemo, ...(detail.yearlyMemos || [])].filter(Boolean).join(' • ')}</small>
    ${hasMemo && detail.memoAuthor ? `<div class="memo-author-line">작성: ${detail.memoAuthor.nickname || detail.memoAuthor.name}${detail.updatedAt ? ` · ${formatDateTime(detail.updatedAt)}` : ''}</div>` : ''}
  `;
}

function getCurrentCalendar() {
  return state.calendars.find((c) => c.id === state.calendarId) || null;
}

function setCalendarCollections(res) {
  const calendars = Array.isArray(res?.calendars) ? res.calendars : [];
  const ownedCalendars = Array.isArray(res?.ownedCalendars)
    ? res.ownedCalendars
    : calendars.filter((calendar) => calendar.owned);
  const sharedCalendars = Array.isArray(res?.sharedCalendars)
    ? res.sharedCalendars
    : calendars.filter((calendar) => !calendar.owned);

  state.calendars = calendars;
  state.calendarGroups = {
    owned: ownedCalendars,
    shared: sharedCalendars
  };
}

function hideCalendarPicker() {
  if (calendarPickerSection) {
    calendarPickerSection.hidden = true;
  }
}

function showCalendarPicker() {
  if (!calendarPickerSection || !calendarPickerList) {
    return;
  }
  calendarPickerList.innerHTML = '';
  const groups = [
    { title: '내 캘린더', calendars: state.calendarGroups.owned },
    { title: '공유받은 캘린더', calendars: state.calendarGroups.shared }
  ].filter((group) => group.calendars.length > 0);

  groups.forEach((group) => {
    const groupEl = document.createElement('div');
    groupEl.className = 'calendar-picker-group';

    const title = document.createElement('div');
    title.className = 'calendar-picker-group-title';
    title.textContent = group.title;
    groupEl.appendChild(title);

    group.calendars.forEach((cal) => {
      const card = document.createElement('button');
      card.type = 'button';
      card.className = 'calendar-picker-card';

      const name = document.createElement('strong');
      name.textContent = cal.name;

      const meta = document.createElement('div');
      meta.className = 'calendar-picker-meta';
      meta.textContent = cal.owned ? '내가 소유' : `소유자 ${cal.ownerName || '-'}`;

      card.append(name, meta);

      if (!cal.owned) {
        const badge = document.createElement('span');
        badge.className = 'calendar-picker-badge';
        badge.textContent = cal.permission === 'EDIT' ? '편집 가능' : '보기 전용';
        card.appendChild(badge);
      }

      card.addEventListener('click', async () => {
        state.calendarId = cal.id;
        hideCalendarPicker();
        await bootstrap();
      });

      groupEl.appendChild(card);
    });

    calendarPickerList.appendChild(groupEl);
  });

  calendarSection.hidden = true;
  settingsMenuButton.hidden = true;
  patternManager.hide();
  if (patternOnboardingModal) patternOnboardingModal.hidden = true;
  if (patternCalendarModal) patternCalendarModal.hidden = true;
  calendarPickerSection.hidden = false;
}

function isCalendarEditable() {
  const current = getCurrentCalendar();
  if (!current) return true;
  return current.owned || current.editable || current.permission === 'EDIT';
}

const CACHE_TTL_MS = 3 * 60 * 1000; // 3분 캐시
const cacheStores = {
  settings: new Map(),
  shares: new Map(),
  calendar: new Map(),
  day: new Map()
};
const inflightStores = {
  settings: new Map(),
  shares: new Map(),
  calendar: new Map(),
  day: new Map()
};

let calendarRequestSeq = 0;
let hasPromptedPattern = false;
let lastPatternSettings = null;
let patternCalendarMode = 'shift'; // 'shift' | 'general'

function getCalendarTemplateConfig(template) {
  switch (template) {
    case 'shift':
      return {
        patternEnabled: true,
        createTitle: '교대 근무 캘린더 만들기',
        subtitle: '교대 패턴을 적용할 캘린더 이름을 정해주세요.',
        submitLabel: '교대 캘린더 준비',
        placeholder: '예: 내 근무표',
        hint: '교대 근무 사이클을 설정해서 자동으로 일정을 채우는 캘린더입니다.',
        defaultName: state.me?.name ? `${state.me.name}의 근무표` : '내 근무표'
      };
    case 'empty':
      return {
        patternEnabled: false,
        createTitle: '비어 있는 캘린더 만들기',
        subtitle: '일정은 나중에 추가하고 지금은 이름만 정해둘게요.',
        submitLabel: '빈 캘린더 만들기',
        placeholder: '예: 아이디어 보드',
        hint: '자동 패턴 없이 빈 상태로 시작하는 캘린더입니다.',
        defaultName: state.me?.name ? `${state.me.name}의 새 캘린더` : '새 캘린더'
      };
    case 'general':
    default:
      return {
        patternEnabled: false,
        createTitle: '일반 일정 캘린더 만들기',
        subtitle: '반복 패턴 없이 바로 기록을 시작할 캘린더 이름을 정해주세요.',
        submitLabel: '일정 캘린더 만들기',
        placeholder: '예: 개인 일정',
        hint: '개인 일정, 가족 일정, 공부 계획처럼 일반 용도로 쓰는 캘린더입니다.',
        defaultName: state.me?.name ? `${state.me.name}의 일정` : '내 일정'
      };
  }
}

function updateCreateCalendarTemplateUI() {
  if (!newCalendarTemplateInput) return;
  const config = getCalendarTemplateConfig(newCalendarTemplateInput.value);
  if (newCalendarTemplateHint) {
    newCalendarTemplateHint.textContent = config.hint;
  }
  if (newCalendarNameInput && !newCalendarNameInput.value) {
    newCalendarNameInput.placeholder = config.placeholder;
  }
  if (newCalendarTemplatePicker) {
    newCalendarTemplatePicker.querySelectorAll('.template-card').forEach((card) => {
      const active = card.dataset.template === newCalendarTemplateInput.value;
      card.classList.toggle('is-active', active);
      card.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
  }
}

function renderScheduleTypeEditor(scheduleTypes = state.scheduleTypes) {
  if (!scheduleTypeEditorSection || !scheduleTypeEditorList) {
    return;
  }
  const current = getCurrentCalendar();
  const isShift = !!current && !isGeneralCalendar(current);
  const canEdit = !!current && isCalendarEditable() && current.patternEnabled !== false;
  scheduleTypeEditorSection.hidden = !current || current.patternEnabled === false;
  scheduleTypeEditorList.innerHTML = '';

  getScheduleTypes(scheduleTypes).forEach((type) => {
    const card = document.createElement('div');
    card.className = 'schedule-type-card';
    card.dataset.code = type.code;
    card.dataset.originalCode = type.code;
    card.dataset.existing = 'true';

    const timeBlock = type.defaultOff
      ? '<div class="schedule-type-note">휴무 성격의 코드는 시간을 사용하지 않습니다.</div>'
      : `<div class="time-row">
          <label>
            시작
            <input type="time" data-field="startTime" value="${type.startTime || ''}" ${canEdit ? '' : 'disabled'} />
          </label>
          <label>
            종료
            <input type="time" data-field="endTime" value="${type.endTime || ''}" ${canEdit ? '' : 'disabled'} />
          </label>
        </div>`;

    card.innerHTML = `
      <div class="schedule-type-card-header">
        <div class="schedule-type-swatch" style="background:${type.color || '#94A3B8'}"></div>
        <div>
          <strong>${type.name}</strong>
          <div class="schedule-type-code">${type.code}</div>
        </div>
        ${canEdit ? '<button type="button" class="schedule-type-remove-button secondary" data-action="remove-type">삭제</button>' : ''}
      </div>
      <div class="schedule-type-grid">
        <label>
          코드
          <input type="text" data-field="code" value="${type.code}" maxlength="32" ${canEdit ? '' : 'disabled'} />
        </label>
        <label>
          이름
          <input type="text" data-field="name" value="${type.name || ''}" maxlength="100" ${canEdit ? '' : 'disabled'} />
        </label>
        <label>
          색상
          <input type="color" data-field="color" value="${type.color || '#94A3B8'}" ${canEdit ? '' : 'disabled'} />
        </label>
      </div>
      ${timeBlock}
    `;
    scheduleTypeEditorList.appendChild(card);
  });

  if (saveScheduleTypesButton) {
    saveScheduleTypesButton.hidden = !canEdit;
    saveScheduleTypesButton.disabled = !canEdit;
  }
  if (addScheduleTypeButton) {
    addScheduleTypeButton.hidden = !canEdit;
    addScheduleTypeButton.disabled = !canEdit;
  }
  if (resetPatternButton) {
    resetPatternButton.hidden = !isShift;
  }
}

function isGeneralCalendar(current = getCurrentCalendar()) {
  return !!current && current.patternEnabled !== false && Array.isArray(state.weeklyRules) && state.weeklyRules.length > 0;
}

function renderWeeklyRuleEditor(weeklyRules = state.weeklyRules) {
  if (!weeklyRuleEditorSection || !weeklyRuleEditorList) {
    return;
  }
  const current = getCurrentCalendar();
  const visible = isGeneralCalendar(current);
  weeklyRuleEditorSection.hidden = !visible;
  if (!visible) {
    weeklyRuleEditorList.innerHTML = '';
    if (saveWeeklyRulesButton) {
      saveWeeklyRulesButton.hidden = true;
      saveWeeklyRulesButton.disabled = true;
    }
    return;
  }
  const canEdit = !!current && isCalendarEditable();
  const ruleMap = new Map((weeklyRules || []).map((rule) => [rule.dayOfWeek, rule.scheduleTypeCode]));
  const weekdayLabels = ['월요일', '화요일', '수요일', '목요일', '금요일', '토요일', '일요일'];
  weeklyRuleEditorList.innerHTML = '';

  weekdayLabels.forEach((label, index) => {
    const dayOfWeek = index + 1;
    const wrap = document.createElement('div');
    wrap.className = 'schedule-type-card';
    const options = getScheduleTypes().map((type) => `
      <option value="${type.code}" ${ruleMap.get(dayOfWeek) === type.code ? 'selected' : ''}>${formatScheduleTypeLabel(type)}</option>
    `).join('');
    wrap.innerHTML = `
      <div class="schedule-type-card-header">
        <div>
          <strong>${label}</strong>
          <div class="schedule-type-code">기본 일정 코드 선택</div>
        </div>
      </div>
      <label>
        일정
        <select data-day-of-week="${dayOfWeek}" ${canEdit ? '' : 'disabled'}>
          ${options}
        </select>
      </label>
    `;
    weeklyRuleEditorList.appendChild(wrap);
  });

  if (saveWeeklyRulesButton) {
    saveWeeklyRulesButton.hidden = !canEdit;
    saveWeeklyRulesButton.disabled = !canEdit;
  }
}

function collectScheduleTypePayload() {
  if (!scheduleTypeEditorList) {
    return [];
  }
  return Array.from(scheduleTypeEditorList.querySelectorAll('.schedule-type-card'))
    .map((card) => ({
      originalCode: (card.dataset.originalCode || '').trim().toUpperCase() || null,
      code: (card.querySelector('[data-field="code"]')?.value || card.dataset.code || '').trim().toUpperCase(),
      name: card.querySelector('[data-field="name"]')?.value?.trim(),
      color: card.querySelector('[data-field="color"]')?.value || '#94A3B8',
      startTime: card.querySelector('[data-field="startTime"]')?.value || null,
      endTime: card.querySelector('[data-field="endTime"]')?.value || null
    }))
    .filter((item) => item.code && item.name);
}

async function saveScheduleTypes() {
  if (!state.calendarId) {
    showToast('캘린더를 먼저 선택하세요.');
    return;
  }
  try {
    const scheduleTypes = await api.updateScheduleTypes(state.calendarId, {
      scheduleTypes: collectScheduleTypePayload()
    });
    setScheduleTypes(scheduleTypes || []);
    if (state.calendarData) {
      state.calendarData.scheduleTypes = scheduleTypes || [];
    }
    invalidateCache('calendar');
    invalidateCache('day');
    showToast('근무 코드 설정이 저장되었습니다.');
    await loadCalendar(state.year, state.month, { force: true });
    if (state.selectedDate) {
      await selectDay(state.selectedDate);
    }
  } catch (e) {
    showToast('근무 코드 설정 저장 실패: ' + formatScheduleTypeSaveError(e.message || '오류'));
  }
}

function appendNewScheduleTypeCard() {
  if (!scheduleTypeEditorList) {
    return;
  }
  const index = scheduleTypeEditorList.querySelectorAll('.schedule-type-card').length + 1;
  const card = document.createElement('div');
  card.className = 'schedule-type-card';
  card.dataset.existing = 'false';
  card.innerHTML = `
    <div class="schedule-type-card-header">
      <div class="schedule-type-swatch" style="background:#22C55E"></div>
      <div>
        <strong>새 코드</strong>
        <div class="schedule-type-code">직접 만든 일정 코드</div>
      </div>
      <button type="button" class="schedule-type-remove-button secondary" data-action="remove-type">삭제</button>
    </div>
    <div class="schedule-type-grid">
      <label>
        코드
        <input type="text" data-field="code" value="CUSTOM${index}" maxlength="32" />
      </label>
      <label>
        이름
        <input type="text" data-field="name" value="새 일정 ${index}" maxlength="100" />
      </label>
      <label>
        색상
        <input type="color" data-field="color" value="#22C55E" />
      </label>
    </div>
    <div class="time-row">
      <label>
        시작
        <input type="time" data-field="startTime" value="" />
      </label>
      <label>
        종료
        <input type="time" data-field="endTime" value="" />
      </label>
    </div>
    <div class="schedule-type-note">시간을 비워두면 휴식/메모 성격의 코드로 저장됩니다.</div>
  `;
  scheduleTypeEditorList.appendChild(card);
}

function removeScheduleTypeCard(button) {
  const card = button?.closest('.schedule-type-card');
  if (!card || !scheduleTypeEditorList) {
    return;
  }
  if (scheduleTypeEditorList.querySelectorAll('.schedule-type-card').length <= 1) {
    showToast('코드는 최소 1개 이상 필요합니다.');
    return;
  }
  card.remove();
}

function formatScheduleTypeSaveError(message) {
  if (!message) {
    return '오류';
  }
  if (message.startsWith('schedule_type_in_use:')) {
    const code = message.split(':')[1] || '';
    return `${code} 코드는 현재 패턴이나 일정에서 사용 중이라 삭제할 수 없습니다.`;
  }
  if (message === 'schedule_types_required') {
    return '코드는 최소 1개 이상 필요합니다.';
  }
  return message;
}

function collectWeeklyRulesPayload() {
  if (!weeklyRuleEditorList) {
    return [];
  }
  return Array.from(weeklyRuleEditorList.querySelectorAll('select[data-day-of-week]')).map((select) => ({
    dayOfWeek: Number(select.dataset.dayOfWeek),
    scheduleTypeCode: select.value
  }));
}

async function saveWeeklyRules() {
  if (!state.calendarId) {
    showToast('캘린더를 먼저 선택하세요.');
    return;
  }
  try {
    const weeklyRules = await api.updateWeeklyRules(state.calendarId, {
      rules: collectWeeklyRulesPayload()
    });
    setWeeklyRules(weeklyRules || []);
    invalidateCache('calendar');
    invalidateCache('day');
    showToast('주간 일정이 저장되었습니다.');
    await loadCalendar(state.year, state.month, { force: true });
    if (state.selectedDate) {
      await selectDay(state.selectedDate);
    }
  } catch (e) {
    showToast('주간 일정 저장 실패: ' + (e.message || '오류'));
  }
}

function calendarCacheKey(calendarId, year, month) {
  return `${calendarId || 'default'}:${year}-${month}`;
}

function dayCacheKey(calendarId, date) {
  return `${calendarId || 'default'}:${date}`;
}

function isFresh(entry) {
  return entry && Date.now() - entry.fetchedAt < CACHE_TTL_MS;
}

function invalidateCache(kind, key) {
  if (key === undefined) {
    cacheStores[kind].clear();
    inflightStores[kind].clear();
    return;
  }
  cacheStores[kind].delete(key);
  inflightStores[kind].delete(key);
}

function invalidateDayCache(date, calendarId = state.calendarId) {
  if (!date || !calendarId) {
    return;
  }
  invalidateCache('day', dayCacheKey(calendarId, date));
}

async function fetchWithCache(kind, key, fetcher, { force = false } = {}) {
  const store = cacheStores[kind];
  const inflight = inflightStores[kind];
  const cached = store.get(key);

  if (!force && isFresh(cached)) {
    return cached.data;
  }

  if (inflight.has(key)) {
    return inflight.get(key);
  }

  const promise = fetcher()
    .then((data) => {
      store.set(key, { data, fetchedAt: Date.now() });
      inflight.delete(key);
      return data;
    })
    .catch((err) => {
      inflight.delete(key);
      throw err;
    });

  inflight.set(key, promise);
  return promise;
}

function debounce(fn, delay = 200) {
  let timer = null;
  return (...args) => {
    clearTimeout(timer);
    timer = setTimeout(() => fn(...args), delay);
  };
}

function startGlobalLoading() {
  loadingCounter++;
  if (loadingOverlay) {
    loadingOverlay.hidden = false;
    loadingOverlay.style.display = 'flex';
  }
  if (loadingHideTimer) {
    clearTimeout(loadingHideTimer);
    loadingHideTimer = null;
  }
  if (loadingFailSafeTimer) {
    clearTimeout(loadingFailSafeTimer);
  }
  loadingFailSafeTimer = setTimeout(() => {
    loadingCounter = 0;
    if (loadingOverlay) {
      loadingOverlay.hidden = true;
      loadingOverlay.style.display = 'none';
    }
  }, 15000);
  return () => finishGlobalLoading();
}

function finishGlobalLoading(force = false) {
  if (force) {
    loadingCounter = 0;
  } else {
    loadingCounter = Math.max(loadingCounter - 1, 0);
  }
  if (loadingCounter === 0) {
    if (loadingHideTimer) {
      clearTimeout(loadingHideTimer);
    }
    loadingHideTimer = setTimeout(() => {
      if (loadingCounter === 0 && loadingOverlay) {
        loadingOverlay.hidden = true;
        loadingOverlay.style.display = 'none';
      }
    }, 150);
    if (loadingFailSafeTimer) {
      clearTimeout(loadingFailSafeTimer);
      loadingFailSafeTimer = null;
    }
  }
}

function scheduleIdle(task) {
  if (typeof window.requestIdleCallback === 'function') {
    window.requestIdleCallback(task, { timeout: 1500 });
  } else {
    setTimeout(task, 200);
  }
}

async function bootstrap() {
  const endLoading = startGlobalLoading();
  try {
    if (!state.calendarId) {
      const calendarsRes = await api.listCalendars();
      setCalendarCollections(calendarsRes);
      renderCalendarSelector();
      renderInvites();

      if (state.calendars.length > 1) {
        showCalendarPicker();
        return;
      }

      state.calendarId = calendarsRes.defaultCalendarId || (state.calendars[0] ? state.calendars[0].id : null);
      if (!state.calendarId) {
        hideCalendarPicker();
        handlePatternOnboarding({});
        return;
      }
    }

    // 우선 서버에서 한 번에 내려주는 bootstrap API 시도
    try {
      const bootstrapData = await api.bootstrap({
        year: state.year,
        month: state.month,
        calendarId: state.calendarId
      });

      // 캘린더 목록/기본 캘린더
      const calendarsRes = bootstrapData.calendars || {};
      setCalendarCollections(calendarsRes);
      state.calendarId = state.calendarId
        || calendarsRes.defaultCalendarId
        || (state.calendars[0] ? state.calendars[0].id : null);
      const currentCalendar = state.calendars.find((c) => c.id === state.calendarId);
      state.usePattern = currentCalendar ? currentCalendar.patternEnabled !== false : true;
      hideCalendarPicker();
      renderCalendarSelector();
      renderInvites();

      // 사용자
      if (bootstrapData.me) {
        state.me = bootstrapData.me;
        if (colorPicker && bootstrapData.me.colorTag) {
          colorPicker.value = bootstrapData.me.colorTag;
        }
      }

      // 설정
      const settings = bootstrapData.settings || {};
      state.patternConfigured = state.usePattern ? (settings.configured || false) : true;
      if (!state.patternConfigured) {
        handlePatternOnboarding(settings);
        return;
      }

      // 달력 데이터
      if (bootstrapData.calendar) {
        const calData = bootstrapData.calendar;
        state.year = calData.year;
        state.month = calData.month;
        calData.calendarId = state.calendarId;
        state.calendarData = calData;
        setScheduleTypes(calData.scheduleTypes || []);
        cacheStores.calendar.set(calendarCacheKey(state.calendarId, calData.year, calData.month), {
          data: calData,
          fetchedAt: Date.now()
        });
        calendarTitle.textContent = `${calData.year}년 ${calData.month}월`;
        renderCalendar({
          gridEl: calendarGrid,
          summaryEl: summaryList,
          data: calData,
          today: getLocalTodayIso(),
          selectedDate: state.selectedDate,
          onSelectDay: (date) => selectDay(date),
          usePattern: state.usePattern
        });
      }

      // 공유 목록/캐시
      if (bootstrapData.shares && state.calendarId) {
        cacheStores.shares.set(state.calendarId, { data: bootstrapData.shares, fetchedAt: Date.now() });
        renderCalendarSelector();
        renderInvites();
        await loadShares();
      }

      patternManager.hide();
      calendarSection.hidden = false;
      settingsMenuButton.hidden = false;

      scheduleIdle(() => {
        prefetchAdjacentMonths(state.year, state.month, state.calendarId);
      });
      return;
    } catch (bootstrapError) {
      // fallback to 기존 로직
      console.warn('bootstrap API failed, fallback to individual calls', bootstrapError);
    }

    // fallback 경로: 필수 데이터만 먼저
    await loadCalendars();
    hideCalendarPicker();
    const [settings] = await Promise.all([
      loadPatternSettings({ force: true }),
      loadCalendar(state.year, state.month, { force: true, prefetch: false }).catch(() => null),
      loadMeColor().catch(() => null)
    ]);

    if (!state.patternConfigured) {
      handlePatternOnboarding(settings);
      return;
    }
    patternManager.hide();
    calendarSection.hidden = false;
    settingsMenuButton.hidden = false;

    // 부가 데이터와 prefetch는 렌더 이후로 지연 실행
    scheduleIdle(() => {
      loadShares({ force: true }).catch(() => null);
      prefetchAdjacentMonths(state.year, state.month, state.calendarId);
    });
  } finally {
    endLoading();
  }
}

async function loadCalendars() {
  const res = await api.listCalendars();
  setCalendarCollections(res);
  state.calendarId = res.defaultCalendarId || (state.calendars[0] ? state.calendars[0].id : null);
  state.selectedDate = null;
  state.calendarData = null;
  if (!state.calendarId) {
    setScheduleTypes([]);
    setWeeklyRules([]);
  }
  renderCalendarSelector();
  renderInvites();
  return res;
}

async function loadMeColor() {
  if (!colorPicker) return null;
  try {
    const me = await api.me();
    state.me = me;
    if (me && me.colorTag) {
      colorPicker.value = me.colorTag;
    }
    return me;
  } catch (e) {
    // ignore
    return null;
  }
}

async function loadPatternSettings({ force = false } = {}) {
  if (!state.calendarId) return {};
  const key = state.calendarId;
  const settings = await fetchWithCache('settings', key, () => api.getSettings(state.calendarId), { force });
  const currentCalendar = state.calendars.find((c) => c.id === state.calendarId);
  const usePattern = currentCalendar ? currentCalendar.patternEnabled !== false : true;
  state.patternConfigured = usePattern ? !!settings.configured : true;
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

function updateCalendarEmptyState() {
  if (!calendarEmpty) return;
  const hasCalendar = !!state.calendarId;
  calendarEmpty.hidden = hasCalendar;
  if (calendarGrid) calendarGrid.hidden = !hasCalendar;
  if (summaryList) summaryList.hidden = !hasCalendar;
  if (prevMonthBtn) prevMonthBtn.disabled = !hasCalendar;
  if (nextMonthBtn) nextMonthBtn.disabled = !hasCalendar;
  if (!hasCalendar) {
    if (calendarTitle) {
      calendarTitle.textContent = '';
    }
    if (calendarGrid) {
      calendarGrid.innerHTML = '';
    }
    if (summaryList) {
      const summaryItems = summaryList.querySelectorAll('.summary-item');
      summaryItems.forEach(item => item.remove());
    }
    if (legendTooltip) {
      legendTooltip.hidden = true;
    }
  }
}

function renderCalendarSelector() {
  if (!calendarSelector || !calendarSelectorList) return;
  calendarSelectorList.innerHTML = '';
  const groups = [
    { title: '내 캘린더', calendars: state.calendarGroups.owned },
    { title: '공유받은 캘린더', calendars: state.calendarGroups.shared }
  ].filter((group) => group.calendars.length > 0);

  groups.forEach((group) => {
    const section = document.createElement('div');
    section.className = 'calendar-selector-group';

    const title = document.createElement('div');
    title.className = 'calendar-selector-group-title';
    title.textContent = group.title;
    section.appendChild(title);

    group.calendars.forEach((cal) => {
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
          patternManager.show(60, settings);
          calendarSection.hidden = true;
          settingsMenuButton.hidden = true;
          return;
        }
        patternManager.hide();
        calendarSection.hidden = false;
        settingsMenuButton.hidden = false;
        await Promise.all([
          loadCalendar(state.year, state.month),
          loadShares()
        ]);
        renderCalendarSelector();
      });
      section.appendChild(item);
    });

    calendarSelectorList.appendChild(section);
  });
  const current = state.calendars.find(c => c.id === state.calendarId);
  state.usePattern = current ? current.patternEnabled !== false : false;
  const currentLabel = current
    ? `${current.name}${current.owned ? '' : ` · ${current.ownerName || ''}`}`
    : '캘린더 선택';
  calendarSelectorButton.textContent = currentLabel;
  syncCalendarEditForm();
  updateCalendarEmptyState();
}

async function loadShares({ force = false } = {}) {
  if (!shareList) return;
  const current = state.calendars.find(c => c.id === state.calendarId);
  if (!current || !current.owned) {
    shareList.innerHTML = '';
    return;
  }
  const key = state.calendarId;
  const shares = await fetchWithCache('shares', key, () => api.listShares(state.calendarId), { force });
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
        invalidateCache('shares', cal.id);
        invalidateCache('shares', state.calendarId);
        await loadShares({ force: true });
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
        invalidateCache('shares', cal.id);
        invalidateCache('shares', state.calendarId);
        await loadShares({ force: true });
      } catch (e) {
        showToast('거절 실패: ' + (e.message || '오류'));
      }
    });
    actions.append(acceptBtn, rejectBtn);
    item.appendChild(actions);
    inviteList.appendChild(item);
  });
}

async function fetchDay(date, { calendarId = state.calendarId, force = false } = {}) {
  const targetCalendarId = calendarId ?? state.calendarId;
  const key = dayCacheKey(targetCalendarId, date);
  return fetchWithCache('day', key, () => api.getDay(date, targetCalendarId), { force });
}

function getCachedCalendarData(year, month, calendarId = state.calendarId) {
  const targetCalendarId = calendarId ?? state.calendarId;
  if (state.calendarData && state.calendarData.year === year && state.calendarData.month === month && state.calendarData.calendarId === targetCalendarId) {
    return state.calendarData;
  }
  const key = calendarCacheKey(targetCalendarId, year, month);
  const cached = cacheStores.calendar.get(key);
  return isFresh(cached) ? cached.data : null;
}

async function fetchCalendar(year, month, { calendarId = state.calendarId, force = false } = {}) {
  const targetCalendarId = calendarId ?? state.calendarId;
  const key = calendarCacheKey(targetCalendarId, year, month);
  const data = await fetchWithCache('calendar', key, () => api.getCalendar(year, month, targetCalendarId), { force });
  data.calendarId = targetCalendarId;
  if (Array.isArray(data.scheduleTypes)) {
    setScheduleTypes(data.scheduleTypes);
  }
  return data;
}

function prefetchAdjacentMonths(year, month, calendarId = state.calendarId) {
  const prevDate = new Date(year, month - 2, 1);
  const nextDate = new Date(year, month, 1);
  // 이전/다음 달을 병렬로 사전 로드
  Promise.all([
    fetchCalendar(prevDate.getFullYear(), prevDate.getMonth() + 1, { calendarId }),
    fetchCalendar(nextDate.getFullYear(), nextDate.getMonth() + 1, { calendarId })
  ]).catch(() => {
    // 사전 로드 실패는 무시
  });
}

async function loadCalendar(year, month, { calendarId = state.calendarId, force = false, prefetch = true } = {}) {
  if (!calendarId) return null;
  const requestId = ++calendarRequestSeq;
  const data = await fetchCalendar(year, month, { calendarId, force });
  if (requestId !== calendarRequestSeq) {
    return data; // 더 최신 요청이 있으면 렌더링 생략
  }
  state.year = data.year;
  state.month = data.month;
  state.calendarData = data;
  calendarTitle.textContent = `${data.year}년 ${data.month}월`;
  renderCalendar({
    gridEl: calendarGrid,
    summaryEl: summaryList,
    data,
    today: getLocalTodayIso(),
    selectedDate: state.selectedDate,
    onSelectDay: (date) => selectDay(date),
    usePattern: state.usePattern
  });

  if (prefetch) {
    prefetchAdjacentMonths(data.year, data.month, calendarId);
  }

  return data;
}

function setMemoFormEnabled(enabled) {
  if (!memoAddForm) return;
  memoAddForm.classList.toggle('disabled', !enabled);
  const submitBtn = memoAddForm.querySelector('button[type="submit"]');
  if (submitBtn) {
    submitBtn.disabled = !enabled;
  }
  if (newMemoText) {
    newMemoText.disabled = !enabled;
  }
}

function renderMemos(memos, canEdit = true) {
  memoList.innerHTML = '';
  if (!memos || memos.length === 0) {
    memoList.innerHTML = '<div class="memo-empty">메모가 없습니다</div>';
    return;
  }

  memos.forEach(memo => {
    const item = document.createElement('div');
    item.className = 'memo-item';

    const content = document.createElement('div');
    content.className = 'memo-content';

    const text = document.createElement('div');
    text.className = 'memo-text';
    text.textContent = memo.memo;

    const meta = document.createElement('div');
    meta.className = 'memo-meta';
    const authorName = memo.author.nickname || memo.author.name;
    const timeStr = memo.updatedAt ? formatDateTime(memo.updatedAt) : '';
    meta.textContent = `${authorName}${timeStr ? ' · ' + timeStr : ''}`;

    content.append(text, meta);
    item.append(content);

    if (memo.isOwn && canEdit) {
      const deleteBtn = document.createElement('button');
      deleteBtn.className = 'memo-delete-btn';
      deleteBtn.textContent = '삭제';
      deleteBtn.type = 'button';
      deleteBtn.addEventListener('click', () => deleteMemo(memo.id));
      item.append(deleteBtn);
    }

    memoList.append(item);
  });
}

async function deleteMemo(memoId) {
  if (!isCalendarEditable()) {
    showToast('읽기 전용 캘린더입니다.');
    return;
  }
  if (!confirm('메모를 삭제하시겠습니까?')) return;

  try {
    await api.deleteMemo(state.selectedDate, memoId, state.calendarId);
    invalidateDayCache(state.selectedDate);
    await selectDay(state.selectedDate);
    showToast('메모가 삭제되었습니다.');
  } catch (e) {
    showToast('메모 삭제 실패: ' + (e.message || '오류'));
  }
}

async function selectDay(date) {
  try {
    state.selectedDate = date;
    const activeCalendarId = state.calendarId;
    const canEdit = isCalendarEditable();

    // 날짜 상세 조회와 캘린더 데이터를 병렬로 가져오기
    const cachedCalendar = getCachedCalendarData(state.year, state.month);
    const [detail, calendarData] = await Promise.all([
      fetchDay(date, { calendarId: activeCalendarId }),
      cachedCalendar ? Promise.resolve(cachedCalendar) : fetchCalendar(state.year, state.month, { calendarId: activeCalendarId })
    ]);

    dayModal.hidden = false;
    state.calendarData = calendarData;
    setScheduleTypes(detail.scheduleTypes || calendarData.scheduleTypes || state.scheduleTypes);
    renderDayDetailPanel(detail, date);
    syncDetailCodeOptions(detail.scheduleTypes || calendarData.scheduleTypes, detail.effectiveCode || '');

    // 다중 사용자 메모 렌더링
    renderMemos(detail.dayMemos || [], canEdit);

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

    const hasAnniversary = detail.anniversaryMemo || (detail.yearlyMemos && detail.yearlyMemos.length > 0);
    clearAnniversaryButton.style.display = canEdit && hasAnniversary ? 'block' : 'none';
    clearAnniversaryButton.disabled = !canEdit;

    // 패턴 사용 안 하는 캘린더거나 읽기 전용이면 코드 선택 비활성
    if (state.usePattern === false || !canEdit) {
      if (state.usePattern === false) {
        detailCode.value = '';
      }
      detailCode.disabled = true;
      patternFields.forEach(el => el.setAttribute('disabled', 'true'));
      if (patternDisabledHint) {
        patternDisabledHint.hidden = state.usePattern !== false;
      }
    } else {
      detailCode.disabled = false;
      patternFields.forEach(el => el.removeAttribute('disabled'));
      if (patternDisabledHint) {
        patternDisabledHint.hidden = true;
      }
    }

    // 메모 추가/수정 가능 여부 표시
    setMemoFormEnabled(canEdit);

    // 선택된 날짜를 표시하기 위해 캘린더 렌더링
    renderCalendar({
      gridEl: calendarGrid,
      summaryEl: summaryList,
      data: calendarData,
      today: getLocalTodayIso(),
      selectedDate: state.selectedDate,
      onSelectDay: (date) => selectDay(date),
      usePattern: state.usePattern
    });
  } catch (e) {
    console.error('Failed to load day detail', e);
    showToast(e.message || '날짜 정보를 불러오지 못했습니다.');
  }
}

function closeModal() {
  dayModal.hidden = true;
  state.selectedDate = null;
  loadCalendar(state.year, state.month);
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
      invalidateCache('shares', state.calendarId);
      await loadShares({ force: true });
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
    const previousCalendars = [...state.calendars];
    const name = (newCalendarNameInput.value || '').trim();
    if (!name) {
      alert('캘린더 이름을 입력하세요.');
      return;
    }
    const template = newCalendarTemplateInput?.value || 'general';
    const patternEnabled = getCalendarTemplateConfig(template).patternEnabled;
    try {
      const res = await api.createCalendar({ name, patternEnabled, templateType: template });
      setCalendarCollections(res);
      state.calendarId = findNewCalendarId(previousCalendars, state.calendars)
        || res.defaultCalendarId
        || (state.calendars[0] ? state.calendars[0].id : null);
      invalidateCache('settings');
      invalidateCache('calendar');
      invalidateCache('shares');
      renderCalendarSelector();
      renderInvites();
      newCalendarNameInput.value = '';
      if (newCalendarTemplateInput) {
        newCalendarTemplateInput.value = 'general';
        updateCreateCalendarTemplateUI();
      }
      showToast('캘린더가 생성되었습니다.');
      const settings = await loadPatternSettings({ force: true });
      if (!state.patternConfigured) {
        patternManager.hide();
        calendarSection.hidden = true;
        settingsMenuButton.hidden = false;
        settingsModal.hidden = false;
        showSettingsView('calendar-pattern');
        return;
      }
      patternManager.hide();
      calendarSection.hidden = false;
      settingsMenuButton.hidden = false;
      await Promise.all([
        loadCalendar(state.year, state.month, { force: true }),
        loadShares({ force: true })
      ]);
      if (settingsModal) {
        settingsModal.hidden = true;
      }
    } catch (e) {
      showToast('캘린더 생성 실패: ' + (e.message || '오류'));
    }
  });
}

if (newCalendarTemplatePicker && newCalendarTemplateInput) {
  newCalendarTemplatePicker.querySelectorAll('.template-card').forEach((card) => {
    card.addEventListener('click', () => {
      newCalendarTemplateInput.value = card.dataset.template || 'general';
      updateCreateCalendarTemplateUI();
    });
  });
}

if (addScheduleTypeButton) {
  addScheduleTypeButton.addEventListener('click', () => {
    appendNewScheduleTypeCard();
  });
}

if (scheduleTypeEditorList) {
  scheduleTypeEditorList.addEventListener('click', (event) => {
    const button = event.target.closest('[data-action="remove-type"]');
    if (!button) {
      return;
    }
    removeScheduleTypeCard(button);
  });
}

if (saveWeeklyRulesButton) {
  saveWeeklyRulesButton.addEventListener('click', async () => {
    await saveWeeklyRules();
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
      setCalendarCollections(res);
      const stillExists = state.calendars.find(c => c.id === state.calendarId);
      if (!stillExists) {
        state.calendarId = res.defaultCalendarId || (state.calendars[0] ? state.calendars[0].id : null);
      }
      invalidateCache('settings', state.calendarId);
      invalidateCache('calendar');
      renderCalendarSelector();
      renderInvites();
      showToast('캘린더가 수정되었습니다.');
      const settings = await loadPatternSettings({ force: true });
      if (!state.patternConfigured) {
        patternManager.hide();
        calendarSection.hidden = true;
        settingsMenuButton.hidden = false;
        settingsModal.hidden = false;
        showSettingsView('calendar-pattern');
        return;
      }
      patternManager.hide();
      calendarSection.hidden = false;
      settingsMenuButton.hidden = false;
      await Promise.all([
        loadCalendar(state.year, state.month, { force: true }),
        loadShares({ force: true })
      ]);
      if (settingsModal) {
        settingsModal.hidden = true;
      }
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
      setCalendarCollections(res);
      state.calendarId = res.defaultCalendarId || (state.calendars[0] ? state.calendars[0].id : null);
      invalidateCache('settings');
      invalidateCache('calendar');
      invalidateCache('shares');
      renderCalendarSelector();
      renderInvites();
      showToast('캘린더가 삭제되었습니다.');
      if (state.calendarId) {
        const settings = await loadPatternSettings({ force: true });
        if (!state.patternConfigured) {
          patternManager.show(60, settings);
          calendarSection.hidden = true;
          settingsMenuButton.hidden = true;
        } else {
          patternManager.hide();
          calendarSection.hidden = false;
          settingsMenuButton.hidden = false;
          await Promise.all([
            loadCalendar(state.year, state.month, { force: true }),
            loadShares({ force: true })
          ]);
        }
      } else {
        calendarGrid.innerHTML = '';
        summaryList.innerHTML = '';
        setScheduleTypes([]);
        setWeeklyRules([]);
        if (shareList) shareList.innerHTML = '';
      }
      if (settingsModal) {
        settingsModal.hidden = true;
      }
    } catch (e) {
      showToast('캘린더 삭제 실패: ' + (e.message || '오류'));
    }
  });
}

function guessPatternCalendarName() {
  return getCalendarTemplateConfig('shift').defaultName;
}

function guessCalendarName(template = 'general') {
  const current = state.calendars.find((c) => c.id === state.calendarId && c.owned);
  if (current && current.name) return current.name;
  return getCalendarTemplateConfig(template).defaultName;
}

function findNewCalendarId(previousCalendars, nextCalendars) {
  const previousIds = new Set((previousCalendars || []).map((calendar) => calendar.id));
  const created = (nextCalendars || []).find((calendar) => !previousIds.has(calendar.id));
  return created?.id || null;
}

function openPatternChoice(settings) {
  hasPromptedPattern = true;
  patternManager.hide();
  hideCalendarPicker();
  calendarSection.hidden = true;
  settingsMenuButton.hidden = true;
  dayModal.hidden = true;
  settingsModal.hidden = true;
  if (patternOnboardingModal) {
    patternOnboardingModal.hidden = false;
    if (patternCalendarModal) {
      patternCalendarModal.hidden = true;
    }
    if (patternCalendarNameInput) {
      patternCalendarNameInput.value = guessCalendarName('shift');
    }
    if (patternOnboardingNoCalendar) {
      patternOnboardingNoCalendar.hidden = !!state.calendarId;
    }
  } else {
    const baseSettings = settings || lastPatternSettings;
    patternManager.show(60, baseSettings);
  }
}

function startWithoutCalendar() {
  hasPromptedPattern = true;
  state.calendarId = null;
  state.usePattern = false;
  state.patternConfigured = true;
  setScheduleTypes([]);
  setWeeklyRules([]);
  patternManager.hide();
  hideCalendarPicker();
  if (patternOnboardingModal) patternOnboardingModal.hidden = true;
  if (patternCalendarModal) patternCalendarModal.hidden = true;
  calendarSection.hidden = false;
  settingsMenuButton.hidden = false;
  renderCalendarSelector();
  renderInvites();
  updateCalendarEmptyState();
}

function handlePatternOnboarding(settings) {
  lastPatternSettings = settings || lastPatternSettings;
  if (hasPromptedPattern) {
    return;
  }
  openPatternChoice(settings);
}

async function ensureCalendar(name, patternEnabled) {
  const previousCalendars = [...state.calendars];
  const template = patternCalendarMode === 'general' || patternCalendarMode === 'empty'
    ? patternCalendarMode
    : (patternEnabled ? 'shift' : 'general');
  const fallbackName = guessCalendarName(template);
  const targetName = (name || fallbackName).trim() || fallbackName;
  const res = await api.createCalendar({ name: targetName, patternEnabled, templateType: template });
  setCalendarCollections(res);
  state.calendarId = findNewCalendarId(previousCalendars, state.calendars)
    || res.defaultCalendarId
    || (state.calendars[0] ? state.calendars[0].id : null);
  invalidateCache('settings');
  invalidateCache('calendar');
  invalidateCache('shares');
  renderCalendarSelector();
  return res;
}

function openPatternCalendarModal(mode = 'shift') {
  patternCalendarMode = mode;
  const config = getCalendarTemplateConfig(mode);
  if (patternCalendarModal) {
    patternCalendarModal.hidden = false;
  }
  if (patternOnboardingModal) {
    patternOnboardingModal.hidden = true;
  }
  if (!patternCalendarNameInput.value) {
    patternCalendarNameInput.value = guessCalendarName(mode);
  }
  if (patternCalendarTitle && patternCalendarSubtitle && patternCalendarSubmit) {
    patternCalendarTitle.textContent = config.createTitle;
    patternCalendarSubtitle.textContent = config.subtitle;
    patternCalendarSubmit.textContent = config.submitLabel;
  }
  if (patternCalendarNameInput) {
    patternCalendarNameInput.placeholder = config.placeholder;
  }
  patternCalendarNameInput.focus();
}

if (patternOnboardingUse) {
  patternOnboardingUse.addEventListener('click', () => {
    openPatternCalendarModal('shift');
  });
}

if (patternOnboardingSkip) {
  patternOnboardingSkip.addEventListener('click', () => {
    openPatternCalendarModal('general');
  });
}

if (patternOnboardingNoCalendar) {
  patternOnboardingNoCalendar.addEventListener('click', () => {
    openPatternCalendarModal('empty');
  });
}

if (patternOnboardingClose) {
  patternOnboardingClose.addEventListener('click', () => {
    if (patternOnboardingModal) patternOnboardingModal.hidden = true;
  });
}

if (saveScheduleTypesButton) {
  saveScheduleTypesButton.addEventListener('click', async () => {
    await saveScheduleTypes();
  });
}

if (patternCalendarBack) {
  patternCalendarBack.addEventListener('click', () => {
    if (patternCalendarModal) patternCalendarModal.hidden = true;
    if (patternOnboardingModal) patternOnboardingModal.hidden = false;
  });
}

if (patternCalendarClose) {
  patternCalendarClose.addEventListener('click', () => {
    if (patternCalendarModal) patternCalendarModal.hidden = true;
    if (patternOnboardingModal) patternOnboardingModal.hidden = false;
  });
}

if (patternCalendarForm) {
  patternCalendarForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const name = (patternCalendarNameInput.value || '').trim();
    if (!name) {
      alert('캘린더 이름을 입력하세요.');
      return;
    }
    try {
      const wantPattern = getCalendarTemplateConfig(patternCalendarMode).patternEnabled;
      const res = await ensureCalendar(name, wantPattern);
      if (patternCalendarModal) patternCalendarModal.hidden = true;
      if (patternOnboardingModal) patternOnboardingModal.hidden = true;

      if (wantPattern) {
        const settings = await loadPatternSettings({ force: true });
        state.patternConfigured = settings.configured || false;
        patternManager.hide();
        calendarSection.hidden = true;
        settingsMenuButton.hidden = false;
        settingsModal.hidden = false;
        showSettingsView('calendar-pattern');
        if (!state.patternConfigured && res.defaultCalendarId) {
          state.calendarId = res.defaultCalendarId;
        }
      } else {
        state.usePattern = false;
        state.patternConfigured = true;
        hasPromptedPattern = true;
        patternManager.hide();
        calendarSection.hidden = false;
        settingsMenuButton.hidden = false;
        await Promise.all([
          loadCalendar(state.year, state.month, { force: true }).catch(() => null),
          loadShares({ force: true }).catch(() => null)
        ]);
      }
    } catch (e) {
      showToast('캘린더 준비에 실패했습니다: ' + (e.message || '오류'));
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

// 기념일 지우기 버튼
clearAnniversaryButton.addEventListener('click', async () => {
  if (!isCalendarEditable()) {
    showToast('읽기 전용 캘린더입니다.');
    return;
  }
  try {
    const detail = await fetchDay(state.selectedDate, { calendarId: state.calendarId });

    // yearlyMemos가 있고 현재 날짜에 anniversaryMemo가 없으면, 다른 년도에서 반복되는 것
    const isYearlyFromOtherYear = !detail.anniversaryMemo && detail.yearlyMemos && detail.yearlyMemos.length > 0;

    let shouldSave = false;

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
        shouldSave = true;
      }
    } else if (repeatYearly.checked) {
      // 매년 반복이 설정된 기념일 (원본)
      const selectedDate = new Date(state.selectedDate);
      const currentYear = selectedDate.getFullYear();

      const choice = confirm(`이 기념일을 삭제하시겠습니까?\n\n확인: ${currentYear}년부터 이후 모든 연도에서 삭제\n취소: ${currentYear}년에만 삭제`);

      if (choice !== null) {
        anniversaryMemo.value = '';
        repeatYearly.checked = false;
        shouldSave = true;
      }
    } else if (anniversaryMemo.value) {
      if (confirm('기념일을 삭제하시겠습니까?')) {
        anniversaryMemo.value = '';
        repeatYearly.checked = false;
        shouldSave = true;
      }
    } else {
      showToast('삭제할 기념일이 없습니다.');
    }

    // 자동 저장
    if (shouldSave) {
      try {
        const savedDetail = await api.saveDay(state.selectedDate, {
          customCode: detailCode.value || null,
          anniversaryMemo: anniversaryMemo.value || null,
          repeatYearly: repeatYearly.checked
        }, state.calendarId);

        // 캘린더 새로고침
        await refreshCalendar();

        // 기념일 지우기 버튼 숨기기
        clearAnniversaryButton.style.display = 'none';

        showToast('기념일이 삭제되었습니다.');
      } catch (error) {
        console.error('기념일 삭제 실패:', error);
        showToast('삭제에 실패했습니다.');
      }
    }
  } catch (e) {
    showToast(e.message || '기념일을 불러오지 못했습니다.');
  }
});

// 공통 저장 함수
async function saveDayDetail(showSuccessToast = true) {
  if (!state.selectedDate) {
    return;
  }
  if (!isCalendarEditable()) {
    showToast('읽기 전용 캘린더입니다.');
    return;
  }

  try {
    const savedDetail = await api.saveDay(state.selectedDate, {
      customCode: detailCode.value || null,
      anniversaryMemo: anniversaryMemo.value || null,
      repeatYearly: repeatYearly.checked
    }, state.calendarId);

    // 캘린더 새로고침
    await refreshCalendar();

    const detail = savedDetail || await fetchDay(state.selectedDate, { calendarId: state.calendarId, force: true });
    // 저장 결과를 캐시에 반영
    const dayKey = dayCacheKey(state.calendarId, state.selectedDate);
    cacheStores.day.set(dayKey, { data: detail, fetchedAt: Date.now() });

    // 모달 내용 업데이트
    setScheduleTypes(detail.scheduleTypes || state.scheduleTypes);
    renderDayDetailPanel(detail, state.selectedDate);
    syncDetailCodeOptions(detail.scheduleTypes || state.scheduleTypes, detail.effectiveCode || '');

    // 기념일 메모 지우기 버튼 표시 여부 업데이트
    clearAnniversaryButton.style.display = (detail.anniversaryMemo || (detail.yearlyMemos && detail.yearlyMemos.length > 0)) ? 'block' : 'none';

    if (showSuccessToast) {
      showToast('저장되었습니다.');
    }
  } catch (e) {
    showToast('저장 실패: ' + (e.message || '오류'));
  }
}

// 근무 패턴(customCode) 변경 시 자동 저장
detailCode.addEventListener('change', async () => {
  await saveDayDetail();
});

// 기념일 메모 변경 시 자동 저장 (포커스 해제 시)
let anniversaryMemoOriginalValue = '';
anniversaryMemo.addEventListener('focus', () => {
  anniversaryMemoOriginalValue = anniversaryMemo.value;
});
anniversaryMemo.addEventListener('blur', async () => {
  if (anniversaryMemo.value !== anniversaryMemoOriginalValue) {
    await saveDayDetail();
  }
});

// 매년 반복 체크박스 변경 시 자동 저장
repeatYearly.addEventListener('change', async () => {
  await saveDayDetail();
});

// form submit은 preventDefault만 (저장 버튼이 닫기로 변경되어 제출되지 않음)
dayDetailForm.addEventListener('submit', (event) => {
  event.preventDefault();
});

resetPatternButton.addEventListener('click', async () => {
  const settings = await api.getSettings(state.calendarId);
  settingsModal.hidden = true;
  calendarSection.hidden = true;
  settingsMenuButton.hidden = false;
  dayModal.hidden = true;
  patternManager.show(60, settings);
});

const navigateMonth = debounce((year, month) => {
  loadCalendar(year, month);
}, 150);

prevMonthBtn.addEventListener('click', () => {
  const date = new Date(state.year, state.month - 2, 1);
  navigateMonth(date.getFullYear(), date.getMonth() + 1);
});

nextMonthBtn.addEventListener('click', () => {
  const date = new Date(state.year, state.month, 1);
  navigateMonth(date.getFullYear(), date.getMonth() + 1);
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

// 메모 추가 폼
memoAddForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!isCalendarEditable()) {
    showToast('읽기 전용 캘린더입니다.');
    return;
  }
  const memoText = newMemoText.value.trim();
  if (!memoText) {
    showToast('메모를 입력해주세요.');
    return;
  }

  try {
    await api.saveMemo(state.selectedDate, { memo: memoText }, state.calendarId);
    newMemoText.value = '';
    invalidateDayCache(state.selectedDate);
    await selectDay(state.selectedDate);
    await loadCalendar(state.year, state.month, { force: true });
    showToast('메모가 저장되었습니다.');
  } catch (e) {
    showToast('메모 저장 실패: ' + (e.message || '오류'));
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
  const d = parseIsoDateLocal(date);
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

if (checkAuth()) {
  bootstrap();
}

if (newCalendarTemplateInput) {
  newCalendarTemplateInput.addEventListener('change', updateCreateCalendarTemplateUI);
  updateCreateCalendarTemplateUI();
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
  onStart: () => startGlobalLoading(),
  onEnd: () => finishGlobalLoading()
});

// 설정 네비게이션
const settingsBackButton = document.getElementById('settingsBackButton');
const settingsTitle = document.getElementById('settingsTitle');
const settingsViews = {
  main: document.getElementById('settingsMainView'),
  calendar: document.getElementById('settingsCalendarView'),
  'calendar-create': document.getElementById('settingsCalendarCreateView'),
  'calendar-edit': document.getElementById('settingsCalendarEditView'),
  'calendar-pattern': document.getElementById('settingsCalendarPatternView'),
  share: document.getElementById('settingsShareView'),
  personal: document.getElementById('settingsPersonalView')
};

const viewTitles = {
  main: '설정',
  calendar: '캘린더 관리',
  'calendar-create': '새 캘린더 만들기',
  'calendar-edit': '캘린더 수정',
  'calendar-pattern': '코드와 규칙 관리',
  share: '공유 관리',
  personal: '개인 설정'
};

const viewParents = {
  'calendar-create': 'calendar',
  'calendar-edit': 'calendar',
  'calendar-pattern': 'calendar'
};

let currentSettingsView = 'main';

function showSettingsView(viewName) {
  // 모든 뷰 숨기기
  Object.values(settingsViews).forEach(view => {
    if (view) view.hidden = true;
  });

  // 선택한 뷰 보이기
  if (settingsViews[viewName]) {
    settingsViews[viewName].hidden = false;
  }

  // 제목 변경
  if (settingsTitle) {
    settingsTitle.textContent = viewTitles[viewName] || '설정';
  }

  // 뒤로가기 버튼 표시/숨기기
  if (settingsBackButton) {
    settingsBackButton.hidden = viewName === 'main';
  }

  // 개인 설정 뷰로 이동 시 프로필 정보 로드
  if (viewName === 'personal') {
    loadProfileInfo();
  }
  if (viewName === 'calendar-pattern') {
    renderScheduleTypeEditor();
    if (state.calendarId) {
      Promise.all([
        api.getScheduleTypes(state.calendarId).catch(() => state.scheduleTypes),
        api.getWeeklyRules(state.calendarId).catch(() => state.weeklyRules)
      ]).then(([scheduleTypes, weeklyRules]) => {
        setScheduleTypes(scheduleTypes || []);
        setWeeklyRules(weeklyRules || []);
      }).catch(() => null);
    }
  }

  currentSettingsView = viewName;
}

async function loadProfileInfo() {
  try {
    const me = await api.me();
    const profileName = document.getElementById('profileName');
    const profileEmail = document.getElementById('profileEmail');
    const profileNickname = document.getElementById('profileNickname');
    const profileProviderHint = document.getElementById('profileProviderHint');

    if (profileName && me) {
      profileName.textContent = me.name || '-';
    }
    if (profileEmail && me) {
      profileEmail.textContent = me.email || '-';
    }
    if (profileNickname && me) {
      profileNickname.value = me.nickname || me.name || '';
    }
    if (profileProviderHint && me && me.provider === 'GOOGLE') {
      profileProviderHint.hidden = false;
    }
    // 현재 사용자 정보 저장
    state.me = me;
  } catch (e) {
    console.error('Failed to load profile info:', e);
  }
}

// 메뉴 항목 클릭 이벤트
const menuItems = document.querySelectorAll('.settings-menu-item');
menuItems.forEach(item => {
  item.addEventListener('click', () => {
    const viewName = item.dataset.view;
    if (viewName) {
      showSettingsView(viewName);
    }
  });
});

// 뒤로가기 버튼 클릭 이벤트
if (settingsBackButton) {
  settingsBackButton.addEventListener('click', () => {
    // 부모 뷰가 있으면 부모로, 없으면 메인으로
    const parentView = viewParents[currentSettingsView];
    showSettingsView(parentView || 'main');
  });
}

// 설정 모달이 열릴 때 메인 뷰로 초기화
const originalSettingsMenuButtonClick = settingsMenuButton ? settingsMenuButton.onclick : null;
if (settingsMenuButton) {
  settingsMenuButton.addEventListener('click', () => {
    showSettingsView('main');
  });
}

// 프로필 업데이트 폼
const updateProfileForm = document.getElementById('updateProfileForm');
if (updateProfileForm) {
  updateProfileForm.addEventListener('submit', async (event) => {
    event.preventDefault();
    const nickname = document.getElementById('profileNickname').value.trim();
    if (!nickname) {
      showToast('닉네임을 입력하세요');
      return;
    }
    try {
      const updatedUser = await api.updateProfile({ nickname });
      state.me = updatedUser;
      showToast('닉네임이 저장되었습니다');
    } catch (e) {
      showToast('저장 실패: ' + (e.message || '오류'));
    }
  });
}
