export function renderToday({ sectionEl, cardEl, upcomingEl, data }) {
  if (!data.patternConfigured || !data.today) {
    sectionEl.hidden = true;
    return;
  }
  sectionEl.hidden = false;
  cardEl.innerHTML = `
    <strong>${formatDate(data.today.date)}</strong>
    <span>${data.today.code} · ${data.today.shiftLabel}</span>
    <span>${data.today.timeRange}</span>
    <small>${(data.today.memos || []).join(' \u2022 ')}</small>
  `;
  upcomingEl.innerHTML = '';
  data.upcoming.forEach((day) => {
    const div = document.createElement('div');
    div.className = 'upcoming-card';
    div.innerHTML = `
      <strong>${formatDate(day.date)}</strong><br />
      ${day.code || '-'} · ${day.shiftLabel}<br />
      <small>${day.timeRange}</small><br />
      <small>${(day.memos || []).join(' \u2022 ')}</small>
    `;
    upcomingEl.appendChild(div);
  });
}

function formatDate(date) {
  return new Date(date).toLocaleDateString(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
}
