/**
 * Creates an Alpine.js data object with processor settings functionality
 */
export function sidebarChapterSelectorApp() {
    return {

        ruleType: 'all',
        ruleTypeDropdown: false,
        newRuleItem: '',
        rangeRuleConfig: {
            min: 1,
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

        setRuleType(type) {
            this.ruleType = type;
            this.ruleTypeDropdown = false;
        },

        getRuleTypeLabel() {
            const labels = {
                'all': 'All items',
                'range': 'Min/Max Range',
                'specific': 'Specific Items',
                'filter': 'Named Filters'
            };

            return labels[this.ruleType] || 'Select Type';
        },

        addRuleItem() {
            const item = parseInt(this.newRuleItem);
            if (item && !this.specificRuleConfig.items.includes(item)) {
                this.specificRuleConfig.items.push(item);
                this.specificRuleConfig.items.sort((a, b) => a - b);
            }
            this.newRuleItem = '';
        },

        removeRuleItem(item) {
            this.specificRuleConfig.items = this.specificRuleConfig.items.filter(i => i !== item);
        },

        toggleQuickRuleItem(item) {
            if (this.specificRuleConfig.items.includes(item)) {
                this.removeRuleItem(item);
            } else {
                this.specificRuleConfig.items.push(item);
                this.specificRuleConfig.items.sort((a, b) => a - b);
            }
        },

        generateRuleConfig() {
            const config = {
                ruleType: this.ruleType,
            };

            if (this.ruleType === 'range') {
                config.range = {
                    min: parseInt(this.rangeRuleConfig.min),
                    max: parseInt(this.rangeRuleConfig.max),
                    count: parseInt(this.rangeRuleConfig.max) - parseInt(this.rangeRuleConfig.min) + 1
                };
            } else if (this.ruleType === 'specific') {
                config.specific = {
                    items: [...this.specificRuleConfig.items],
                    count: this.specificRuleConfig.items.length
                };
            } else if (this.ruleType === 'filter') {
                config.filters = {
                    selected: [...this.selectedRuleFilters],
                    definitions: this.selectedRuleFilters.map(id =>
                        this.availableRuleFilters.find(f => f.id === id)
                    )
                };
            }

            return config;
        },

    };
}