# UML and Architecture Diagrams

The diagrams use Mermaid syntax. They render in GitHub, VS Code Markdown
preview, and other Mermaid-compatible viewers.

## 1. Component Diagram

```mermaid
flowchart LR
    YT[YouTube Live Chat DOM]
    CS[content.js<br/>DOM observer + dashboard]
    SW[background.js<br/>MV3 service worker]
    API[Spring Boot<br/>POST /filter]
    GATE[Local relevance gate]
    GROQ[Groq Classifier API]
    UI[Student Doubts dashboard]

    YT --> CS
    CS --> SW
    SW --> API
    API --> GATE
    GATE -->|eligible| GROQ
    GATE -->|rejected| API
    GROQ --> API
    API --> SW
    SW --> CS
    CS --> UI
```

## 2. Sequence Diagram

```mermaid
sequenceDiagram
    participant YouTube
    participant Content as content.js
    participant Worker as background.js
    participant API as FilterController
    participant Service as FilterService
    participant Gate as GroqClassifier gate
    participant Groq as Groq API
    participant Panel as Dashboard

    YouTube->>Content: Add chat renderer
    Content->>Content: Extract author and text
    Content->>Worker: jev-filter-message
    Worker->>Worker: Validate payload and timeout
    Worker->>API: POST /filter
    API->>API: Bean Validation
    API->>Service: filter(FilterRequest)
    Service->>Gate: isObviouslyNotRelevant(text)
    alt Social or non-academic
        Gate-->>Service: Reject
        Service-->>API: should_show=false
        API-->>Worker: FilterResponse
    else Eligible message
        Gate->>Groq: JSON classification request
        Groq-->>Gate: category + confidence
        Gate-->>Service: Classification
        Service-->>API: FilterResponse
        API-->>Worker: FilterResponse
    end
    Worker-->>Content: API result
    alt Approved
        Content->>Panel: Append doubt
    else Rejected
        Content->>Content: Do not display
    end
```

## 3. Backend Class Diagram

```mermaid
classDiagram
    class YouTubeCommentFilterApplication {
        +main(String[] args)
    }
    class FilterController {
        -FilterService filterService
        +filter(FilterRequest request) FilterResponse
    }
    class FilterService {
        -ClassifierClient classifierClient
        +filter(FilterRequest request) FilterResponse
    }
    class ClassifierClient {
        <<interface>>
        +isEnabled() boolean
        +isObviouslyNotRelevant(String text) boolean
        +classify(String text) ClassificationResult
    }
    class GroqClassifier {
        -ObjectMapper objectMapper
        -HttpClient httpClient
        -boolean enabled
        +isEnabled() boolean
        +isObviouslyNotRelevant(String text) boolean
        +classify(String text) ClassificationResult
    }
    class FilterRequest {
        +String author
        +String text
    }
    class FilterResponse {
        +boolean should_show
        +String category
        +Double doubt_probability
    }
    class ClassificationResult {
        +String category
        +double confidence
    }
    class WebConfig {
        +addCorsMappings(CorsRegistry registry)
    }
    class ApiExceptionHandler {
        +handleValidation(...) ResponseEntity
        +handleUnreadableMessage() ResponseEntity
    }

    YouTubeCommentFilterApplication ..> FilterController : starts Spring context
    FilterController --> FilterService : delegates
    FilterController --> FilterRequest : receives
    FilterController --> FilterResponse : returns
    FilterService --> ClassifierClient : uses
    GroqClassifier ..|> ClassifierClient
    GroqClassifier --> ClassificationResult : creates
    WebConfig ..|> WebMvcConfigurer
    ApiExceptionHandler ..> FilterController : handles errors
```

## 4. Extension Runtime Diagram

```mermaid
stateDiagram-v2
    [*] --> ExtensionLoaded
    ExtensionLoaded --> WaitingForChat: content script starts
    WaitingForChat --> MessageDetected: DOM mutation or fallback scan
    MessageDetected --> SentToWorker: author and text extracted
    SentToWorker --> BackendRequest: payload valid
    SentToWorker --> Hidden: payload invalid
    BackendRequest --> Approved: should_show=true
    BackendRequest --> Hidden: should_show=false or error
    Approved --> DashboardUpdated
    Hidden --> WaitingForChat
    DashboardUpdated --> WaitingForChat
```

## 5. Deployment View

```mermaid
flowchart TB
    Browser[Chrome browser]
    Extension[Unpacked MV3 extension]
    YouTube[YouTube HTTPS page]
    Localhost[127.0.0.1:8000]
    Spring[Spring Boot JVM]
    External[Groq HTTPS API]

    Browser --> Extension
    Extension --> YouTube
    Extension --> Localhost
    Localhost --> Spring
    Spring --> External
```
