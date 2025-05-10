### **Silero SSML Wiki:**

Speech Synthesis Markup Language (SSML) is an XML-based markup language that you can use to fine-tune your text to
speech output attributes such as pitch, pronunciation, speaking rate, volume, and more. It gives you more control and
flexibility than plain text input.

## Example

```python
ssml_sample = """
     <speak>
     <p>
         Когда я просыпаюсь, <prosody rate="x-slow">я говорю довольно медленно</prosody>.
         Потом я начинаю говорить своим обычным голосом,
         <prosody pitch="x-high"> а могу говорить тоном выше </prosody>,
         или <prosody pitch="x-low">наоборот, ниже</prosody>.
         Потом, если повезет – <prosody rate="fast">я могу говорить и довольно быстро.</prosody>
         А еще я умею делать паузы любой длины, например две секунды <break time="2000ms"/>.
         <p>
           Также я умею делать паузы между параграфами.
         </p>
         <p>
           <s>И также я умею делать паузы между предложениями</s>
           <s>Вот например как сейчас</s>
         </p>
     </p>
     </speak>
     """

sample_rate = 48000
speaker = 'xenia'
audio = model.apply_tts(ssml_text=ssml_sample,
                        speaker=speaker,
                        sample_rate=sample_rate)
```

## Supported tags

### speak

The root tag

```
<speak>В недрах тундры выдры в г+етрах т+ырят в вёдра ядра к+едров.</speak>
```

### break

Add a pause with a specified duration

| Attribute  | Description                                                                                                                                                                                               |
|------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `time`     | Pause duration in seconds or milliseconds, for example 500ms or 5s                                                                                                                                        |
| `strength` | Default value is `medium`. Possible values: - `x-weak`, `weak`, `medium`, `strong`, `x-strong`. This tag has a lower priority: `<break time="1s" strength="x-weak"/>` will produce a 1 second long pause. |

**Example**

```
<speak>
    Пауза длиной в три секунды<break time="3s"/>
    После этого речь продолжается.
</speak>
```

### prosody

Modifies the pitch, and rate of the tagged speech.

| Attribute | Description                                                                                                                     |
|-----------|---------------------------------------------------------------------------------------------------------------------------------|
| rate      | Modify the rate of the speech: `x-slow`, `slow`, `medium`, `fast`, `x-fast`: Set the rate to a predefined value.                |
| pitch     | Raise or lower the tone (pitch) of the speech: `x-low`, `low`, `medium`, `high`, `x-high`: Set the pitch to a predefined value. |

You can combine prosody with other tags.

```
<speak>
    Когда я просыпаюсь, <prosody rate="x-slow">я говорю довольно медленно</prosody>.
    Потом я начинаю говорить своим обычным голосом, <prosody pitch="x-high">а могу говорить тоном выше.</prosody>
</speak>
```

### p

Represents a paragraph, equivalent to `x-strong` pause.

```
<speak>                                         
    <p>Первый параграф.</p>       
    <p>Второй параграф.</p>
</speak>
```

### s

Represents a sentence, equivalent to `strong` pause.

```
<speak>                                         
    <s>Первое предложение.</s><s>Второе предложение.</s>
</speak>
```

### **Task:**

Convert plain text into valid SSML (Speech Synthesis Markup Language) following Silero Models' SSML guidelines. The
output must enhance speech synthesis quality by incorporating pauses, prosody adjustments, phonetic clarity, and other
SSML features supported by Silero TTS models .

---

### **Guidelines:**

1. **Analyze Input Text:**
    - Identify natural pauses, sentence boundaries, and punctuation (e.g., commas, periods, question marks).
    - Detect emphasis points, speed variations, or tonal shifts in the text.

2. **Apply SSML Tags:**
    - **Pauses:** Use `<break time="Xms"/>` for pauses, where `X` is duration in milliseconds (e.g., `500ms` for a
      medium pause).
    - **Prosody:** Adjust speech rate and pitch with `<prosody rate="rateType" pitch="pitchType">` (e.g., available
      values for rate are `x-slow`, `slow`, `medium`, `fast`, `x-fast`. For pitch
      are `x-low`, `low`, `medium`, `high`, `x-high`).
    - **Sentences/Paragraphs:** Wrap text in `<s>` or `<p>` tags to denote sentence or paragraph boundaries.

3. **Ensure Compatibility:**
    - Use Silero-supported SSML features only (e.g., avoid unsupported tags like `<sub>` or `<audio>`).
    - Validate against Silero’s SSML implementation.

4. **Output Requirements:**
    - Wrap the final output in `<speak>` tags.

---

### **Example Input/Output:**

**Input Text:**

```  
"Привет, мир! Это пример текста для синтеза речи. Пауза здесь... а теперь продолжим."  
```  

**Output SSML:**

```  
<speak>
    <s>Привет, мир!</s>
    <break time="300ms"/>
    <s>Это пример текста для синтеза речи.</s>
    <break time="500ms"/>
    <prosody rate="slow">
        <s>Пауза здесь...</s>
    </prosody>
    <break time="200ms"/>
    <s>А теперь продолжим.</s>
</speak>  
```  

---

### **Validation Steps:**

1. **Check Syntax:** Ensure all tags are properly closed and nested.
2. **Test Compatibility:** Verify tags against Silero’s SSML support.
3. **Optimize for Naturalness:** Prioritize pauses and prosody adjustments that mimic human speech patterns.

---

### **Notes:**

- Silero’s SSML supports multiple sampling rates (8kHz, 24kHz, 48kHz) and batch processing for long texts.
- Refer to Silero’s SSML Wiki for tag-specific constraints and advanced use cases.
