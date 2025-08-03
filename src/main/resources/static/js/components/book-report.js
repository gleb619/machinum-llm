const app = {
    completionChart: null,
    missingDataChart: null,
};

/**
 * Creates an Alpine.js data object with book report functionality
 */
export function bookReportApp() {
    return {
        summary: null,
        heatmapData: null,
        selectedChapter: null,
        chaptersPerRow: 20,
        tooltipVisible: false,
        tooltipX: 0,
        tooltipY: 0,
        tooltipContent: '',

        initBookReport() {
            this.bookReportLoadData();
            this.bookReportLoadHeatmapData();
        },

        async bookReportLoadData() {
            if(!this.activeId) return;

            try {
                // In real application, this would be API calls
                 const response = await fetch(`/api/books/${this.activeId}/chapters-summary`);
                 this.summary = await response.json();

                this.updateCharts();
            } catch (error) {
                console.error('Error loading data:', error);
                this.showToast(`Error loading data: ${error.message || error.detail || error}`, true);
            }
        },

        getCompleteCount(field, fieldName = `empty${field.charAt(0).toUpperCase() + field.slice(1)}s`) {
            if (!this.summary) return 0;
            return this.summary.totalChapters - (this.summary[ fieldName] || 0);
        },

        getPercentage(field, fieldName = `${field}CompletionPercentage`) {
            if (!this.summary) return 0;
            const percentage = this.summary[fieldName];
            return percentage ? Math.round(percentage) : 0;
        },

        getColorClassForPercentage(percentage) {
            if (percentage >= 99) return 'text-green-600';
            if (percentage >= 95) return 'text-green-400';
            if (percentage >= 85) return 'text-blue-400';
            if (percentage >= 75) return 'text-blue-600';
            if (percentage >= 50) return 'text-yellow-400';
            if (percentage >= 30) return 'text-yellow-600';
            if (percentage >= 10) return 'text-red-400';
            return 'text-red-600';
        },

        getChapterStatus(chapter) {
            const missing = [];
            if (!chapter.title) missing.push('Title');
            if (!chapter.translatedTitle) missing.push('Translation');
            if (!chapter.summary) missing.push('Summary');

            if (missing.length === 0) return 'Complete';
            return `Missing: ${missing.join(', ')}`;
        },

        getChapterStatusClass(chapter) {
            const status = this.getChapterStatus(chapter);
            return status === 'Complete' ? 'text-green-600 font-medium' : 'text-red-600';
        },

        updateCharts() {
            this.$nextTick(() => {
                this.createCompletionChart();
                this.createMissingDataChart();
            });
        },

        createCompletionChart() {
            const ctx = document.getElementById('completionChart');
            if(!ctx) return;
            if (app.completionChart) {
                app.completionChart.destroy();
            }

            app.completionChart = new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: ['Titles', 'Translated Titles', 'Summaries', 'Texts', 'Translated Texts', 'Names', 'Warnings'],
                    datasets: [{
                        label: 'Completion %',
                        data: [
                            this.getPercentage('title'),
                            this.getPercentage('translatedTitle'),
                            this.getPercentage('summary'),
                            this.getPercentage('text'),
                            this.getPercentage('translatedText'),
                            this.getPercentage('translatedNames'),
                            100 - this.getPercentage('warnings', 'warningsPercentage'),
                        ],
                        backgroundColor: ['#3B82F6', '#10B981', '#F59E0B', '#FCD34D', '#6366F1', '#EC4899', '#EC061D', '#EF4444'],
                        borderColor: ['#2563EB', '#059669', '#D97706', '#DC2626', '#4F46E5', '#DB2777', '#0284C7', '#F5A623'],
                        borderWidth: 1
                    }]
                },
                options: {
                    responsive: true,
                    scales: {
                        y: {
                            beginAtZero: true,
                            max: 100
                        }
                    }
                }
            });
        },

        createMissingDataChart() {
            const ctx = document.getElementById('missingDataChart');
            if(!ctx) return;

            if (app.missingDataChart) {
                app.missingDataChart.destroy();
            }

            app.missingDataChart = new Chart(ctx, {
                type: 'doughnut',
                data: {
                    labels: ['Titles', 'Translated Titles', 'Summaries', 'Texts', 'Translated Texts', 'Names', 'Warnings'],
                    datasets: [{
                        data: [
                            this.summary.emptyTitles,
                            this.summary.emptyTranslatedTitles,
                            this.summary.emptySummaries,
                            this.summary.emptyTexts,
                            this.summary.emptyTranslatedTexts,
                            this.summary.emptyTranslatedNames,
                            this.summary.totalChapters - this.summary.emptyWarnings
                        ],
                        backgroundColor: ['#3B82F6', '#10B981', '#F59E0B', '#FCD34D', '#6366F1', '#EC4899', '#EC061D', '#EF4444'],
                        borderWidth: 2
                    }]
                },
                options: {
                    responsive: true,
                    plugins: {
                        legend: {
                            position: 'bottom'
                        }
                    }
                }
            });
        },

        async bookReportLoadHeatmapData(force = false) {
            if(!this.activeId) return;

            try {
                const response = await fetch(`/api/books/${this.activeId}/chapters-heatmap?forceUpdate=${force}`);
                this.heatmapData = await response.json();
            } catch (error) {
                console.error('Error loading heatmap data:', error);
                this.showToast(`Error loading heatmap data: ${error.message || error.detail || error}`, true);
            }
        },

        getHeatmapCellClass(percentage) {
            if (percentage >= 99) return 'bg-green-600 hover:bg-green-700';
            if (percentage >= 95) return 'bg-green-400 hover:bg-green-500';
            if (percentage >= 90) return 'bg-green-200 hover:bg-green-300';
            if (percentage >= 85) return 'bg-blue-300 hover:bg-blue-400';
            if (percentage >= 80) return 'bg-blue-400 hover:bg-blue-500';
            if (percentage >= 75) return 'bg-blue-500 hover:bg-blue-600';
            if (percentage >= 70) return 'bg-blue-600 hover:bg-blue-700';
            if (percentage >= 65) return 'bg-indigo-200 hover:bg-indigo-300';
            if (percentage >= 60) return 'bg-indigo-300 hover:bg-indigo-400';
            if (percentage >= 55) return 'bg-indigo-400 hover:bg-indigo-500';
            if (percentage >= 50) return 'bg-yellow-200 hover:bg-yellow-300';
            if (percentage >= 45) return 'bg-yellow-300 hover:bg-yellow-400';
            if (percentage >= 40) return 'bg-yellow-400 hover:bg-yellow-500';
            if (percentage >= 35) return 'bg-yellow-600 hover:bg-yellow-700';
            if (percentage >= 12.5) return 'bg-red-400 hover:bg-red-500';
            return 'bg-red-600 hover:bg-red-700';
        },

        getReadinessBarClass(percentage) {
            if (percentage >= 99) return 'bg-green-600';
            if (percentage >= 95) return 'bg-green-400';
            if (percentage >= 90) return 'bg-green-200';
            if (percentage >= 85) return 'bg-blue-300';
            if (percentage >= 80) return 'bg-blue-400';
            if (percentage >= 75) return 'bg-blue-500';
            if (percentage >= 70) return 'bg-blue-600';
            if (percentage >= 65) return 'bg-indigo-200';
            if (percentage >= 60) return 'bg-indigo-300';
            if (percentage >= 55) return 'bg-indigo-400';
            if (percentage >= 50) return 'bg-yellow-200';
            if (percentage >= 45) return 'bg-yellow-400';
            if (percentage >= 40) return 'bg-yellow-500';
            if (percentage >= 35) return 'bg-yellow-600';
            if (percentage >= 12.5) return 'bg-red-400';
            return 'bg-red-600';
        },

        getStatusTextClass(status) {
            const classes = {
                'excellent': 'text-green-600',
                'good': 'text-blue-600',
                'fair': 'text-yellow-600',
                'poor': 'text-yellow-400',
                'critical': 'text-red-600'
            };
            return classes[status] || 'text-gray-600';
        },

        getStatusCount(status) {
            return this.heatmapData?.statusCounts[status] || 0;
        },

        showTooltip(event, chapter) {
            this.tooltipX = event.pageX + 10;
            this.tooltipY = event.pageY - 10;

            const completedFields = Object.values(this.fieldStatus(chapter)).filter(Boolean).length;
            const totalFields = Object.keys(this.fieldStatus(chapter)).length;

            this.tooltipContent = `
                <strong>Chapter ${chapter.chapterNumber}</strong><br>
                Readiness: ${Math.round(chapter.readinessIndex)}%<br>
                Status: ${chapter.status}<br>
                Fields: ${completedFields}/${totalFields} complete
            `;

            this.tooltipVisible = true;
        },

        hideTooltip() {
            this.tooltipVisible = false;
        },

        selectChapter(chapter) {
            this.selectedChapter = chapter;
        },

        fieldStatus(chapterReadinessItem) {
            if(!chapterReadinessItem) return {};

            return {
                title: chapterReadinessItem.title,
                translatedTitle: chapterReadinessItem.translatedTitle,
                text: chapterReadinessItem.text,
                translatedText: chapterReadinessItem.translatedText,
                summary: chapterReadinessItem.summary,
                names: chapterReadinessItem.names,
                translatedNames: chapterReadinessItem.translatedNames,
                warnings: chapterReadinessItem.warnings
            };
        },

        setFromChap(selectedItem) {
            this.setRuleType('range');
            this.chapterSource.rangeRuleConfig.min = selectedItem.chapterNumber - 1;

            const selectedIndex = this.heatmapData.chapters.findIndex(item => item.id === selectedItem.id);
            const selectedReadinessIndex = selectedItem.readinessIndex;

            for (let i = selectedIndex + 1; i < this.heatmapData.chapters.length; i++) {
                if (this.heatmapData.chapters[i].readinessIndex !== selectedReadinessIndex) {
                    this.chapterSource.rangeRuleConfig.max = this.heatmapData.chapters[i - 1].chapterNumber;
                    return;
                }
            }

            this.chapterSource.rangeRuleConfig.max = this.heatmapData.chapters[this.heatmapData.chapters.length - 1].chapterNumber;
        },

        setToChap(selectedItem) {
            this.setRuleType('range');
            this.chapterSource.rangeRuleConfig.max = selectedItem.chapterNumber;

            const selectedIndex = this.heatmapData.chapters.findIndex(item => item.id === selectedItem.id);
            const selectedReadinessIndex = selectedItem.readinessIndex;

            for (let i = selectedIndex - 1; i >= 0; i--) {
                if (this.heatmapData.chapters[i].readinessIndex !== selectedReadinessIndex) {
                    this.chapterSource.rangeRuleConfig.min = this.heatmapData.chapters[i + 1].chapterNumber - 1;
                    return;
                }
            }

            this.chapterSource.rangeRuleConfig.min = this.heatmapData.chapters[0].chapterNumber;
        }

    };
}