/**
 * Creates an Alpine.js data object with processor settings functionality
 */
export function sidebarChapterSelectorApp() {
    return {

        ruleTypeDropdown: false,
        chapterSource: {
            ruleType: 'all',
            newRuleItem: '',
            rangeRuleConfig: {
                min: 0,
                max: 10
            },
            specificRuleConfig: {
                items: [1, 4, 7]
            },
            selectedRuleFilters: [],
            availableRuleFilters: [
                { id: 'odd', name: 'Odd Numbers', description: 'Only odd items' },
                { id: 'even', name: 'Even Numbers', description: 'Only even items' },
                { id: 'prime', name: 'Prime Numbers', description: 'Prime numbered items' },
                { id: 'multiples_of_5', name: 'Multiples of 5', description: 'Divisible by 5' },
                { id: 'with_problems', name: 'With Problems', description: 'Problematic items' },
                { id: 'completed', name: 'Completed', description: 'Already processed' },
                { id: 'pending', name: 'Pending', description: 'Awaiting processing' },
                { id: 'high_priority', name: 'High Priority', description: 'Priority items' }
            ],
        },

        initChapterSource(type) {
            this.loadValue('chapterSource', this.chapterSource);
        },

        setRuleType(type) {
            this.chapterSource.ruleType = type;
            this.ruleTypeDropdown = false;

            if(type == 'range') {
                this.chapterSource.rangeRuleConfig.max = (this.currentBook?.chaptersCount || 100);
            }

            this.changeValue('chapterSource', this.chapterSource);
        },

        getRuleTypeLabel() {
            const labels = {
                'all': 'All items',
                'range': 'Min/Max Range',
                'specific': 'Specific Items',
                'filter': 'Named Filters'
            };

            return labels[this.chapterSource.ruleType] || 'Select Type';
        },

        addRuleItem() {
            const item = parseInt(this.chapterSource.newRuleItem);
            if (item && !this.chapterSource.specificRuleConfig.items.includes(item)) {
                this.chapterSource.specificRuleConfig.items.push(item);
                this.chapterSource.specificRuleConfig.items.sort((a, b) => a - b);
            }
            this.chapterSource.newRuleItem = '';
            this.changeValue('chapterSource', this.chapterSource);
        },

        removeRuleItem(item) {
            this.chapterSource.specificRuleConfig.items = this.chapterSource.specificRuleConfig.items.filter(i => i !== item);
            this.changeValue('chapterSource', this.chapterSource);
        },

        toggleQuickRuleItem(item) {
            if (this.chapterSource.specificRuleConfig.items.includes(item)) {
                this.removeRuleItem(item);
            } else {
                this.chapterSource.specificRuleConfig.items.push(item);
                this.chapterSource.specificRuleConfig.items.sort((a, b) => a - b);
            }
            this.changeValue('chapterSource', this.chapterSource);
        },

        generateRuleConfig() {
            const config = {
                ruleType: this.chapterSource.ruleType,
            };

            if (this.chapterSource.ruleType === 'range') {
                config.range = {
                    min: parseInt(this.chapterSource.rangeRuleConfig.min),
                    max: parseInt(this.chapterSource.rangeRuleConfig.max),
                    count: parseInt(this.chapterSource.rangeRuleConfig.max) - parseInt(this.chapterSource.rangeRuleConfig.min) + 1
                };
            } else if (this.chapterSource.ruleType === 'specific') {
                config.specific = {
                    items: [...this.chapterSource.specificRuleConfig.items],
                    count: this.chapterSource.specificRuleConfig.items.length
                };
            } else if (this.chapterSource.ruleType === 'filter') {
                config.filters = {
                    selected: [...this.chapterSource.selectedRuleFilters],
                    definitions: this.chapterSource.selectedRuleFilters.map(id =>
                        this.chapterSource.availableRuleFilters.find(f => f.id === id)
                    )
                };
            }

            return config;
        },

    };
}