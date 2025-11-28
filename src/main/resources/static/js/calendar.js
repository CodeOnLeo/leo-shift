const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];

function initials(name) {
  if (!name) return '';
  const parts = name.trim().split(' ').filter(Boolean);
  if (parts.length === 1) {
    return parts[0].slice(0, 2).toUpperCase();
  }
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

function formatDate(dateTimeString) {
  try {
    const d = new Date(dateTimeString);
    return `${d.getMonth() + 1}/${d.getDate()}`;
  } catch (e) {
    return '';
  }
}

export function renderCalendar({
  gridEl,
  summaryEl,
  data,
  today,
  selectedDate,
  onSelectDay
}) {
  gridEl.innerHTML = '';
  // Clear only summary items, preserve the legend button
  const summaryItems = summaryEl.querySelectorAll('.summary-item');
  summaryItems.forEach(item => item.remove());
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

  // 백엔드에서 35일(5주)을 보내주므로 빈 셀 없이 바로 렌더링
  data.days.forEach((day) => {
    const dayDate = new Date(day.date);
    const isCurrentMonth = dayDate.getMonth() === data.month - 1;
    const cell = document.createElement('div');
    cell.className = 'day-cell';
    cell.dataset.date = day.date;

    // 다른 월의 날짜는 other-month 클래스 추가
    if (!isCurrentMonth) {
      cell.classList.add('other-month');
    }

    if (day.effectiveCode) {
      cell.dataset.code = day.effectiveCode;
    }
    if (day.date === today) {
      cell.classList.add('today');
    }
    if (day.date === selectedDate) {
      cell.classList.add('selected');
    }
    const dateLabel = document.createElement('div');
    dateLabel.className = 'date-label';
    dateLabel.textContent = new Date(day.date).getDate();
    const codeLabel = document.createElement('div');
    codeLabel.className = 'shift-code';
    codeLabel.textContent = day.effectiveCode || '-';

    cell.append(dateLabel, codeLabel);

    // 기념일 메모를 먼저 표시 (당일 + 반복)
    const allAnniversaries = [...(day.anniversaryMemos || []), ...(day.yearlyMemos || [])];
    if (allAnniversaries.length > 0) {
      const anniversary = document.createElement('div');
      anniversary.className = 'memo-anniversary';
      anniversary.textContent = '🎉 ' + allAnniversaries.join(' \u2022 ');
      cell.append(anniversary);
    }

    // 일반 메모는 그 다음에 표시
    if (day.memos && day.memos.length > 0) {
      const memo = document.createElement('div');
      memo.className = 'memo-regular';
      memo.textContent = day.memos.join(' \u2022 ');
      cell.append(memo);
    }

    // 작성자 태그 (다중 사용자 메모)
    if (day.dayMemos && day.dayMemos.length > 0) {
      const tagContainer = document.createElement('div');
      tagContainer.className = 'memo-author-tags';

      if (day.dayMemos.length === 1) {
        // 1개일 때는 태그만 표시
        const memo = day.dayMemos[0];
        const tag = document.createElement('div');
        tag.className = 'memo-author-tag';
        const authorName = memo.author.nickname || memo.author.name || '';
        tag.textContent = initials(authorName);
        tag.title = `${authorName || '작성자'}: ${memo.memo}${memo.updatedAt ? ` · ${formatDate(memo.updatedAt)}` : ''}`;
        if (memo.author.color) {
          tag.style.backgroundColor = memo.author.color;
          tag.style.color = '#fff';
          tag.style.border = '1px solid rgba(0,0,0,0.08)';
        }
        tagContainer.append(tag);
      } else {
        // 2개 이상일 때는 첫 번째 태그 + 수량 표시
        const firstMemo = day.dayMemos[0];
        const firstTag = document.createElement('div');
        firstTag.className = 'memo-author-tag';
        const firstName = firstMemo.author.nickname || firstMemo.author.name || '';
        firstTag.textContent = initials(firstName);
        firstTag.title = `${firstName || '작성자'}: ${firstMemo.memo}${firstMemo.updatedAt ? ` · ${formatDate(firstMemo.updatedAt)}` : ''}`;
        if (firstMemo.author.color) {
          firstTag.style.backgroundColor = firstMemo.author.color;
          firstTag.style.color = '#fff';
          firstTag.style.border = '1px solid rgba(0,0,0,0.08)';
        }
        tagContainer.append(firstTag);

        // 나머지 개수 표시
        const countTag = document.createElement('div');
        countTag.className = 'memo-author-tag';
        countTag.textContent = `+${day.dayMemos.length - 1}`;
        // 모든 메모를 툴팁에 표시
        const allMemos = day.dayMemos.map(memo => {
          const name = memo.author.nickname || memo.author.name || '작성자';
          return `${name}: ${memo.memo}${memo.updatedAt ? ` · ${formatDate(memo.updatedAt)}` : ''}`;
        }).join('\n');
        countTag.title = allMemos;
        tagContainer.append(countTag);
      }

      cell.append(tagContainer);
    } else if (day.memoAuthor) {
      // 기존 호환성 유지 (deprecated)
      const tag = document.createElement('div');
      tag.className = 'memo-author-tag';
      const authorName = day.memoAuthor.nickname || day.memoAuthor.name || '';
      tag.textContent = initials(authorName);
      tag.title = `${authorName || '작성자'}${day.updatedAt ? ` · ${formatDate(day.updatedAt)}` : ''}`;
      if (day.memoAuthor.color) {
        tag.style.backgroundColor = day.memoAuthor.color;
        tag.style.color = '#fff';
        tag.style.border = '1px solid rgba(0,0,0,0.08)';
      }
      cell.append(tag);
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
