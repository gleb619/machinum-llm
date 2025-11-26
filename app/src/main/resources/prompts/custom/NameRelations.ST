Analyze the following name similarity data and consolidate into unique related names.

Current Name: ${currentName}
Current Description: ${currentDescription}

List 1 - Initial Similar Names:
${initialSimilarNames}

List 2 - Contextually Similar Names:
${contextualSimilarNames}

List 3 - Additional Context Descriptions:
${additionalDescriptions}

Task:

1. Identify names that refer to the same entity (considering spelling variations, typos, phonetic similarity)
2. Consolidate similar names into canonical forms
3. Correct obvious misspellings
4. Merge semantically equivalent names

Output JSON format:
[
{
"consolidatedName": "Corrected Canonical Name",
"originalNames": ["name1", "name2"],
"confidence": 0.95,
"reason": "Phonetic similarity and semantic context match"
}
]

Only return the JSON array, no additional text.
