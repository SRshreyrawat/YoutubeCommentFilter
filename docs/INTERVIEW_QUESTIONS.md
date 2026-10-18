# Interview Questions and Answers

## Project and Architecture

### 1. Explain the project in one minute.

It is a Chrome Manifest V3 extension connected to a Spring Boot service. The
content script observes YouTube Live Chat, the MV3 service worker calls the
local API, and the backend applies a deterministic relevance gate followed by
Groq classification. Only high-confidence academic doubts are displayed in a
dashboard.

### 2. Why is a background service worker used?

The service worker performs network calls from the extension context rather
than the YouTube page context. This separates page DOM access from backend
communication and allows the request to use extension host permissions.

### 3. Why is the API local?

The local API keeps the Groq API key off the browser and centralizes validation,
filtering policy, logging, and external-provider integration.

### 4. What is the complete data flow?

YouTube DOM → content script → runtime message → service worker → Spring
`POST /filter` → local relevance gate → Groq API when eligible → response →
dashboard decision.

### 5. Why does the project use two filtering layers?

The deterministic gate is fast, predictable, and inexpensive for obvious
social messages. Groq handles language understanding for messages that remain
eligible. This reduces cost and lowers false positives.

## Spring Boot and Java

### 6. What does `@SpringBootApplication` do?

It combines configuration, component scanning, and auto-configuration. It
boots the embedded web server and discovers the controller, service, and
configuration classes.

### 7. What does `@RestController` mean?

It marks a class as a web controller whose methods return response bodies
directly, normally serialized as JSON.

### 8. What does `@RequestMapping("/filter")` do?

It defines the base URL for the controller. Together with `@PostMapping`, the
method handles `POST /filter`.

### 9. Why is `@Valid` used?

`@Valid` activates Jakarta Bean Validation on the request DTO. It ensures the
controller does not receive blank or over-sized values.

### 10. Explain `@NotBlank` versus `@Size`.

`@NotBlank` rejects null, empty, and whitespace-only strings. `@Size` limits
the number of characters. Both are needed because a non-empty value can still
be too large.

### 11. Why are records used for DTOs?

Records provide immutable data carriers with a compact declaration, generated
accessors, equality, and a canonical constructor. They are suitable for API
request and response models.

### 12. What is the role of `@Service`?

`@Service` registers `FilterService` as a Spring-managed bean. The controller
receives it through constructor injection, while the service depends on the
`ClassifierClient` abstraction.

### 13. Why prefer constructor injection?

It makes dependencies explicit, supports immutable fields, improves testability,
and prevents a class from being created in an invalid dependency state.

### 14. What does `@RestControllerAdvice` do?

It provides centralized exception handling for REST controllers. Validation
and malformed JSON errors are converted into structured HTTP 400 responses.

### 15. Why does the service handle classifier failures?

The application is designed to fail closed. If Groq is unavailable or returns
invalid data, `FilterService` returns the existing error response and the
message is hidden instead of being presented as a genuine doubt. This keeps
provider details out of the controller.

## Browser Extension

### 16. What is Manifest V3?

Manifest V3 is Chrome's extension platform version. It uses service workers
instead of persistent background pages and requires declared permissions and
host permissions.

### 17. What is the difference between a content script and a service worker?

The content script can inspect and modify the YouTube page DOM. The service
worker runs in the extension context, handles messages, and performs the
backend request.

### 18. Why does `background.js` return `true` from `onMessage`?

The response is asynchronous because `fetch` completes later. Returning
`true` keeps the message channel open until `sendResponse` is called.

### 19. Why does the content script use `MutationObserver`?

YouTube is a dynamic single-page application. Live chat messages are inserted
after page load, so a static query is not enough.

### 20. Why is there also a periodic scan?

YouTube can replace or reconstruct parts of the chat DOM without producing a
mutation visible to the observer's current root. The fallback scan improves
resilience.

### 21. How are duplicate messages avoided?

The script uses a `WeakSet` for already-seen renderer elements and a key set
containing author plus text for duplicate content across frames or recreated
nodes.

### 22. How is the dashboard protected from XSS?

Author and message values are inserted with `textContent` and
`document.createTextNode`, not `innerHTML`. Chat content is therefore treated
as text rather than executable markup.

## API, Reliability, and Security

### 23. Why is CORS restricted?

An unrestricted wildcard CORS policy allows arbitrary web pages to call the
API. The project allows only extension and local development origins.

### 24. Why should the API key not be placed in the extension?

Extension code is distributed to the browser and can be inspected. The key
belongs in backend environment configuration and should never be exposed to
the client.

### 25. What happens when the backend is unavailable?

The service worker returns an error to the content script. The content script
fails closed and does not add the message to the dashboard.

### 26. What happens when Groq returns invalid JSON?

The classifier validates the category and confidence. Invalid data raises an
exception, and the controller returns a hidden/error result.

### 27. What would you improve for production?

- Add automated controller and classifier tests.
- Add rate limiting and authentication between the extension and API.
- Use a production HTTPS deployment instead of an unauthenticated localhost
  endpoint.
- Add structured request IDs and metrics.
- Avoid logging message content.
- Add configurable thresholds and a moderation review mode.
- Add retry policy only for safe, bounded transient failures.
- Store secrets in a secret manager.

### 28. How would you test the system?

Test DTO validation, deterministic rejection cases, accepted academic cases,
Groq malformed responses, HTTP failures, CORS behavior, service-worker
timeouts, DOM extraction, duplicate prevention, and end-to-end dashboard
updates.

### 29. What trade-off exists in the academic-signal gate?

It reduces false positives but can reject a legitimate question that uses
unusual wording or a regional language not represented in the signal list.
A production version should replace the hard-coded list with a configurable
lightweight classifier or a carefully tested multilingual rule set.

### 30. How would you explain a false positive?

Trace the message through the backend logs and inspect the API response:
first determine whether the deterministic gate allowed it, then inspect the
Groq category and confidence. The fix should target the layer that made the
incorrect decision rather than blindly lowering or raising the threshold.
