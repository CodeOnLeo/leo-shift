export function initPatternForm({
  sectionEl,
  formEl,
  patternInput,
  startInput,
  applyRetroactiveInput,
  onSave,
  getScheduleTypes
}) {
  const previewText = document.getElementById('patternPreviewText');
  const useManualCheckbox = document.getElementById('useManualPattern');
  const paletteEl = document.getElementById('patternCodePalette');
  const sequenceEl = document.getElementById('patternSequence');
  const undoButton = document.getElementById('patternUndoButton');
  const clearButton = document.getElementById('patternClearButton');
  let sequence = [];

  function availableTypes() {
    return typeof getScheduleTypes === 'function' ? (getScheduleTypes() || []) : [];
  }

  function appendCode(code) {
    if (!code) return;
    sequence.push(code);
    renderSequence();
    updatePreview();
  }

  function renderPalette() {
    if (!paletteEl) return;
    paletteEl.innerHTML = '';
    availableTypes().forEach((type) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'pattern-code-chip';
      button.textContent = `${type.name || type.code} (${type.code})`;
      button.addEventListener('click', () => appendCode(type.code));
      paletteEl.appendChild(button);
    });
  }

  function renderSequence() {
    if (!sequenceEl) return;
    sequenceEl.innerHTML = '';
    sequence.forEach((code) => {
      const chip = document.createElement('div');
      chip.className = 'pattern-code-chip sequence';
      chip.textContent = code;
      sequenceEl.appendChild(chip);
    });
  }

  function updatePreview() {
    if (useManualCheckbox.checked) {
      previewText.textContent = '직접 입력 모드';
      return;
    }

    const total = sequence.length;
    if (total === 0) {
      previewText.textContent = '아직 코드가 없습니다. 위의 코드 버튼을 눌러 순서를 만들어보세요.';
      return;
    }

    previewText.innerHTML = `
      <div style="margin-bottom: 0.5rem;">${sequence.join(', ')}</div>
      <div style="font-size: 0.85rem; color: #666;">
        총 ${total}개 순서
      </div>
    `;
  }

  function syncManualMode(enabled) {
    if (enabled) {
      patternInput.hidden = false;
      patternInput.required = true;
      const builder = sectionEl.querySelector('.pattern-builder');
      if (builder) builder.style.display = 'none';
    } else {
      patternInput.hidden = true;
      patternInput.required = false;
      const builder = sectionEl.querySelector('.pattern-builder');
      if (builder) builder.style.display = 'block';
    }
    updatePreview();
  }

  if (undoButton) {
    undoButton.addEventListener('click', () => {
      sequence.pop();
      renderSequence();
      updatePreview();
    });
  }

  if (clearButton) {
    clearButton.addEventListener('click', () => {
      sequence = [];
      renderSequence();
      updatePreview();
    });
  }

  useManualCheckbox.addEventListener('change', (e) => {
    syncManualMode(e.target.checked);
  });

  formEl.addEventListener('submit', async (event) => {
    event.preventDefault();

    let pattern;
    if (useManualCheckbox.checked) {
      pattern = patternInput.value
        .split(',')
        .map((code) => code.trim().toUpperCase())
        .filter(Boolean);
    } else {
      pattern = sequence.slice();
    }

    const payload = {
      pattern,
      patternStartDate: startInput.value,
      applyRetroactive: applyRetroactiveInput ? applyRetroactiveInput.checked : false
    };
    await onSave(payload);
  });

  renderPalette();
  renderSequence();
  updatePreview();

  return {
    show(initialMinutes = 60, existingSettings = null) {
      sectionEl.hidden = false;
      if (applyRetroactiveInput) {
        applyRetroactiveInput.checked = false;
      }

      renderPalette();

      if (existingSettings && existingSettings.configured) {
        const pattern = existingSettings.pattern || [];
        sequence = pattern.slice();
        patternInput.value = pattern.join(', ');
        startInput.value = existingSettings.patternStartDate || '';
      } else {
        sequence = [];
        patternInput.value = '';
      }

      useManualCheckbox.checked = false;
      syncManualMode(false);
      renderSequence();
      updatePreview();
    },
    hide() {
      sectionEl.hidden = true;
    }
  };
}
