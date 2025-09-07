You are tasked with scoring a chapter’s text translation from English to Russian. The translation will be evaluated
based on the following criteria:

Fluency: Does the translation sound natural and fluent for a Russian native speaker?

Accuracy: Does the translation accurately reflect the meaning of the original English text?

Adaptation: Are culturally specific references or idioms adapted appropriately for a Russian audience?

Coherence: Is the translation consistent with the glossary, previous translations, and the overall tone of the text?

Terminology: Does the translation correctly use terms and names as defined in the glossary?

Provide the score and feedback in JSON format, following the schema below.
JSON Schema:
{
"$schema": "http://json-schema.org/draft-07/schema#",
"title": "Translation Evaluation Schema",
"type": "object",
"properties": {
"fluency": {
"type": "object",
"properties": {
"score": {
"type": "integer",
"minimum": 0,
"maximum": 10
},
"comment": {
"type": "string"
},
"suggestions": {
"type": "array",
"items": {
"type": "string"
},
"minItems": 0,
"maxItems": 20
}
},
"required": ["score", "comment", "suggestions"],
"additionalProperties": false
},
"accuracy": {
"type": "object",
"properties": {
"score": {
"type": "integer",
"minimum": 0,
"maximum": 10
},
"comment": {
"type": "string"
},
"suggestions": {
"type": "array",
"items": {
"type": "string"
},
"minItems": 0,
"maxItems": 20
}
},
"required": ["score", "comment", "suggestions"],
"additionalProperties": false
},
"adaptation": {
"type": "object",
"properties": {
"score": {
"type": "integer",
"minimum": 0,
"maximum": 10
},
"comment": {
"type": "string"
},
"suggestions": {
"type": "array",
"items": {
"type": "string"
},
"minItems": 0,
"maxItems": 20
}
},
"required": ["score", "comment", "suggestions"],
"additionalProperties": false
},
"coherence": {
"type": "object",
"properties": {
"score": {
"type": "integer",
"minimum": 0,
"maximum": 10
},
"comment": {
"type": "string"
},
"suggestions": {
"type": "array",
"items": {
"type": "string"
},
"minItems": 0,
"maxItems": 20
}
},
"required": ["score", "comment", "suggestions"],
"additionalProperties": false
},
"terminology": {
"type": "object",
"properties": {
"score": {
"type": "integer",
"minimum": 0,
"maximum": 10
},
"comment": {
"type": "string"
},
"suggestions": {
"type": "array",
"items": {
"type": "string"
},
"minItems": 0,
"maxItems": 20
}
},
"required": ["score", "comment", "suggestions"],
"additionalProperties": false
}
},
"required": ["fluency", "accuracy", "adaptation", "coherence", "terminology"],
"additionalProperties": false
}

Scoring System:

0-2: Poor (significant errors, awkward phrasing, or major inconsistencies).

3-4: Fair (some errors or inconsistencies, but the overall meaning is preserved).

5-6: Good (minor errors, mostly fluent and consistent).

7-8: Very Good (few errors, fluent and natural, adheres well to the glossary).

9-10: Excellent (no errors, perfectly fluent, natural, and consistent).

Guidelines:

Provide a Score: Assign a score from 0 to 10 based on the criteria above.

Detailed Feedback: Provide detailed feedback explaining the score, highlighting strengths and areas for improvement.

Examples: Include specific examples from the text to support your feedback.

Use Glossary: Refer to the provided glossary for term descriptions and translations to ensure coherence and accuracy.

Glossary Example:
`Elara` - it's a Character; Example of usage: The protagonist, a low-ranking clerk in a powerful merchant guild. Russian
translation is: `Элара`.
`Merchant Guild` - it's a Faction; Example of usage: A powerful organization involved in illegal activities. Russian
translation is: `Торговая гильдия`.
`Hidden Ledger` - it's a Object; Example of usage: A secret document revealing the guild’s involvement in illegal
activities. Russian translation is: `Скрытая бухгалтерская книга`.
`Mysterious Figure` - it's a Character; Example of usage: An unknown individual who begins following Elara. Russian
translation is: `Таинственная фигура`.

Example Input (English Text):
Kael, a young villager, discovers an ancient artifact in the forest. The artifact grants him mysterious powers, but it
also attracts the attention of a dark organization. Kael’s best friend, Lira, warns him about the dangers of using the
artifact, but Kael is determined to uncover its secrets.

Example Input (Russian Translation):
Каэль, молодой житель деревни, находит древний артефакт в лесу. Артефакт дает ему странные силы, но также привлекает
внимание темной группы. Лира, лучшая подруга Каэля, предупреждает его о рисках использования артефакта, но Каэль хочет
узнать его тайны.

Example Output (JSON):
{
"fluency": {
"score": 6,
"comment": "The text is mostly fluent but contains awkward phrasing. Example: 'дает ему странные силы' (grants him
strange powers) sounds unnatural.",
"suggestions": [
"Rephrase 'дает ему странные силы' to 'наделяет его таинственными силами.'",
"Replace 'хочет узнать его тайны' with 'полон решимости раскрыть его тайны.'"
]
},
"accuracy": {
"score": 7,
"comment": "The translation mostly reflects the original meaning but contains minor inaccuracies. Example: 'dark
organization' is translated as 'темная группа' (too generic) instead of 'тёмная организация.'",
"suggestions": [
"Replace 'темная группа' with 'тёмная организация.'",
"Rephrase 'риски использования артефакта' to 'опасности использования артефакта.'"
]
},
"adaptation": {
"score": 8,
"comment": "The translation includes some culturally appropriate terms but misses others. Example: 'странные силы' (
strange powers) could be replaced with 'таинственные силы.'",
"suggestions": [
"Replace 'странные силы' with 'таинственные силы.'",
"Rephrase 'предупреждает его о рисках' to 'предупреждает его об опасностях.'"
]
},
"coherence": {
"score": 5,
"comment": "Some terms are inconsistent. Example: 'Lira' is correctly used, but 'dark organization' is mistranslated
as 'темная группа' instead of 'тёмная организация.'",
"suggestions": [
"Ensure 'Lira' is consistently referred to as 'Лира.'",
"Rephrase 'привлекает внимание темной группы' to 'привлекает внимание тёмной организации.'"
]
},
"terminology": {
"score": 9,
"comment": "The glossary is mostly followed, but 'dark organization' is not translated as per the glossary.",
"suggestions": [
"Replace 'темная группа' with 'тёмная организация' to match the glossary.",
"Ensure 'Ancient Artifact' is consistently translated as 'древний артефакт.'"
]
}
}

Explanation of the Structure:
Fluency:
Evaluates how natural and fluent the translation sounds in Russian.
Includes suggestions for improving awkward phrasing or unnatural expressions.

Accuracy:
Assesses how accurately the translation reflects the original meaning.
Includes suggestions for correcting inaccuracies or deviations from the source text.

Adaptation:
Evaluates how well culturally specific references or idioms are adapted for a Russian audience.
Includes suggestions for improving cultural appropriateness.

Coherence:
Checks for coherence in terminology, character names, and key concepts.
Includes suggestions for resolving inconsistencies.

Terminology:
Assesses how well the translation adheres to the provided glossary.
Includes suggestions for correcting deviations from glossary terms.

This example demonstrates how a translation with significant issues can be scored and improved with detailed feedback
and actionable suggestions. The JSON output follows the schema and provides a comprehensive evaluation.

Explanation of the Output:
Fluency (6/10):
The text is mostly fluent but contains awkward phrasing.
Suggestions:
Rephrase "дает ему странные силы" to "наделяет его таинственными силами."
Replace "хочет узнать его тайны" with "полон решимости раскрыть его тайны."

Accuracy (7/10):
The translation mostly reflects the original meaning but contains minor inaccuracies.
Suggestions:
Replace "темная группа" with "тёмная организация."
Rephrase "риски использования артефакта" to "опасности использования артефакта."

Adaptation (8/10):
The translation includes some culturally appropriate terms but misses others.
Suggestions:
Replace "странные силы" with "таинственные силы."
Rephrase "предупреждает его о рисках" to "предупреждает его об опасностях."

Coherence (5/10):
Some terms are inconsistent.
Suggestions:
Ensure "Lira" is consistently referred to as "Лира."
Rephrase "привлекает внимание темной группы" to "привлекает внимание тёмной организации."

Terminology (9/10):
The glossary is mostly followed, but "dark organization" is not translated as per the glossary.
Suggestions:
Replace "темная группа" with "тёмная организация" to match the glossary.
Ensure "Ancient Artifact" is consistently translated as "древний артефакт."

This structure ensures the feedback is organized, actionable, and easy to follow, with clear suggestions for improving
the translation.

Note that, the "suggestions" array may be empty, if no details for improvement were found. The maximum number of
elements for
"suggestions" is 20, feel free to add various suggestions to improve the translation.

Additional Examples for Clarity:

Example Input (English Text):
Elara, a low-ranking clerk in a powerful merchant guild, discovers a hidden ledger revealing the guild’s involvement in
illegal activities. Fearing for her safety, she begins investigating but is noticed by a mysterious figure. The figure,
whose identity remains unknown, begins following her, adding to her paranoia.

Example Input (Russian Translation):
Элара, низкий клерк ранга в сильной торговой группе, находит скрытую книгу, показывающую участие группы в незаконных
активностях. Боясь за свою безопасность, она начинает расследование, но её видит странная фигура. Фигура, чья личность
неизвестна, начинает следовать за ней, добавляя к её паранойе.

Example Output (JSON):
{
"fluency": {
"score": 4,
"comment": "The text is grammatically flawed and unnatural. Example: 'добавляя к её паранойе' is a direct translation
that does not sound natural in Russian.",
"suggestions": [
"Rephrase 'добавляя к её паранойе' to 'усугубляя её паранойю.'",
"Replace 'находит скрытую книгу' with 'обнаруживает скрытую бухгалтерскую книгу.'",
"Rephrase 'показывающую участие группы' to 'которая раскрывает участие гильдии.'",
"Replace 'низкий клерк ранга' with 'младший клерк.'",
"Rephrase 'сильной торговой группе' to 'могущественной торговой гильдии.'"
]
},
"accuracy": {
"score": 5,
"comment": "The translation misrepresents key terms and loses some of the original meaning. Example: 'hidden ledger' is
mistranslated as 'скрытая книга' (omits 'ledger').",
"suggestions": [
"Replace 'скрытая книга' with 'скрытая бухгалтерская книга.'",
"Rephrase 'незаконных активностях' to 'незаконной деятельности.'",
"Replace 'странная фигура' with 'таинственная фигура.'",
"Rephrase 'начинает следовать за ней' to 'начинает преследовать её.'"
]
},
"adaptation": {
"score": 4,
"comment": "The translation includes overly literal phrases (e.g., 'добавляя к её паранойе') that are culturally
jarring.",
"suggestions": [
"Rephrase 'добавляя к её паранойе' to 'усугубляя её паранойю.'",
"Replace 'незаконных активностях' with 'незаконной деятельности.'",
"Rephrase 'сильной торговой группе' to 'могущественной торговой гильдии.'"
]
},
"coherence": {
"score": 3,
"comment": "Terms like 'Merchant Guild' are translated inconsistently (e.g., 'торговой группы' instead of 'торговой
гильдии').",
"suggestions": [
"Replace 'торговой группы' with 'торговой гильдии.'",
"Ensure 'hidden ledger' is consistently translated as 'скрытая бухгалтерская книга.'",
"Replace 'странная фигура' with 'таинственная фигура.'"
]
},
"terminology": {
"score": 2,
"comment": "The glossary is mostly ignored. Example: 'Mysterious Figure' is translated as 'странная фигура' instead of '
Таинственная фигура.'",
"suggestions": [
"Replace 'странная фигура' with 'таинственная фигура.'",
"Ensure 'Merchant Guild' is translated as 'торговая гильдия.'",
"Replace 'скрытая книга' with 'скрытая бухгалтерская книга.'"
]
}
}

Explanation of the Output:
Summary of Issues:
Fluency:
Awkward phrasing and unnatural expressions.
Example: "добавляя к её паранойе" (adding to her paranoia) is overly literal.

Accuracy:
Key terms like "hidden ledger" and "mysterious figure" are mistranslated.
Example: "скрытая книга" (hidden book) omits "ledger."

Adaptation:
Overly literal translations that are culturally inappropriate.
Example: "незаконных активностях" (illegal activities) is awkward in Russian.

Coherence:
Inconsistent use of terms like "Merchant Guild" and "hidden ledger."
Example: "торговой группы" (trade group) instead of "торговой гильдии" (merchant guild).

Terminology:
Glossary terms are ignored or mistranslated.
Example: "странная фигура" (strange figure) instead of "таинственная фигура" (mysterious figure).

This example demonstrates how a poor translation can be evaluated and improved with detailed feedback and actionable
suggestions. The JSON output follows the schema and provides a comprehensive evaluation.