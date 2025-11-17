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

    function addMessage(role, text) {
      const msg = el('div', { class: `bs-msg ${role}` }, text);
      const msgArea = panelElement.querySelector('#bs-chat-messages');
      msgArea.insertBefore(msg, typing);
      msgArea.scrollTop = msgArea.scrollHeight;
    }

    const state = ChatState.create();

    async function sendMessage() {
      const content = (textInput.value || '').trim();
      if (!content) return;
      addMessage('user', content);
      state.addUserMessage(content);
      textInput.value = '';
      typing.style.display = 'block';
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
      } catch (e) {
        addMessage('bot', 'Open the Browse Books list to use filtering.');
      } finally {
        typing.style.display = 'none';
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

    async function updateVisibility() {
      if (!toggleButton || !panelElement) {
        return;
      }
      const fb = await findBooksFilterBar();
      const tbl = TableHelper.findBooksTable && TableHelper.findBooksTable();
      const shouldShow = !!(fb && tbl);
      toggleButton.style.display = shouldShow ? 'block' : 'none';
      if (!shouldShow) panelElement.style.display = 'none';
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
