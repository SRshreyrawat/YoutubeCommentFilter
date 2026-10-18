# YouTube Comment Filter - Project Guide

## 1. Purpose

This project is a Chrome Manifest V3 extension backed by a Spring Boot API.
It observes YouTube Live Chat messages, sends each message to the local
backend, classifies the message, and displays only likely genuine academic
doubts in a floating dashboard.

The project is intentionally fail-closed:

- Invalid requests are rejected with HTTP 400.
- Obvious social, promotional, emoji-only, and personal messages are rejected
  before they are sent to Groq.
- A classified message is displayed only when its confidence is at least
  `0.85` and its category is `subject_matter` or `exam_logistics`.
- Classification failures hide the message instead of displaying an
  unverified comment.

## 2. Technology Stack

| Layer | Technology | Responsibility |
|---|---|---|
| Browser extension | Chrome Manifest V3 | Extension lifecycle and permissions |
| Page integration | JavaScript content script | Finds YouTube chat messages and renders the dashboard |
| Extension background | MV3 service worker | Calls the local backend outside the YouTube page context |
| API | Spring Boot 3.4.x | Receives and validates chat messages |
| Validation | Jakarta Bean Validation | Enforces required fields and length limits |
| Classifier | Groq-compatible Chat Completions API | Classifies eligible messages |
| HTTP client | Java `HttpClient` | Calls the external classifier |
| Configuration | Spring properties and `.env` | Port, feature flag, model, and credentials |

## 3. Directory Structure

```text
Youtube_Comment_Filter/
├── extension/
│   ├── manifest.json       # MV3 metadata, permissions, scripts
│   ├── content.js          # YouTube DOM observer and dashboard UI
│   ├── background.js       # Backend request service worker
│   └── styles.css          # Dashboard styling
├── src/main/java/com/jev/filter/
│   ├── YouTubeCommentFilterApplication.java      # Spring Boot entry point
│   ├── controller/FilterController.java          # HTTP boundary
│   ├── service/FilterService.java                # Filtering policy and orchestration
│   ├── client/ClassifierClient.java              # Classifier port
│   ├── client/GroqClassifier.java                # Groq adapter and local gate
│   ├── dto/FilterRequest.java                    # Validated request DTO
│   ├── dto/FilterResponse.java                   # API response DTO
│   ├── dto/ApiError.java                         # Error response DTO
│   ├── exception/ApiExceptionHandler.java        # Global API error handling
│   ├── exception/ClassificationException.java    # Classifier failure
│   └── config/WebConfig.java                     # CORS policy
├── src/main/resources/
│   └── application.properties                # Runtime configuration
├── pom.xml                                   # Maven dependencies and build
├── .env                                      # Local secrets; never commit
└── README.md
```

## 4. End-to-End Request Flow

1. YouTube adds a `yt-live-chat-text-message-renderer` element.
2. `content.js` discovers the element using a `MutationObserver` and a
   periodic fallback scan.
3. The content script extracts the author and message text.
4. The top frame sends a `jev-filter-message` to `background.js`.
5. The service worker validates the message and sends `POST /filter` to
   `http://127.0.0.1:8000`.
6. Spring validates the JSON request.
7. `GroqClassifier.isObviouslyNotRelevant` rejects clear social or
   non-academic messages locally.
8. Eligible messages are sent to Groq.
9. `FilterService` accepts only high-confidence academic categories.
10. The content script appends approved messages to the dashboard.

## 5. API Contract

### Request

```http
POST http://127.0.0.1:8000/filter
Content-Type: application/json
```

```json
{
  "author": "student123",
  "text": "What is the function of mitochondria?"
}
```

### Successful response

```json
{
  "should_show": true,
  "category": "subject_matter",
  "doubt_probability": 0.94
}
```

### Rejected response

```json
{
  "should_show": false,
  "category": "not_relevant",
  "doubt_probability": 0.0
}
```

### Validation limits

- `author`: required, maximum 100 characters
- `text`: required, maximum 1,000 characters
- Allowed CORS origins: Chrome extension origins and local development
  origins
- Allowed request methods: `POST` and `OPTIONS`

## 6. Classification Policy

The system uses two filters:

### Deterministic local gate

The local gate rejects:

- URLs and promotions
- Emoji-only or punctuation-only messages
- Very short messages
- Greetings and social replies
- Personal calls and conversations
- Birthday and relationship messages
- Location and availability questions
- Messages without an academic or question signal

This saves external API calls and prevents obvious false positives.

### Groq classification

Messages that pass the local gate are sent to Groq with a strict JSON
instruction. The controller displays a message only when:

```java
classification.confidence() >= 0.85
    && ("subject_matter".equals(category)
        || "exam_logistics".equals(category));
```

## 7. Running the Project

Requirements:

- Java 21
- Maven
- Chrome or Chromium
- A valid Groq API key stored only in local environment configuration

Start the backend:

```powershell
cd "C:\Users\DELL\Desktop\SELF-LEARN\JEV AI\Youtube_Comment_Filter"
mvn spring-boot:run
```

The API runs on `http://127.0.0.1:8000`.

Load the extension:

1. Open `chrome://extensions`.
2. Enable **Developer mode**.
3. Select **Load unpacked**.
4. Choose the `extension` directory.
5. Refresh the YouTube Live Chat page.

## 8. Testing the API Manually

Academic message:

```powershell
curl.exe -i -X POST "http://127.0.0.1:8000/filter" `
  -H "Content-Type: application/json" `
  --data '{"author":"test","text":"What is the function of mitochondria?"}'
```

Social message:

```powershell
curl.exe -i -X POST "http://127.0.0.1:8000/filter" `
  -H "Content-Type: application/json" `
  --data '{"author":"test","text":"YE SAB CALL PE BHI KAR SAKTE HAI"}'
```

The second request should return `should_show: false`.

## 9. Layering and persistence

The backend uses constructor-injected controller, service, DTO, client, and
exception layers. The controller only validates input and delegates; the
service owns filtering decisions; and the Groq client owns external HTTP
communication and response parsing.

This feature is intentionally stateless and has no database use case. There
are consequently no JPA entities or repositories to add: introducing a
database solely to satisfy a package layout would add persistence side effects
and change the existing runtime behavior. If comment history or audit storage
is added later, it should introduce a dedicated entity and Spring Data
repository behind the service without exposing that entity from the API.

## 10. Troubleshooting

| Symptom | Check |
|---|---|
| Port 8000 already in use | Find the listener with `Get-NetTCPConnection -LocalPort 8000 -State Listen` |
| Extension panel is empty | Reload the extension and refresh YouTube |
| No `/filter` network request | Inspect the YouTube page console for content-script messages |
| `Failed to fetch` | Confirm Spring Boot is listening on port 8000 |
| All messages are hidden | Check Groq credentials, API response, and backend logs |
| Old behavior remains | Restart Spring Boot after Java changes |
| Network tab appears empty | Clear DevTools filters and select **Fetch/XHR** |

## 11. Security Notes

- Never commit `.env`.
- Rotate a credential immediately if it is shared or exposed.
- Keep the API key on the backend; never place it in extension JavaScript.
- Keep CORS restricted to known extension and local origins.
- Keep validation limits to reduce abuse and excessive classifier costs.
- Avoid logging full chat text in production logs.
