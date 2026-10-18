package com.jev.filter.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jev.filter.exception.ClassificationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class GroqClassifier implements ClassifierClient {

    private static final String CLASSIFIER_INSTRUCTIONS = """
            You filter a teacher's YouTube live chat.
            Decide whether a message is a genuine, relevant academic doubt about the lesson,
            a school subject, homework, an exam, timings, or calculations. Understand Hindi,
            Hinglish, and English. Reject greetings, personal chat, attendance messages, vague
            requests, unrelated questions, spam, promotions, insults, and emojis.
            Return only JSON with exactly these keys:
            {"category":"subject_matter"|"exam_logistics"|"not_relevant","confidence":0.0}
            Return not_relevant for greetings, short social replies, names, locations,
            attendance, links, advertisements, emojis, insults, or messages without
            a clear academic question. Use confidence >= 0.85 only for clear relevant doubts.
            A message is not relevant if it is personal or social chat, even when it
            contains a question mark. Examples that must be rejected:
            "mujhe koi nhi krti video call", "hello bhai", "ridah yaar smjho aap",
            "welcome", "acha 😂", "me to jyada nhi bol raha", and emoji-only messages.
            Do not treat a request to call, a greeting, a name, a joke, or a conversation
            about another viewer as an academic doubt.
            """;
    private static final Set<String> VALID_CATEGORIES =
            Set.of("subject_matter", "exam_logistics", "not_relevant");
    private static final Pattern URL = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern EMOJI_OR_PUNCTUATION =
            Pattern.compile("[\\p{P}\\p{S}\\s\\d]+");
    private static final Pattern SOCIAL_CHAT = Pattern.compile(
            "(?i)\\b(?:hello|hi|hey|welcome|bye|thanks|thank you|good morning|good night|"
                    + "video call|call me|call karo|call pe|call par|baat karo|baat kar|"
                    + "where are you|where r u|kaha se|kahan se|welcome|smjho|samjho|"
                    + "jyada nhi bol|koi nhi|koi nahi|kaise ho|how are you|birthday|"
                    + "happy birthday|wait kar|wait kr|exit|love you|romantic|free ho|"
                    + "kya kar rahe|kya kr rahe)\\b");
    private static final Pattern SHORT_SOCIAL = Pattern.compile(
            "(?i)^(?:lol|lmao|haha|acha|accha|okay|ok|hlo|hlo dostoo|hi|hey|"
                    + "karugi|karunga|tu hai|s|exit)(?:\\s+[\\p{P}\\p{S}]+)*$");
    private static final Pattern ACADEMIC_SIGNAL = Pattern.compile(
            "(?i)\\b(?:what|why|how|when|which|explain|define|meaning|difference|"
                    + "answer|question|solve|calculate|formula|theorem|proof|"
                    + "subject|chapter|lesson|homework|exam|test|syllabus|"
                    + "biology|physics|chemistry|math|mathematics|science|history|"
                    + "geography|english|grammar|photosynthesis|mitochondria|"
                    + "equation|atom|cell|force|motion|समझा|समझाएं|क्यों|कैसे|क्या|"
                    + "प्रश्न|उत्तर|गणना|अध्याय)\\b");

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final boolean enabled;
    private final String apiKey;
    private final String apiUrl;
    private final String model;

    public GroqClassifier(
            ObjectMapper objectMapper,
            @Value("${jev.filter.enabled:true}") boolean enabled,
            @Value("${groq.api-key:}") String apiKey,
            @Value("${groq.api-url:https://api.groq.com/openai/v1/chat/completions}") String apiUrl,
            @Value("${groq.model:openai/gpt-oss-20b}") String model) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.apiKey = apiKey.trim();
        this.apiUrl = apiUrl;
        this.model = model;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isObviouslyNotRelevant(String text) {
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return URL.matcher(normalized).find()
                || normalized.length() < 3
                || EMOJI_OR_PUNCTUATION.matcher(normalized).matches()
                || SOCIAL_CHAT.matcher(normalized).find()
                || SHORT_SOCIAL.matcher(normalized).matches()
                || !ACADEMIC_SIGNAL.matcher(normalized).find();
    }

    @Override
    public ClassificationResult classify(String text) throws ClassificationException {
        if (apiKey.isEmpty()) {
            throw new ClassificationException("GROQ_API_KEY is not set");
        }

        try {
            String payload = objectMapper.writeValueAsString(new GroqRequest(
                    model,
                    0,
                    new ResponseFormat("json_object"),
                    new Message[]{
                            new Message("system", CLASSIFIER_INSTRUCTIONS),
                            new Message("user", text)
                    }));
            HttpRequest request = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IOException("Groq returned HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            String content = root.path("choices").path(0).path("message").path("content").asText(null);
            if (content == null) {
                throw new IOException("Groq response did not contain message content");
            }
            JsonNode answer = objectMapper.readTree(content);
            String category = answer.path("category").asText(null);
            double confidence = answer.path("confidence").asDouble(Double.NaN);
            if (!VALID_CATEGORIES.contains(category)) {
                throw new IOException("Groq returned an invalid category");
            }
            if (Double.isNaN(confidence) || confidence < 0 || confidence > 1) {
                throw new IOException("Groq returned an invalid confidence");
            }
            return new ClassificationResult(category, confidence);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ClassificationException("Groq classification was interrupted", exception);
        } catch (IOException | RuntimeException exception) {
            throw new ClassificationException("Groq classification failed", exception);
        }
    }

    private record GroqRequest(String model, int temperature, ResponseFormat response_format,
                               Message[] messages) {
    }

    private record ResponseFormat(String type) {
    }

    private record Message(String role, String content) {
    }
}
