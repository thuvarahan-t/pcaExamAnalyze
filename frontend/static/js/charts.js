/* PCA Exam Analyzer — Chart.js helpers themed for the glass UI. */
(function () {
    if (typeof Chart === "undefined") return;

    Chart.defaults.color = "#4d567a";
    Chart.defaults.font.family = "'Inter', system-ui, sans-serif";
    Chart.defaults.font.size = 12;
    Chart.defaults.borderColor = "rgba(26,19,100,0.10)";
    Chart.defaults.plugins.legend.labels.usePointStyle = true;
    Chart.defaults.plugins.legend.labels.padding = 14;
    Chart.defaults.plugins.tooltip.backgroundColor = "rgba(22,26,58,0.95)";
    Chart.defaults.plugins.tooltip.borderColor = "rgba(26,19,172,0.25)";
    Chart.defaults.plugins.tooltip.borderWidth = 1;
    Chart.defaults.plugins.tooltip.padding = 10;
    Chart.defaults.plugins.tooltip.cornerRadius = 10;

    // Brand-derived palette (logo blues/cyan + complements)
    var PALETTE = ["#1a13ac", "#0eb3fe", "#07dffe", "#2767e8", "#6c5ce7", "#00cec9", "#5b8def", "#9b8cff"];

    function pctScale() {
        return {
            beginAtZero: true, max: 100,
            ticks: { callback: function (v) { return v + "%"; } },
            grid: { color: "rgba(26,19,100,0.07)" }
        };
    }

    window.PCAChart = {
        palette: PALETTE,

        doughnut: function (id, labels, data, colors) {
            var el = document.getElementById(id);
            if (!el) return;
            return new Chart(el, {
                type: "doughnut",
                data: {
                    labels: labels,
                    datasets: [{
                        data: data,
                        backgroundColor: colors || PALETTE,
                        borderColor: "#ffffff",
                        borderWidth: 2,
                        hoverOffset: 8
                    }]
                },
                options: {
                    responsive: true, maintainAspectRatio: false, cutout: "62%",
                    plugins: { legend: { position: "right" } }
                }
            });
        },

        topicBar: function (id, labels, data, colors) {
            var el = document.getElementById(id);
            if (!el) return;
            return new Chart(el, {
                type: "bar",
                data: {
                    labels: labels,
                    datasets: [{
                        label: "Topic %",
                        data: data,
                        backgroundColor: colors || PALETTE,
                        borderRadius: 8,
                        maxBarThickness: 46
                    }]
                },
                options: {
                    responsive: true, maintainAspectRatio: false,
                    plugins: { legend: { display: false } },
                    scales: { y: pctScale(), x: { grid: { display: false } } }
                }
            });
        },

        line: function (id, labels, data) {
            var el = document.getElementById(id);
            if (!el) return;
            var ctx = el.getContext("2d");
            var grad = ctx.createLinearGradient(0, 0, 0, 260);
            grad.addColorStop(0, "rgba(26,19,172,0.28)");
            grad.addColorStop(1, "rgba(14,179,254,0)");
            return new Chart(el, {
                type: "line",
                data: {
                    labels: labels,
                    datasets: [{
                        label: "Overall %",
                        data: data,
                        borderColor: "#1a13ac",
                        backgroundColor: grad,
                        fill: true, tension: 0.35,
                        pointBackgroundColor: "#0eb3fe",
                        pointRadius: 5, pointHoverRadius: 7, borderWidth: 3
                    }]
                },
                options: {
                    responsive: true, maintainAspectRatio: false,
                    plugins: { legend: { display: false } },
                    scales: { y: pctScale(), x: { grid: { display: false } } }
                }
            });
        },

        groupedBar: function (id, labels, datasets) {
            var el = document.getElementById(id);
            if (!el) return;
            var ds = datasets.map(function (d, i) {
                var c = PALETTE[i % PALETTE.length];
                return { label: d.label, data: d.data, backgroundColor: c, borderRadius: 6, maxBarThickness: 30 };
            });
            return new Chart(el, {
                type: "bar",
                data: { labels: labels, datasets: ds },
                options: {
                    responsive: true, maintainAspectRatio: false,
                    plugins: { legend: { position: "top" } },
                    scales: { y: pctScale(), x: { grid: { display: false } } }
                }
            });
        }
    };
})();
