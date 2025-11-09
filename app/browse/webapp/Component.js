sap.ui.define([
  "sap/fe/core/AppComponent",
  "sap/ui/dom/includeStylesheet",
  "bookshop/chat/Widget"
], function (AppComponent, includeStylesheet, Widget) {
  "use strict";
  return AppComponent.extend("bookshop.Component", {
    metadata: { manifest: "json" },
    init: function () {
      AppComponent.prototype.init.apply(this, arguments);
      try {
        includeStylesheet(sap.ui.require.toUrl("bookshop/chat-widget.css"));
      } catch (e) { /* ignore */ }
      try {
        if (Widget && Widget.init) { Widget.init(); }
      } catch (e) { /* ignore */ }
    }
  });
});
