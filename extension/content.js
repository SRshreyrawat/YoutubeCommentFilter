(() => {
  "use strict";

  const BOX_ID = "teacher-doubt-box";
  const LIST_ID = "teacher-doubt-list";
  const MESSAGE_EVENT = "jev-live-chat-message";
  const isTopFrame = window.top === window;
  const seenMessages = new WeakSet();
  const seenMessageKeys = new Set();

  function createDashboard() {
    const existing = document.getElementById(BOX_ID);
    if (existing) {
      return existing.querySelector(`#${LIST_ID}`);
    }

    const box = document.createElement("section");
    box.id = BOX_ID;
    box.setAttribute("aria-label", "Student doubts detected by Jev AI");

    const header = document.createElement("header");
    header.id = "teacher-doubt-header";
    const title = document.createElement("span");
    title.id = "teacher-doubt-title";
    title.textContent = "\u{1F3AF} Student Doubts (Jev AI)";
    const controls = document.createElement("div");
    controls.className = "teacher-doubt-controls";
    const themeButton = createControl("\u263E", "Switch to dark mode");
    const expandButton = createControl("\u2922", "Expand panel");
    const collapseButton = createControl("\u2212", "Collapse panel");
    controls.append(themeButton, expandButton, collapseButton);
    header.replaceChildren(title, controls);

    const list = document.createElement("div");
    list.id = LIST_ID;
    list.setAttribute("role", "log");
    list.setAttribute("aria-live", "polite");

    box.append(header, list);
    document.body.appendChild(box);
    restorePanelPreferences(box, themeButton);
    setupPanelControls(box, header, themeButton, expandButton, collapseButton);
    return list;
  }

  function createControl(icon, label) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "teacher-doubt-button";
    button.textContent = icon;
    button.title = label;
    button.setAttribute("aria-label", label);
    return button;
  }

  function savePanelPreferences(box) {
    localStorage.setItem("jev-panel-theme", box.classList.contains("is-dark") ? "dark" : "light");
    localStorage.setItem("jev-panel-position", JSON.stringify({ left: box.style.left, top: box.style.top }));
  }

  function restorePanelPreferences(box, themeButton) {
    if (localStorage.getItem("jev-panel-theme") === "dark") {
      box.classList.add("is-dark");
      themeButton.textContent = "\u2600";
      themeButton.title = "Switch to light mode";
      themeButton.setAttribute("aria-label", themeButton.title);
    }
    try {
      const position = JSON.parse(localStorage.getItem("jev-panel-position"));
      if (position?.left && position?.top) {
        box.style.left = position.left;
        box.style.top = position.top;
        box.style.bottom = "auto";
      }
    } catch {
      console.warn("[Jev Live Chat] Ignoring an invalid saved panel position.");
    }
  }

  function setupPanelControls(box, header, themeButton, expandButton, collapseButton) {
    themeButton.addEventListener("click", () => {
      const dark = box.classList.toggle("is-dark");
      themeButton.textContent = dark ? "\u2600" : "\u263E";
      themeButton.title = dark ? "Switch to light mode" : "Switch to dark mode";
      themeButton.setAttribute("aria-label", themeButton.title);
      savePanelPreferences(box);
    });
    expandButton.addEventListener("click", () => {
      const expanded = box.classList.toggle("is-expanded");
      expandButton.textContent = expanded ? "\u2921" : "\u2922";
      expandButton.title = expanded ? "Restore panel size" : "Expand panel";
      expandButton.setAttribute("aria-label", expandButton.title);
    });
    collapseButton.addEventListener("click", () => {
      const collapsed = box.classList.toggle("is-collapsed");
      collapseButton.textContent = collapsed ? "+" : "\u2212";
      collapseButton.title = collapsed ? "Show messages" : "Collapse panel";
      collapseButton.setAttribute("aria-label", collapseButton.title);
    });

    let dragOffsetX = 0;
    let dragOffsetY = 0;
    header.addEventListener("pointerdown", (event) => {
      if (event.target.closest("button")) return;
      const bounds = box.getBoundingClientRect();
      dragOffsetX = event.clientX - bounds.left;
      dragOffsetY = event.clientY - bounds.top;
      header.classList.add("is-dragging");
      header.setPointerCapture(event.pointerId);
    });
    header.addEventListener("pointermove", (event) => {
      if (!header.hasPointerCapture(event.pointerId)) return;
      const left = Math.max(8, Math.min(event.clientX - dragOffsetX, window.innerWidth - box.offsetWidth - 8));
      const top = Math.max(8, Math.min(event.clientY - dragOffsetY, window.innerHeight - box.offsetHeight - 8));
      box.style.left = `${left}px`;
      box.style.top = `${top}px`;
      box.style.bottom = "auto";
    });
    header.addEventListener("pointerup", (event) => {
      if (!header.hasPointerCapture(event.pointerId)) return;
      header.releasePointerCapture(event.pointerId);
      header.classList.remove("is-dragging");
      savePanelPreferences(box);
    });
  }

  let doubtList = null;

  function appendDoubt(authorName, chatText) {
    if (!doubtList) {
      doubtList = createDashboard();
    }

    const item = document.createElement("article");
    item.className = "doubt-item";

    const author = document.createElement("span");
    author.className = "doubt-author";
    author.textContent = `${authorName}: `;
    item.append(author, document.createTextNode(chatText));

    doubtList.appendChild(item);
    doubtList.scrollTo({ top: doubtList.scrollHeight, behavior: "smooth" });
    console.info("[Jev Live Chat] Added message to Student Doubts panel", {
      author: authorName,
      text: chatText,
    });
  }

  async function sendToJevFilter(authorName, chatText) {
    console.info("[Jev Live Chat] Detected live-chat message", {
      author: authorName,
      text: chatText,
    });
    try {
      const result = await chrome.runtime.sendMessage({
        type: "jev-filter-message",
        author: authorName,
        text: chatText,
      });
      if (!result?.ok) {
        throw new Error(result?.error || "The extension could not contact the local API.");
      }

      const data = result.data;
      console.info("[Jev Live Chat] Local filter response", data);
      if (data.should_show === true) {
        appendDoubt(authorName, chatText);
      } else {
        console.info("[Jev Live Chat] Message not added by the filter", {
          author: authorName,
          text: chatText,
        });
      }
    } catch (error) {
      // Fail closed: display a message only after the local classifier approves it.
      console.warn("Teacher's Live Chat Filter could not reach the local API; message was hidden.", error);
    }
  }

  function processMessage(renderer) {
    if (seenMessages.has(renderer)) {
      return;
    }

    const author = readElementText(renderer, [
      "#author-name",
      "#author-name a",
      "[id='author-name']",
    ]);
    const text = readElementText(renderer, [
      "#message",
      "#message span",
      "[id='message']",
    ]);
    if (!author || !text) {
      return;
    }

    seenMessages.add(renderer);
    const messageKey = `${author}\u0000${text}`;
    if (seenMessageKeys.has(messageKey)) {
      return;
    }
    seenMessageKeys.add(messageKey);
    if (isTopFrame) {
      void sendToJevFilter(author, text);
    } else {
      window.top.postMessage({ type: MESSAGE_EVENT, author, text }, "*");
    }
  }

  function scanForMessages(root = document) {
    root.querySelectorAll(
      "yt-live-chat-text-message-renderer, yt-live-chat-paid-message-renderer"
    ).forEach(processMessage);
  }

  function readElementText(root, selectors) {
    for (const selector of selectors) {
      const element = root.querySelector(selector);
      const text = element?.textContent?.trim();
      if (text) {
        return text;
      }
    }
    return "";
  }

  function observeLiveChat(root) {
    if (isTopFrame) doubtList = createDashboard();
    console.info("[Jev Live Chat] Observing the page for live-chat messages.");
    scanForMessages(root);

    const observer = new MutationObserver((records) => {
      for (const record of records) {
        for (const node of record.addedNodes) {
          if (!(node instanceof Element)) {
            continue;
          }
          if (node.matches(
            "yt-live-chat-text-message-renderer, yt-live-chat-paid-message-renderer"
          )) {
            processMessage(node);
          }
          scanForMessages(node);
        }
      }
    });

    observer.observe(root, { childList: true, subtree: true });
    window.setInterval(() => scanForMessages(root), 2000);
  }

  if (isTopFrame) {
    doubtList = createDashboard();
    window.addEventListener("message", (event) => {
      const data = event.data;
      if (!["https://www.youtube.com", "https://youtube.com"].includes(event.origin)
        || data?.type !== MESSAGE_EVENT
        || typeof data.author !== "string" || typeof data.text !== "string") return;
      console.info("[Jev Live Chat] Received message from YouTube chat frame");
      void sendToJevFilter(data.author, data.text);
    });
  }

  const targetTimer = window.setInterval(() => {
    if (!document.body) {
      return;
    }
    window.clearInterval(targetTimer);
    observeLiveChat(document.body);
  }, 500);

  console.info("[Jev Live Chat] Extension loaded; waiting for YouTube Live Chat.");
})();
