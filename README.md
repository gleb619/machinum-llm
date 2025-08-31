# Machinum-LLM

**Machinum-LLM** is an AI-powered book translation application designed to automate and enhance the process of
translating books using large language models (LLMs). The system supports multiple languages, integrates with local LLMs
via Ollama, and provides advanced features such as proofreading, glossary extraction, summarization, and text splitting
for optimal translation quality.

## Features

- **Book Translation**: Full book translation pipeline from source to target language.
- **Multi-Language Support**: Supports translation between various languages including English, Russian, and more.
- **LLM Integration**:
  - Uses local LLMs via Ollama (e.g., `gemma-3-27b-it-GGUF`, `T-pro-it-1.0-Q6_K-GGUF`).
  - Supports Gemini for certain tasks like SSML generation and copy-editing.
- **Advanced Text Processing**:
  - Logic-based text splitting (`balancedsentence`)
  - Chunk overlap handling
  - Customizable split modes and batch sizes
- **Translation Quality Enhancements**:
  - Proofreading in English and Russian
  - Glossary extraction and translation
  - Copy editing with scoring
  - Summarization for content consolidation
- **Audio Generation Support**:
  - Integration with TTS service (e.g., `tts:5003`)
  - MinIO integration for audio storage
- **Vector Store Integration**:
  - PostgreSQL + pgvector for semantic search and document retrieval
- **Caching & Performance Optimization**:
  - Local caching of resources (`build/cache`)
  - Configurable TTL (Time To Live)
- **Logging & Debugging**:
  - HTTP request logging
  - Detailed trace logs for debugging

## Project Structure Overview

```
.
├── build/
│   ├── cache/                 # Cached files and metadata
│   ├── http-logs/             # HTTP request/response logs
│   └── logs/                  # Application log file
├── src/main/resources/
│   └── application.properties # Main configuration file
└── README.md
```

## Configuration

The core configuration is defined in `application.properties`. Key settings include:

### Database & Vector Store

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/pgvector?reWriteBatchedInserts=true
spring.ai.vectorstore.pgvector.dimensions=384
```

### LLM Models and Providers

```properties
app.translate.model=hf.co/lmstudio-community/gemma-3-27b-it-GGUF:Q6_K
app.proofread.en.model=hf.co/lmstudio-community/gemma-3-27b-it-GGUF:Q6_K
app.glossary.translate.model=hf.co/lmstudio-community/gemma-3-27b-it-GGUF:Q6_K
```

### Translation Pipeline Settings

```properties
app.split.mode=balancedsentence
app.split.overlap=512
app.history.mode=makeupatext
app.compress.percentage=50
app.allow-tools=false
```

### TTS & Audio Services

```properties
app.tts-service.url=http://tts:5003
app.minio.endpoint=http://minio:9000
app.minio.enabled=true
app.minio.accessKey=minio
app.minio.secretKey=minio123
```

### Logging & Debugging

```properties
logging.level.machinum=DEBUG
logging.file.name=build/logs/log.txt
app.http.logs-enabled=true
app.http.logs-path=build/http-logs
```

## Usage

To run the application:

1. Ensure PostgreSQL with pgvector extension is installed and configured.
2. Start Ollama or other LLM services locally.
3. Run the Spring Boot app:
   ```bash
   ./gradlew bootRun
   ```
4. Access via API endpoints or integrate with frontend tools for book upload and translation.

## Requirements

- Java 17+
- PostgreSQL (with pgvector extension)
- Ollama or compatible LLM server
- MinIO (optional, for audio storage)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.