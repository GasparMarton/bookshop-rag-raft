sap.ui.define([], function () {
  "use strict";

  return {
    create: function () {
      const history = [];
      return {
        getHistory: function () {
          return history;
        },
        addUserMessage: function (content) {
          history.push({ role: "user", content: String(content || "") });
        },
        addAssistantMessage: function (content) {
          history.push({ role: "assistant", content: String(content || "") });
        },
        removeLastMessage: function () {
          history.pop();
        }
      };
    }
  };
});

