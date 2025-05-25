/**
 * Creates an Alpine.js data object with book report functionality
 */
export function bookReportApp() {
    return {
        summary: null,
        completionChart: null,
        missingDataChart: null,
        heatmapData: null,
        selectedChapter: null,
        chaptersPerRow: 20,
        tooltipVisible: false,
        tooltipX: 0,
        tooltipY: 0,
        tooltipContent: '',

        initBookReport() {
            //this.bookReportLoadData();
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
            if (this.completionChart) {
                this.completionChart.destroy();
            }

            this.completionChart = new Chart(ctx, {
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
                            this.getPercentage('names'),
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
            if (this.missingDataChart) {
                this.missingDataChart.destroy();
            }

            this.missingDataChart = new Chart(ctx, {
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
                            this.summary.emptyNames,
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






        async bookReportLoadHeatmapData() {
            if(!this.activeId) return;

            try {
                const response = await fetch(`/api/books/${this.activeId}/chapters-heatmap`);
                this.heatmapData = await response.json();
            } catch (error) {
                console.error('Error loading heatmap data:', error);
                this.showToast(`Error loading heatmap data: ${error.message || error.detail || error}`, true);
            }
        },

        getHeatmapCellClass(percentage) {
            if (percentage >= 99) return 'bg-green-600 hover:bg-green-700';
            if (percentage >= 95) return 'bg-green-400 hover:bg-green-500';
            if (percentage >= 85) return 'bg-blue-400 hover:bg-blue-500';
            if (percentage >= 75) return 'bg-blue-600 hover:bg-blue-700';
            if (percentage >= 50) return 'bg-yellow-400 hover:bg-yellow-500';
            if (percentage >= 30) return 'bg-yellow-600 hover:bg-yellow-700';
            if (percentage >= 10) return 'bg-red-400 hover:bg-red-500';
            return 'bg-red-600 hover:bg-red-700';
        },

        getReadinessBarClass(percentage) {
            if (percentage >= 99) return 'bg-green-600';
            if (percentage >= 95) return 'bg-green-400';
            if (percentage >= 85) return 'bg-blue-400';
            if (percentage >= 75) return 'bg-blue-600';
            if (percentage >= 50) return 'bg-yellow-400';
            if (percentage >= 30) return 'bg-yellow-600';
            if (percentage >= 10) return 'bg-red-400';
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
                warnings: chapterReadinessItem.warnings
            };
        }

    };
}