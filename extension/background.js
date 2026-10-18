"use strict";

const API_URL = "http://127.0.0.1:8000/filter";
const MAX_AUTHOR_LENGTH = 100;
const MAX_TEXT_LENGTH = 1000;
const REQUEST_TIMEOUT_MS = 25000;

// Requests to the local API run from the extension, not YouTube's page context.
// This avoids Chrome's Private Network Access restriction on youtube.com -> 127.0.0.1.
chrome.runtime.onMessage.addListener((message, _sender, sendResponse) => {
  if (message?.type !== "jev-filter-message") {
    return;
  }

  if (!isValidMessage(message)) {
    sendResponse({ ok: false, error: "Invalid chat message." });
    return;
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  fetch(API_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ author: message.author, text: message.text }),
    signal: controller.signal,
  })
    .then(async (response) => {
      if (!response.ok) {
        let detail = `Filter request failed with ${response.status}`;
        try {
          const error = await response.json();
          if (typeof error.error === "string") {
            detail = error.error;
          }
        } catch {
          // Keep the HTTP status when the server returns a non-JSON error.
        }
        throw new Error(detail);
      }
      return response.json();
    })
    .then((data) => sendResponse({ ok: true, data }))
    .catch((error) => sendResponse({ ok: false, error: error.message }))
    .finally(() => clearTimeout(timeout));

  return true;
});

function isValidMessage(message) {
  return typeof message.author === "string"
    && typeof message.text === "string"
    && message.author.trim().length > 0
    && message.author.length <= MAX_AUTHOR_LENGTH
    && message.text.trim().length > 0
    && message.text.length <= MAX_TEXT_LENGTH;
}
