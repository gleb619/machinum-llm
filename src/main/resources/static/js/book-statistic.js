// Import core functions
import { addSearchParam, formatTime, formatBigNumberWithSpaces } from './core.js';

/**
 * Creates an Alpine.js data object with list functionality
 */
export function statisticApp() {
    return {
        collapsed: false,
        open: false,
        runnerPanelOpen: false,
        runner: '',
        isExecuting: false,
        startTime: null,
        timePassed: '00:00:00',
        selectedDate: new Date().toISOString().split('T')[0],
        statistics: [],
        autoRefresh: false,
        refreshInterval: null,
        totalTokens: '0',
        avgConversion: 0,
        operationsTime: '00:00:00',
        tokenChart: null,
        timeChart: null,
        operationTypeChart: null,
        tokensComparisonChart: null,
        statisticCurrentPage: 1,
        statisticPageSize: 10,
        statisticSearchTerm: '',


        initStatistic() {
            this.loadState('collapsed');
        },

        async executeOperation(operationName) {
            addSearchParam('bookId', this.activeId);

            this.isExecuting = true;
            this.startTime = Date.now();
            this.timePassed = '00:00:00';

            const updateTimer = setInterval(() => {
                const elapsedSeconds = Math.floor((Date.now() - this.startTime) / 1000);
                const hours = String(Math.floor(elapsedSeconds / 3600)).padStart(2, '0');
                const minutes = String(Math.floor((elapsedSeconds % 3600) / 60)).padStart(2, '0');
                const seconds = String(elapsedSeconds % 60).padStart(2, '0');
                this.timePassed = `${hours}:${minutes}:${seconds}`;
            }, 1000);

            let retries = 0;
            const maxRetries = 10; // Maximum number of retries
            const retryDelayBase = 2000; // Base delay in milliseconds

            while (retries < maxRetries) {
                try {
                    const response = await axios.post(`/api/books/${this.activeId}/execute`, {
                        operationName: operationName,
                        forceMode: false,
                        runner: this.runner
                    });

                    // Check if the backend responded with a success status
                    if (response.status > 199 && response.status < 300) {
                        console.log(`Executed operation: ${operationName}`);
                        if(this.fetchBooks) {
                            this.fetchBooks();
                        }
                        break; // Exit the retry loop on success
                    }
                } catch (error) {
                    if (error.response && error.response.headers['x-retry-at']) {
                        // Extract the retry time from the X-Retry-At header
                        const retryAt = new Date(error.response.headers['x-retry-at']).getTime();
                        const currentTime = Date.now();
                        const delay = Math.max(retryAt - currentTime, retryDelayBase * Math.pow(2, retries));

                        console.log(`Backend busy. Retrying in ${delay}ms...`);
                        await new Promise(resolve => setTimeout(resolve, delay)); // Wait before retrying
                        retries++;
                    } else {
                        console.error(`Error executing operation: ${operationName}`, error);
                        break; // Exit the retry loop on non-retryable errors
                    }
                }
            }

            clearInterval(updateTimer);
            this.isExecuting = false;

            if (retries === maxRetries) {
                console.error(`Max retries exceeded for operation: ${operationName}`);
            }
        },

        fetchStatisticsData() {
            const params = {
                date: this.selectedDate
            };

            axios.get('/api/statistics', { params })
                .then(response => {
                    this.statistics = response.data;
                    this.calculateMetrics();
                    this.renderCharts();
                })
                .catch(error => {
                    console.error('Error fetching statistics:', error);
                });
        },

        get statisticFilteredData() {
          const searchLower = this.statisticSearchTerm.toLowerCase();
          return this.statistics
            .filter(item => {
              return this.statisticSearchTerm === '' ||
                Object.values(item).some(val =>
                  val.toString().toLowerCase().includes(searchLower)
                );
            });
        },

        get statisticTotalPages() {
          return Math.ceil(this.statisticFilteredData.length / this.statisticPageSize);
        },

        get paginatedData() {
          const startIndex = (this.statisticCurrentPage - 1) * this.statisticPageSize;
          return this.statisticFilteredData.slice(startIndex, startIndex + this.statisticPageSize);
        },

        calculateMetrics() {
            this.totalTokens = formatBigNumberWithSpaces(this.statistics.reduce((sum, stat) => sum + stat.outputHistoryTokens, 0));

            const totalConversion = this.statistics.reduce((sum, stat) => sum + (stat.conversionPercent || 0), 0);
            this.avgConversion = this.statistics.length > 0
                ? Math.round((totalConversion / this.statistics.length) * 100) / 100
                : 0;

            const latestStat = this.statistics.sort((a, b) => new Date(b.date) - new Date(a.date))[0];
            this.operationsTime = formatTime(this.statistics.reduce((sum, stat) => sum + stat.operationTimeSeconds, 0));
        },

        renderCharts() {
            this.renderTokenChart();
            this.renderTimeChart();
            this.renderOperationTypeChart();
            this.renderTokensComparisonChart();
        },

        renderLineChart(id, fieldName, label, color) {
            const ctx = document.getElementById(id).getContext('2d');

            // Group data by date
            const groupedData = this.statistics.reduce((acc, stat) => {
                const date = new Date(stat.operationDate).toLocaleTimeString();
                if (!acc[date]) {
                    acc[date] = 0;
                }
                acc[date] += stat[fieldName];
                return acc;
            }, {});

            const labels = Object.keys(groupedData).sort((a, b) => new Date(a) - new Date(b));
            const data = labels.map(date => groupedData[date]);

            if (this[id]) {
                this[id].destroy();
            }

            this[id] = new Chart(ctx, {
                type: 'line',
                data: {
                    labels: labels,
                    datasets: [{
                        label: label,
                        data: data,
                        borderColor: color,
                        backgroundColor: 'rgba(59, 130, 246, 0.2)',
                        tension: 0.3,
                        fill: true
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: {
                            beginAtZero: true
                        }
                    }
                }
            });
        },

        renderTokenChart() {
            this.renderLineChart('tokenChart', 'outputHistoryTokens', 'Tokens Used', 'rgba(59, 130, 246, 1)');
        },

        renderTimeChart() {
            this.renderLineChart('timeChart', 'operationTimeSeconds', 'Time spent', 'rgba(16, 185, 129, 1)');
        },

        renderOperationTypeChart() {
            const ctx = document.getElementById('operationTypeChart').getContext('2d');

            // Group by operation type
            const typeData = this.statistics.reduce((acc, stat) => {
                const type = stat.operationType || 'Unknown';
                if (!acc[type]) {
                    acc[type] = 0;
                }
                acc[type] += 1;
                return acc;
            }, {});

            const labels = Object.keys(typeData);
            const data = labels.map(type => typeData[type]);

            if (this.operationTypeChart) {
                this.operationTypeChart.destroy();
            }

            this.operationTypeChart = new Chart(ctx, {
                type: 'doughnut',
                data: {
                    labels: labels,
                    datasets: [{
                        data: data,
                        backgroundColor: [
                            'rgba(59, 130, 246, 0.7)',
                            'rgba(16, 185, 129, 0.7)',
                            'rgba(139, 92, 246, 0.7)',
                            'rgba(245, 158, 11, 0.7)',
                            'rgba(239, 68, 68, 0.7)',
                            'rgba(75, 85, 99, 0.7)'
                        ]
                    }]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false
                }
            });
        },

        renderTokensComparisonChart() {
            const ctx = document.getElementById('tokensComparisonChart').getContext('2d');

            // Group data by date (top 7 dates with most data)
            const dateGroups = {};
            this.statistics.forEach(stat => {
                const date = new Date(stat.date).toLocaleDateString();
                if (!dateGroups[date]) {
                    dateGroups[date] = {
                        input: 0,
                        output: 0
                    };
                }
                dateGroups[date].input += stat.inputTokens || 0;
                dateGroups[date].output += stat.outputTokens || 0;
            });

            const sortedDates = Object.keys(dateGroups)
                .sort((a, b) => (dateGroups[b].input + dateGroups[b].output) - (dateGroups[a].input + dateGroups[a].output))
                .slice(0, 7);

            const labels = sortedDates;
            const inputData = sortedDates.map(date => dateGroups[date].input);
            const outputData = sortedDates.map(date => dateGroups[date].output);

            if (this.tokensComparisonChart) {
                this.tokensComparisonChart.destroy();
            }

            this.tokensComparisonChart = new Chart(ctx, {
                type: 'bar',
                data: {
                    labels: labels,
                    datasets: [
                        {
                            label: 'Input Tokens',
                            data: inputData,
                            backgroundColor: 'rgba(59, 130, 246, 0.7)'
                        },
                        {
                            label: 'Output Tokens',
                            data: outputData,
                            backgroundColor: 'rgba(16, 185, 129, 0.7)'
                        }
                    ]
                },
                options: {
                    responsive: true,
                    maintainAspectRatio: false,
                    scales: {
                        y: {
                            beginAtZero: true
                        }
                    }
                }
            });
        },

        toggleAutoRefresh() {
            if (this.autoRefresh) {
                this.refreshInterval = setInterval(() => {
                    this.fetchStatisticsData();
                }, 60000);
            } else {
                clearInterval(this.refreshInterval);
            }
        },

        formatDate(dateString) {
            return new Date(dateString).toLocaleTimeString();
        },

        prevPage() {
          if (this.statisticCurrentPage > 1) {
            this.statisticCurrentPage--;
          }
        },

        nextPage() {
          if (this.statisticCurrentPage < this.statisticTotalPages) {
            this.statisticCurrentPage++;
          }
        },

        paginationPages() {
          let pages = [];
          const maxPagesToShow = 5;

          if (this.statisticTotalPages <= maxPagesToShow) {
            // Show all pages if there are few
            for (let i = 1; i <= this.statisticTotalPages; i++) {
              pages.push(i);
            }
          } else {
            // Show pages around current page
            let startPage = Math.max(1, this.statisticCurrentPage - Math.floor(maxPagesToShow / 2));
            let endPage = Math.min(this.statisticTotalPages, startPage + maxPagesToShow - 1);

            // Adjust if we're at the end
            if (endPage - startPage + 1 < maxPagesToShow) {
              startPage = Math.max(1, endPage - maxPagesToShow + 1);
            }

            for (let i = startPage; i <= endPage; i++) {
              pages.push(i);
            }
          }

          return pages;
        },

        statisticAppInit() {
            this.fetchStatisticsData();
        }
    };
}