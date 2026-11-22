document.querySelectorAll('[data-package-phone]').forEach(input => {
    const digitsOnly = value => value.replace(/[^0-9]/g, '');
    const grouped = input.hasAttribute('data-phone-grouped');

    function format(event) {
        const digitsBeforeCaret = digitsOnly(input.value.slice(0, input.selectionStart)).length;
        const digits = digitsOnly(input.value).slice(0, 8);
        input.value = grouped
            ? [digits.slice(0, 1), digits.slice(1, 4), digits.slice(4)].filter(Boolean).join(' ')
            : digits;

        let caret = 0;
        for (let remaining = Math.min(digitsBeforeCaret, digits.length); remaining > 0; caret++) {
            if (input.value[caret] !== ' ') remaining--;
        }
        if (event?.inputType === 'deleteContentForward' && input.value[caret] === ' ') caret++;
        input.setSelectionRange(caret, caret);
    }

    if (grouped) format();
    input.addEventListener('input', format);
    input.addEventListener('paste', event => {
        const number = event.clipboardData.getData('text').replace(/[\s-]/g, '');
        if (!/^07[0-9]{8}$/.test(number)) return;
        event.preventDefault();
        input.value = number.slice(2);
        input.setSelectionRange(input.value.length, input.value.length);
        input.dispatchEvent(new Event('input', { bubbles: true }));
    });
});
