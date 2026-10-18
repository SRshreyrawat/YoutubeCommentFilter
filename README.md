# Teacher's Live Chat Filter

This project contains a Chrome extension and a Spring Boot service that surfaces
relevant student doubts from YouTube Live Chat.

## Run the Spring Boot service

Requirements: Java 21 and Maven.

```powershell
mvn spring-boot:run
```

The service listens on `http://127.0.0.1:8000`. Filtering is enabled by
default; messages are hidden when Groq cannot classify them.

To enable Groq classification, copy `.env` values into the PowerShell session
before starting. Replace the placeholder with a new Groq API key first:

```powershell
$env:ENABLE_JEV_FILTER="true"
$env:GROQ_API_KEY="your-new-groq-api-key"
mvn spring-boot:run
```

Spring Boot imports the local `.env` file when it exists. Never commit a real
API key.

The extension expects `POST /filter` to accept `{ "author": "...", "text": "..." }`
and return `should_show`, `category`, and `doubt_probability`. The API accepts
authors up to 100 characters and messages up to 1,000 characters. Requests
must originate from the extension or local development origins; the service
does not allow arbitrary web pages to call the endpoint.
Obvious social messages, greetings, links, emoji-only messages, and personal
conversation are rejected locally before sending text to Groq. A message must
also contain an explicit academic or question signal to be eligible for display.

## Documentation

- [Project guide](./docs/PROJECT_GUIDE.md) - architecture, structure, API
  contract, runtime flow, security, and troubleshooting
- [UML diagrams](./docs/UML_DIAGRAMS.md) - component, sequence, class,
  state, and deployment diagrams
- [Interview questions](./docs/INTERVIEW_QUESTIONS.md) - project, Spring Boot,
  extension, API, reliability, and security questions with answers

## Load the extension

Open `chrome://extensions`, enable Developer mode, choose **Load unpacked**,
and select the `extension` folder. Open a YouTube Live Chat page after the
Spring Boot service is running.
