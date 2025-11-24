const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

export function renderCalendar({
  gridEl,
  summaryEl,
  data,
  today,
  onSelectDay
}) {
  gridEl.innerHTML = '';
  summaryEl.innerHTML = '';
  if (!data.patternConfigured) {
    gridEl.innerHTML = '<p>Please configure your pattern first.</p>';
    return;
  }

  WEEKDAYS.forEach((day) => {
    const label = document.createElement('div');
    label.textContent = day;
    label.className = 'day-cell weekday';
    gridEl.appendChild(label);
  });

  const firstDate = new Date(data.year, data.month - 1, 1);
  const offset = firstDate.getDay();
  for (let i = 0; i < offset; i++) {
    const filler = document.createElement('div');
    filler.className = 'day-cell empty';
    gridEl.appendChild(filler);
  }

  data.days.forEach((day) => {
    const cell = document.createElement('div');
    cell.className = 'day-cell';
    cell.dataset.date = day.date;
    if (day.date === today) {
      cell.classList.add('today');
    }
    const dateLabel = document.createElement('div');
    dateLabel.className = 'date-label';
    dateLabel.textContent = new Date(day.date).getDate();
    const codeLabel = document.createElement('div');
    codeLabel.className = 'shift-code';
    codeLabel.textContent = day.effectiveCode || '-';
    const memo = document.createElement('div');
    memo.className = 'memos';
    memo.textContent = [...(day.memos || []), ...(day.yearlyMemos || [])].join(' \u2022 ');
    cell.append(dateLabel, codeLabel);
    if (memo.textContent) {
      cell.append(memo);
    }
    cell.addEventListener('click', () => onSelectDay(day.date));
    gridEl.appendChild(cell);
  });

  if (data.summary) {
    Object.entries(data.summary).forEach(([code, count]) => {
      const item = document.createElement('div');
      item.className = 'summary-item';
      item.textContent = `${code}: ${count}`;
      summaryEl.appendChild(item);
    });
  }
}
