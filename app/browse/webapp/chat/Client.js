sap.ui.define([], function () {
  "use strict";

  async function callChat(message, history) {
    const payload = {
      message,
      history: JSON.stringify(history || [])
    };
    const res = await fetch("/api/browse/chat", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
    if (!res.ok) {
      throw new Error("chat failed");
    }
    const data = await res.json();
    const obj = data && (data.value || data);
    const reply = obj && obj.reply ? String(obj.reply) : "";
    let ids = [];
    if (obj && Array.isArray(obj.ids)) {
      ids = obj.ids;
    } else if (obj && Array.isArray(obj.books)) {
      ids = obj.books
        .map(function (b) { return b && b.ID; })
        .filter(function (id) { return !!id; });
    }
    const needsVectorSearch = obj && obj.needsVectorSearch === true;
    return { reply, ids, needsVectorSearch };
  }

  return {
    callChat: callChat
  };
});
