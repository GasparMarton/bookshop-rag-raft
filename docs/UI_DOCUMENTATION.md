# Chat UI Documentation

## 1. Architecture Overview

The Chat UI is implemented as a custom JavaScript widget that injects itself into the Fiori Elements application. It does not use the standard CAP/Fiori annotations but rather works as an overlay "Assistant" panel.

### Core Components

*   **`Widget.js`**: The main entry point. It creates the DOM elements for the chat panel, handles visibility logic (showing only on the Browse Books page), and manages event listeners.
*   **`State.js`**: A simple state manager that holds the conversation history (User and Assistant messages) in memory.
*   **`Client.js`**: A networking layer that interacts with the backend APIs (`/api/browse/chat` and `/api/browse/chatFt`).
*   **`TableHelper.js`**: A utility bridge that interacts with the specific UI5 controls (MDC Table and FilterBar) on the "Browse Books" page to apply filters based on chat results.

## 2. Detailed Function and Parameter Description

### `app/browse/webapp/chat/Widget.js`

This file orchestrates the entire UI lifecycle.

#### `createUI()`
Constructs the DOM elements for the chat interface using a helper `el()` function.
*   Creates the floating "Chat" toggle button.
*   Creates the Chat Panel (Header, Message Area, Input Area).
*   Sets up the **RAG/RAFT Toggle switch** to control the `useRaft` flag.

#### `sendMessage()`
Handles the user submission flow:
1.  Gets text from input.
2.  Updates UI with user message.
3.  Calls `state.addUserMessage()`.
4.  Sets UI to "Busy" state.
5.  **API Call**: Calls `ChatClient.callChat` or `ChatClient.callChatFt` based on `useRaft` state.
6.  **Response Handling**:
    *   Displays the textual `reply`.
    *   **Vector Search Action**: If `needsVectorSearch` is true, it calls `TableHelper.applyIDsToTable(ids)`. This filters the main books table to show only the relevant books found by the backend.

#### `updateVisibility()`
Dynamically checks if the user is on the "Browse Books" page (`#Books-display`) and if the underlying UI5 controls (FilterBar, Table) are ready.
*   Hides the widget on other pages.
*   Shows a "Loading..." state on the button until the Fiori elements are fully initialized.

### `app/browse/webapp/chat/Client.js`

Handles the `fetch` calls to the CAP backend.

#### `callChat(message, history)`
*   **Endpoint**: `/api/browse/chat`
*   **Payload**: `{"message": "...", "history": "..."}`
*   **Response**: Expects JSON with `value` containing `{ reply, ids, needsVectorSearch }`.

#### `callChatFT(message, history)`
*   **Endpoint**: `/api/browse/chatFt`
*   **Payload**: Same as `callChat`.
*   **Purpose**: Routes the request to the fine-tuned model path in the backend.

### `app/browse/webapp/chat/State.js`

Manages conversation history.

#### `getHistory()`
Returns the array of message objects: `[{ role: "user" | "assistant", content: "..." }]`.

#### `addUserMessage(content)` / `addAssistantMessage(content)`
Appends new messages to the history array.

### CSS Styling (`chat-widget.css`)
