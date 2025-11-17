sap.ui.define([
  "sap/ui/core/Element",
  "sap/ui/model/Filter",
  "sap/ui/model/FilterOperator"
], function (Element, Filter, FilterOperator) {
  "use strict";

  function findBooksTable() {
    let found = null;
    try {
      Element.registry.forEach(function (ctrl) {
        if (!ctrl || !ctrl.getMetadata) {
          return;
        }
        const name = ctrl.getMetadata().getName();
        if (name === "sap.ui.mdc.Table") {
          const id = ctrl.getId();
          if (id.includes("BooksList") && id.includes("Table")) {
            found = ctrl;
          }
        }
      });
    } catch (e) {
      // ignore
    }
    return found;
  }

  async function applyIDsToTable(ids) {
    const table = findBooksTable();
    if (!table || !table.getRowBinding) {
      return false;
    }
    const binding = table.getRowBinding();
    if (!binding) {
      return false;
    }
    if (!ids || ids.length === 0) {
      binding.filter([]);
      return true;
    }
    const filters = ids.map(function (id) {
      return new Filter("ID", FilterOperator.EQ, id);
    });
    const orFilter = new Filter({ filters: filters, and: false });
    binding.filter(orFilter);
    return true;
  }

  return {
    applyIDsToTable: applyIDsToTable,
    findBooksTable: findBooksTable
  };
});
