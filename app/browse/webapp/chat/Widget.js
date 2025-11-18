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
        const { reply, ids } = await ChatClient.callChat(content, state.getHistory());
        if (reply) {
          addMessage('bot', reply);
          state.addAssistantMessage(reply);
        }
        if (ids && ids.length) {
          const ok = await TableHelper.applyIDsToTable(ids);
          if (!ok) throw new Error('Table not ready');
        }
      } catch (err) {
        // Surface a friendly error to the chat pane and keep console details for debugging.
        console.error('Chat request failed', err);
        addMessage('bot', 'Sorry, the assistant is unavailable right now. Please try again.');
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
