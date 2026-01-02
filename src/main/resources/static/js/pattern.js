export function initPatternForm({ sectionEl, formEl, patternInput, startInput, applyRetroactiveInput, onSave }) {
  // Pattern builder elements
  const dDaysInput = document.getElementById('dDays');
  const dOffInput = document.getElementById('dOff');
  const aDaysInput = document.getElementById('aDays');
  const aOffInput = document.getElementById('aOff');
  const nDaysInput = document.getElementById('nDays');
  const nOffInput = document.getElementById('nOff');
  const previewText = document.getElementById('patternPreviewText');
  const useManualCheckbox = document.getElementById('useManualPattern');

  // Generate pattern from inputs
  function generatePattern() {
    const pattern = [];

    // D days + off
    const dDays = parseInt(dDaysInput.value) || 0;
    const dOff = parseInt(dOffInput.value) || 0;
    for (let i = 0; i < dDays; i++) pattern.push('D');
    for (let i = 0; i < dOff; i++) pattern.push('O');

    // A days + off
    const aDays = parseInt(aDaysInput.value) || 0;
    const aOff = parseInt(aOffInput.value) || 0;
    for (let i = 0; i < aDays; i++) pattern.push('A');
    for (let i = 0; i < aOff; i++) pattern.push('O');

    // N days + off
    const nDays = parseInt(nDaysInput.value) || 0;
    const nOff = parseInt(nOffInput.value) || 0;
    for (let i = 0; i < nDays; i++) pattern.push('N');
    for (let i = 0; i < nOff; i++) pattern.push('O');

    return pattern;
  }

  // Update preview
  function updatePreview() {
    if (useManualCheckbox.checked) {
      previewText.textContent = '직접 입력 모드';
      return;
    }

    const pattern = generatePattern();
    const total = pattern.length;
    const workDays = pattern.filter(c => c !== 'O').length;
    const offDays = pattern.filter(c => c === 'O').length;

    previewText.innerHTML = `
      <div style="margin-bottom: 0.5rem;">${pattern.join(', ')}</div>
      <div style="font-size: 0.85rem; color: #666;">
        총 ${total}일 사이클 (근무 ${workDays}일, 휴무 ${offDays}일)
      </div>
    `;
  }

  // Update preview when inputs change
  [dDaysInput, dOffInput, aDaysInput, aOffInput, nDaysInput, nOffInput].forEach(input => {
    input.addEventListener('input', updatePreview);
  });

  // Toggle manual input
  useManualCheckbox.addEventListener('change', (e) => {
    if (e.target.checked) {
      patternInput.hidden = false;
      patternInput.required = true;
      document.querySelector('.pattern-builder').style.display = 'none';
    } else {
      patternInput.hidden = true;
      patternInput.required = false;
      document.querySelector('.pattern-builder').style.display = 'block';
    }
    updatePreview();
  });

  // Form submit
  formEl.addEventListener('submit', async (event) => {
    event.preventDefault();

    let pattern;
    if (useManualCheckbox.checked) {
      // Manual input
      pattern = patternInput.value
        .split(',')
        .map((code) => code.trim().toUpperCase())
        .filter(Boolean);
    } else {
      // Auto-generated pattern
      pattern = generatePattern();
    }

    const payload = {
      pattern,
      patternStartDate: startInput.value,
      applyRetroactive: applyRetroactiveInput ? applyRetroactiveInput.checked : false
    };
    await onSave(payload);
  });

  // Initial preview
  updatePreview();

  return {
    show(initialMinutes = 60, existingSettings = null) {
      sectionEl.hidden = false;
      if (applyRetroactiveInput) {
        applyRetroactiveInput.checked = false;
      }

      if (existingSettings && existingSettings.configured) {
        // Load existing pattern - 직접입력 모드는 해제하고 패턴만 참고용으로 보여줌
        const pattern = existingSettings.pattern || [];
        patternInput.value = pattern.join(', ');
        startInput.value = existingSettings.patternStartDate || '';
      }

      // 항상 자동 생성 모드로 시작
      useManualCheckbox.checked = false;
      patternInput.hidden = true;
      patternInput.required = false;
      document.querySelector('.pattern-builder').style.display = 'block';

      updatePreview();
    },
    hide() {
      sectionEl.hidden = true;
    }
  };
}
