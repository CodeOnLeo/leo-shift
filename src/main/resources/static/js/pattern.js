export function initPatternForm({ sectionEl, formEl, patternInput, startInput, minutesInput, onSave }) {
  formEl.addEventListener('submit', async (event) => {
    event.preventDefault();
    const pattern = patternInput.value
      .split(',')
      .map((code) => code.trim().toUpperCase())
      .filter(Boolean);
    const payload = {
      pattern,
      patternStartDate: startInput.value,
      defaultNotificationMinutes: parseInt(minutesInput.value, 10)
    };
    await onSave(payload);
  });

  return {
    show(initialMinutes = 60) {
      sectionEl.hidden = false;
      minutesInput.value = initialMinutes;
      patternInput.value = '';
      startInput.value = '';
    },
    hide() {
      sectionEl.hidden = true;
    }
  };
}
