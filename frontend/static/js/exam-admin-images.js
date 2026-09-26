(function () {
    "use strict";

    // Exam form: the Drive PDF link only applies to the classic answer sheet; image-sheet
    // exams point to the Question Editor instead.
    var callout = document.getElementById("question-images");
    // The answer-key grid only applies to the classic sheet; image exams set answers per question.
    var answerKey = document.getElementById("answer-key");
    var paperGroup = document.getElementById("paperDriveGroup");
    var paperInput = document.getElementById("paperDriveUrl");
    var radios = document.querySelectorAll('input[name="sheetType"]');
    if (!radios.length) return;

    function refresh() {
        var checked = document.querySelector('input[name="sheetType"]:checked');
        var images = !!checked && checked.value === "QUESTION_IMAGES";
        if (callout) callout.hidden = !images;
        if (answerKey) answerKey.hidden = images;
        if (paperGroup) paperGroup.hidden = images;
        if (paperInput) paperInput.required = !images;
    }

    radios.forEach(function (radio) { radio.addEventListener("change", refresh); });
    refresh();
})();
