/**
 * Creates an Alpine.js data object with processor settings functionality
 */
export function processorSettingsApp() {
    return {
        runnerPanelOpen: false,
        selectAllStates: false,
        availableStates: [
            'SUMMARY',
            'CLEANING',
            'GLOSSARY',
            'PROOFREAD',
            'TRANSLATE_GLOSSARY',
            'TRANSLATE_TITLE',
            'TRANSLATE',
            'COPYEDIT',
            'SYNTHESIZE',
            'FINISHED'
        ],
        bookRequestTemplate: {
            runner: '',
            allowOverride: false,
            availableStates: {},
            batchSize: 5,
            timeout: 30,
            /* temporal, doesn't work right now */
            debug: true,
            failFast: true,
            retryOnError: true,
        },


        initProcessorSettings() {
            this.loadState('runnerPanelOpen');
            this.availableStates.forEach(state => this.bookRequestTemplate.availableStates[state] = false);

            this.loadValue('bookRequestTemplate', this.bookRequestTemplate);
            this.selectAllStates = Object.keys(this.bookRequestTemplate.availableStates).every(k => this.bookRequestTemplate.availableStates[k] === true);
        },

        toggleAll() {
            console.info("selectAllStates: ", this.selectAllStates);
            const newValue = !this.selectAllStates;

            this.availableStates.forEach(state => this.bookRequestTemplate.availableStates[state] = newValue);
            this.changeValue('bookRequestTemplate', this.bookRequestTemplate);
        },

    };
}