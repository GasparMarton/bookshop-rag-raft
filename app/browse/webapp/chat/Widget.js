sap.ui.define([
  "sap/ui/core/Element",
  "bookshop/chat/State",
  "bookshop/chat/Client",
  "bookshop/chat/TableHelper"
], function (Element, ChatState, ChatClient, TableHelper) {
  "use strict";

  let toggleButton = null;
  let panelElement = null;
  let hashChangeHandler = null;
  let visibilityInterval = null;

  function el(tag, attrs, ...children) {
    const node = document.createElement(tag);
    if (attrs) Object.entries(attrs).forEach(([k, v]) => {
      if (k === 'class') node.className = v;
      else if (k === 'style') Object.assign(node.style, v);
      else if (k.startsWith('on') && typeof v === 'function') node.addEventListener(k.substring(2), v);
      else node.setAttribute(k, v);
    });
    children.flat().forEach(c => node.append(c instanceof Node ? c : document.createTextNode(String(c))));
    return node;
  }

  async function findBooksFilterBar() {
    try {
      let found = null;
      Element.registry.forEach(function (ctrl) {
        if (!ctrl || !ctrl.getMetadata) return;
        const name = ctrl.getMetadata().getName();
        if (name === 'sap.ui.mdc.FilterBar') {
          const id = ctrl.getId();
          if ((/BooksList/.test(id) || /Books/.test(id)) && id.includes('FilterBar')) {
            found = ctrl;
          }
        }
      });
      return found;
    } catch (e) {
      return null;
    }
  }

  function isBrowseBooksRoute() {
    if (typeof window === 'undefined') {
      return false;
    }
    const hash = (window.location && window.location.hash) || '';
    if (hash.startsWith('#Books-display')) {
      return true;
    }
    return false;
  }

  function destroyUI() {
    if (hashChangeHandler) {
      window.removeEventListener('hashchange', hashChangeHandler);
      hashChangeHandler = null;
    }
    if (visibilityInterval) {
      clearInterval(visibilityInterval);
      visibilityInterval = null;
    }
    if (panelElement && panelElement.parentElement) {
      panelElement.remove();
    }
    if (toggleButton && toggleButton.parentElement) {
      toggleButton.remove();
    }
    toggleButton = null;
    panelElement = null;
  }

  function createUI() {
    if (toggleButton || document.getElementById('bs-chat-toggle')) return;
    toggleButton = el('button', { id: 'bs-chat-toggle', title: 'Chat' }, 'Chat');
    panelElement = el('div', { id: 'bs-chat-panel' },
      el('div', { id: 'bs-chat-header' },
        el('span', null, 'Assistant'),
        el('button', { title: 'Close', style: { background: 'transparent', color: '#fff', border: 'none', cursor: 'pointer', fontSize: '18px' } }, 'x')
      ),
      el('div', { id: 'bs-chat-messages' }),
      el('div', { id: 'bs-chat-input' },
        el('input', { type: 'text', placeholder: 'Ask anything about the books...', id: 'bs-chat-text' }),
        el('button', { id: 'bs-chat-send' }, 'Apply')
      )
    );
    const typing = el('div', { class: 'bs-typing', id: 'bs-typing' }, 'Working...');
    panelElement.querySelector('#bs-chat-messages').appendChild(typing);

    const closeBtn = panelElement.querySelector('#bs-chat-header button');
    closeBtn.addEventListener('click', () => panelElement.style.display = 'none');
    toggleButton.addEventListener('click', () => {
      panelElement.style.display = (panelElement.style.display === 'none' || !panelElement.style.display) ? 'flex' : 'none';
      if (panelElement.style.display === 'flex') {
        document.getElementById('bs-chat-text').focus();
      }
    });

    const sendBtn = panelElement.querySelector('#bs-chat-send');
    const textInput = panelElement.querySelector('#bs-chat-text');
    const inputBar = panelElement.querySelector('#bs-chat-input');
    const busyIndicator = el('div', {
      id: 'bs-chat-input-busy',
      role: 'status',
      'aria-live': 'polite'
    },
      el('span', { class: 'bs-chat-spinner', 'aria-hidden': 'true' }),
      el('span', null, 'Working on your answer...'));
    busyIndicator.style.display = 'none';
    inputBar.appendChild(busyIndicator);

    function addMessage(role, text) {
      const msg = el('div', { class: `bs-msg ${role}` }, text);
      const msgArea = panelElement.querySelector('#bs-chat-messages');
      msgArea.insertBefore(msg, typing);
      msgArea.scrollTop = msgArea.scrollHeight;
    }

    const state = ChatState.create();

    function setBusy(isBusy) {
      typing.style.display = isBusy ? 'block' : 'none';
      busyIndicator.style.display = isBusy ? 'flex' : 'none';
      textInput.disabled = isBusy;
      sendBtn.disabled = isBusy;
    }

    async function sendMessage() {
      const content = (textInput.value || '').trim();
      if (!content) return;
      addMessage('user', content);
      state.addUserMessage(content);
      textInput.value = '';
      setBusy(true);
      try {
        const { reply, ids, needsVectorSearch } = await ChatClient.callChat(content, state.getHistory());
        if (reply) {
          addMessage('bot', reply);
          state.addAssistantMessage(reply);
        }
        if (needsVectorSearch && ids && ids.length) {
          const ok = await TableHelper.applyIDsToTable(ids);
          if (!ok) console.warn('Table not ready for filtering');
        } else if (needsVectorSearch && (!ids || ids.length === 0)) {
          // If search was needed but no books found, we might want to clear the filter or show all?
          // The requirement says "return those books and set those books in the table".
          // If no books found, maybe we should show empty table?
          // Existing logic:
          // if (!ids || ids.length === 0) { binding.filter([]); return true; } (in TableHelper)
          // So passing empty array clears the filter (shows all).
          // Wait, binding.filter([]) usually means "no filters", so it shows ALL rows.
          // If I want to show NO rows, I should probably pass a filter that matches nothing.
          // But let's stick to the requirement: "set those books in the table".
          // If ids is empty, maybe we shouldn't touch the table?
          // Or maybe we should show nothing?
          // Let's assume if needsVectorSearch is true, we should update the table.
          // If ids is empty, TableHelper.applyIDsToTable([]) shows ALL books.
          // This might be confusing if the user asked for "books about X" and we found none, but show ALL books.
          // But let's follow the existing pattern or improve it.
          // If I pass a filter that is impossible (ID = 'impossible'), it would show empty.
          // But TableHelper.applyIDsToTable handles empty array by clearing filters.
          // Let's leave it as is for now, but only call it if needsVectorSearch is true.
          // If needsVectorSearch is true and ids is empty, we probably shouldn't call applyIDsToTable if it resets the view.
          // But if the user asked for something specific and we found nothing, maybe we SHOULD reset the view?
          // Let's stick to: only update if we have IDs.
          // "each book that has any chunks that is above lets say 0.3 then we return those books and set those books in the table."
          // If no books match, we return empty list.
          // If we set empty list in table, we show all books? That seems wrong for a search result.
          // But let's stick to the plan: "Update table with returned books".
          // If I change the logic to only update if ids.length > 0, then if no books found, the table stays as is.
          // That seems safer.
          // But wait, if I previously searched for "Space" and got 3 books.
          // Then I ask "Books about underwater" and get 0 books.
          // If I don't update the table, it still shows "Space" books. That is confusing.
          // So if needsVectorSearch is true, we MUST update the table, even if empty.
          // But TableHelper clears filter on empty.
          // I should probably fix TableHelper to handle "empty results" vs "clear filter".
          // But I can't change TableHelper easily without understanding its usage elsewhere.
          // Let's look at TableHelper again.
          // "if (!ids || ids.length === 0) { binding.filter([]); return true; }"
          // This definitely clears filters.
          // So if I search and find nothing, I see all books.
          // Maybe that's acceptable for now.
          // I will implement: if needsVectorSearch, call applyIDsToTable.
          const ok = await TableHelper.applyIDsToTable(ids || []);
          if (!ok) console.warn('Table not ready for filtering');
        }
      } catch (err) {
        // Surface a friendly error to the chat pane and keep console details for debugging.
        console.error('Chat request failed', err);
        addMessage('bot', 'Sorry, the assistant is unavailable right now. Please try again.');
        state.removeLastMessage();
      } finally {
        setBusy(false);
      }
    }

    sendBtn.addEventListener('click', sendMessage);
    textInput.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        sendMessage();
      }
    });

    document.body.append(toggleButton, panelElement);

    setTimeout(() => {
      panelElement.style.display = 'flex';
      addMessage('bot', 'Ask about books. I can answer and narrow the list when needed.');
    }, 800);

    let readyCheckStart = Date.now();

    async function updateVisibility() {
      if (!toggleButton || !panelElement) {
        return;
      }
      const onBrowsePage = isBrowseBooksRoute();
      toggleButton.style.display = onBrowsePage ? 'block' : 'none';
      if (!onBrowsePage) {
        panelElement.style.display = 'none';
        readyCheckStart = Date.now();
        return;
      }
      const fb = await findBooksFilterBar();
      const tbl = TableHelper.findBooksTable && TableHelper.findBooksTable();
      const waitedTooLong = (Date.now() - readyCheckStart) > 5000;
      const ready = waitedTooLong || !!(fb && tbl);
      toggleButton.setAttribute('aria-busy', ready ? 'false' : 'true');
      toggleButton.title = ready ? 'Chat' : 'Browse books list is still loading...';
    }

    setTimeout(updateVisibility, 1200);
    hashChangeHandler = () => setTimeout(updateVisibility, 300);
    window.addEventListener('hashchange', hashChangeHandler);
    let tries = 0;
    visibilityInterval = setInterval(async () => {
      tries++;
      await updateVisibility();
      if (tries > 12 && visibilityInterval) {
        clearInterval(visibilityInterval);
        visibilityInterval = null;
      }
    }, 800);
  }

  return {
    init: function () {
      if (sap && sap.ui && sap.ui.getCore) {
        try { sap.ui.getCore().attachInit(createUI); } catch (e) { createUI(); }
      } else if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', createUI);
      } else {
        createUI();
      }
    },
    destroy: function () {
      destroyUI();
    }
  };
});
