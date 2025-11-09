sap.ui.define([
  "sap/ui/core/Element",
  "sap/ui/model/Filter",
  "sap/ui/model/FilterOperator"
], function (Element, Filter, FilterOperator) {
  "use strict";

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

  function findBooksTable() {
    let found = null;
    try {
      Element.registry.forEach(function (ctrl) {
        if (!ctrl || !ctrl.getMetadata) return;
        const name = ctrl.getMetadata().getName();
        if (name === 'sap.ui.mdc.Table') {
          const id = ctrl.getId();
          if (id.includes('BooksList') && id.includes('Table')) found = ctrl;
        }
      });
    } catch (e) {
      // ignore
    }
    return found;
  }

  async function callChat(message, history) {
    try {
      const res = await fetch('/api/browse/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message, history: JSON.stringify(history || []) })
      });
      if (!res.ok) throw new Error('chat failed');
      const data = await res.json();
      const obj = data && (data.value || data);
      const reply = obj && obj.reply ? String(obj.reply) : '';
      const ids = obj && Array.isArray(obj.ids) ? obj.ids : [];
      return { reply, ids };
    } catch (e) {
      return { reply: 'Sorry, I had trouble answering that.', ids: [] };
    }
  }

  async function applyIDsToTable(ids) {
    const table = findBooksTable();
    if (!table || !table.getRowBinding) return false;
    const binding = table.getRowBinding();
    if (!binding) return false;
    if (!ids || ids.length === 0) {
      binding.filter([]);
      return true;
    }
    const filters = ids.map(id => new Filter('ID', FilterOperator.EQ, id));
    const orFilter = new Filter({ filters, and: false });
    binding.filter(orFilter);
    return true;
  }

  function createUI() {
    if (document.getElementById('bs-chat-toggle')) return;
    const toggle = el('button', { id: 'bs-chat-toggle', title: 'Chat' }, 'Chat');
    const panel = el('div', { id: 'bs-chat-panel' },
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
    panel.querySelector('#bs-chat-messages').appendChild(typing);

    const closeBtn = panel.querySelector('#bs-chat-header button');
    closeBtn.addEventListener('click', () => panel.style.display = 'none');
    toggle.addEventListener('click', () => {
      panel.style.display = (panel.style.display === 'none' || !panel.style.display) ? 'flex' : 'none';
      if (panel.style.display === 'flex') {
        document.getElementById('bs-chat-text').focus();
      }
    });

    const sendBtn = panel.querySelector('#bs-chat-send');
    const textInput = panel.querySelector('#bs-chat-text');

    function addMessage(role, text) {
      const msg = el('div', { class: `bs-msg ${role}` }, text);
      panel.querySelector('#bs-chat-messages').insertBefore(msg, typing);
      panel.querySelector('#bs-chat-messages').scrollTop = panel.querySelector('#bs-chat-messages').scrollHeight;
    }

    const history = [];

    async function sendMessage() {
      const content = (textInput.value || '').trim();
      if (!content) return;
      addMessage('user', content);
      history.push({ role: 'user', content });
      textInput.value = '';
      typing.style.display = 'block';
      try {
        const { reply, ids } = await callChat(content, history);
        if (reply) {
          addMessage('bot', reply);
          history.push({ role: 'assistant', content: reply });
        }
        if (ids && ids.length) {
          const ok = await applyIDsToTable(ids);
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

    document.body.append(toggle, panel);

    setTimeout(() => {
      panel.style.display = 'flex';
      addMessage('bot', 'Ask about books. I can answer and narrow the list when needed.');
    }, 800);

    async function updateVisibility() {
      const fb = await findBooksFilterBar();
      const tbl = findBooksTable();
      const shouldShow = !!(fb && tbl);
      toggle.style.display = shouldShow ? 'block' : 'none';
      if (!shouldShow) panel.style.display = 'none';
    }

    setTimeout(updateVisibility, 1200);
    window.addEventListener('hashchange', () => setTimeout(updateVisibility, 300));
    let tries = 0;
    const iv = setInterval(async () => {
      tries++;
      await updateVisibility();
      if (tries > 12) clearInterval(iv);
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
    }
  };
});
