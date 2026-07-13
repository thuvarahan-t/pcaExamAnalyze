(function () {
    "use strict";

    var form = document.getElementById("exam-details-form");
    var email = document.getElementById("examEmail");
    var fullName = document.getElementById("examFullName");
    var nic = document.getElementById("examNic");
    if (!form || !email || !fullName || !nic) return;

    function titleCase(value) {
        var capitalizeNext = true;
        return Array.from(value).map(function (character) {
            if (/\p{L}/u.test(character)) {
                var formatted = capitalizeNext ? character.toLocaleUpperCase() : character.toLocaleLowerCase();
                capitalizeNext = false;
                return formatted;
            }
            if (/\s/u.test(character) || character === "-" || character === "'" || character === ".") {
                capitalizeNext = true;
            }
            return character;
        }).join("");
    }

    function formatName() {
        var start = fullName.selectionStart;
        var end = fullName.selectionEnd;
        fullName.value = titleCase(fullName.value);
        if (start !== null && end !== null) fullName.setSelectionRange(start, end);
        fullName.setCustomValidity(/^[\p{L} .'-]+$/u.test(fullName.value) || !fullName.value
            ? ""
            : "Full name can contain letters only.");
    }

    function cleanNic() {
        nic.value = nic.value.replace(/\D/g, "").slice(0, 12);
        nic.setCustomValidity(/^\d{12}$/.test(nic.value) || !nic.value
            ? ""
            : "NIC must contain exactly 12 digits.");
    }

    function validateEmail() {
        var valid = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(email.value.trim());
        email.setCustomValidity(valid || !email.value ? "" : "Enter a valid email address.");
    }

    nic.addEventListener("keydown", function (event) {
        if (!event.ctrlKey && !event.metaKey && !event.altKey && event.key.length === 1 && !/^\d$/.test(event.key)) {
            event.preventDefault();
        }
    });
    nic.addEventListener("input", cleanNic);
    fullName.addEventListener("input", formatName);
    email.addEventListener("input", validateEmail);

    form.addEventListener("submit", function (event) {
        email.value = email.value.trim().toLocaleLowerCase();
        fullName.value = fullName.value.trim().replace(/\s+/g, " ");
        formatName();
        cleanNic();
        validateEmail();
        if (!form.checkValidity()) {
            event.preventDefault();
            form.reportValidity();
        }
    });

    formatName();
    cleanNic();
    validateEmail();
})();
