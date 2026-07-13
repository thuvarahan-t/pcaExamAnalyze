(function () {
    "use strict";

    var activeOverlay = null;
    var previousOverflow = "";

    function openDialog(options) {
        options = options || {};
        if (activeOverlay) activeOverlay.remove();

        return new Promise(function (resolve) {
            var type = options.type || "info";
            var hasCancel = options.cancel !== false;
            var overlay = document.createElement("div");
            overlay.className = "pca-dialog-overlay";
            overlay.innerHTML = '<section class="pca-dialog-card" role="dialog" aria-modal="true" aria-labelledby="pcaDialogTitle">' +
                '<div class="pca-dialog-content">' +
                '<div class="pca-dialog-icon ' + type + '"><i class="bi ' + (type === "danger" ? "bi-trash3-fill" : type === "error" ? "bi-exclamation-triangle-fill" : type === "success" ? "bi-check-lg" : "bi-question-lg") + '"></i></div>' +
                '<h2 class="pca-dialog-title" id="pcaDialogTitle"></h2>' +
                '<p class="pca-dialog-message"></p>' +
                (options.input ? '<input class="pca-dialog-input" type="text" maxlength="80" autocomplete="off">' : '') +
                '</div><div class="pca-dialog-actions">' +
                (hasCancel ? '<button class="pca-dialog-btn cancel" type="button"></button>' : '') +
                '<button class="pca-dialog-btn ' + (type === "danger" || type === "error" ? type : "primary") + '" type="button"></button>' +
                '</div></section>';

            var title = overlay.querySelector(".pca-dialog-title");
            var message = overlay.querySelector(".pca-dialog-message");
            var input = overlay.querySelector(".pca-dialog-input");
            var cancel = overlay.querySelector(".pca-dialog-btn.cancel");
            var accept = overlay.querySelector(".pca-dialog-btn:not(.cancel)");
            title.textContent = options.title || (type === "danger" ? "Please confirm" : "Confirmation");
            message.textContent = options.message || "";
            accept.textContent = options.acceptText || "Continue";
            if (cancel) cancel.textContent = options.cancelText || "Cancel";
            if (input) input.value = options.value || "";

            previousOverflow = document.body.style.overflow;
            document.body.style.overflow = "hidden";
            document.body.appendChild(overlay);
            activeOverlay = overlay;

            function close(value) {
                document.removeEventListener("keydown", onKey);
                overlay.remove();
                if (activeOverlay === overlay) activeOverlay = null;
                document.body.style.overflow = previousOverflow;
                resolve(value);
            }
            function onKey(event) {
                if (event.key === "Escape") close(options.input ? null : false);
                if (event.key === "Enter" && input && document.activeElement === input) accept.click();
            }
            overlay.addEventListener("click", function (event) {
                if (event.target === overlay) close(options.input ? null : false);
            });
            if (cancel) cancel.addEventListener("click", function () { close(options.input ? null : false); });
            accept.addEventListener("click", function () {
                if (input && !input.value.trim()) {
                    input.focus();
                    return;
                }
                close(input ? input.value.trim() : true);
            });
            document.addEventListener("keydown", onKey);
            window.setTimeout(function () { (input || accept).focus(); if (input) input.select(); }, 20);
        });
    }

    window.PcaDialog = {
        confirm: function (message, options) { return openDialog(Object.assign({ message: message }, options || {})); },
        prompt: function (message, value, options) { return openDialog(Object.assign({ message: message, value: value, input: true }, options || {})); },
        alert: function (message, options) { return openDialog(Object.assign({ message: message, cancel: false, acceptText: "OK" }, options || {})); }
    };

    function bindConfirmForms() {
        document.querySelectorAll("form[data-confirm]").forEach(function (form) {
            form.addEventListener("submit", function (event) {
                if (form.dataset.dialogApproved === "true") {
                    delete form.dataset.dialogApproved;
                    return;
                }
                event.preventDefault();
                var submitter = event.submitter;
                window.PcaDialog.confirm(form.dataset.confirm, {
                    title: form.dataset.confirmTitle || "Please confirm",
                    acceptText: form.dataset.confirmAction || "Continue",
                    type: form.dataset.confirmType || "info"
                }).then(function (approved) {
                    if (!approved) return;
                    form.dataset.dialogApproved = "true";
                    if (form.requestSubmit) form.requestSubmit(submitter || undefined);
                    else form.submit();
                });
            });
        });
    }

    function showFlash() {
        var flash = document.querySelector("[data-dialog-flash]");
        if (!flash) return;
        window.PcaDialog.alert(flash.dataset.dialogFlash || flash.textContent.trim(), {
            title: flash.dataset.dialogTitle || (flash.dataset.dialogType === "danger" ? "Unable to complete" : "Completed"),
            type: flash.dataset.dialogType || "success"
        });
    }

    function ready() { bindConfirmForms(); showFlash(); }
    if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", ready);
    else ready();
})();
