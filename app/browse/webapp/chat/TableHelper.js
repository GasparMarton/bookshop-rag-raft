sap.ui.define([
  "sap/ui/core/Element",
  "sap/ui/model/Filter",
  "sap/ui/model/FilterOperator"
], function (Element, Filter, FilterOperator) {
  "use strict";

  function findBooksTable() {
    try {
      return Element.registry.get("bookshop::BooksList--fe::table::Books::LineItem::Table");
    } catch (e) {
      return null;
    }
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
    if (!ids) {
      // null or undefined -> clear filters (show all)
      binding.filter([]);
      return true;
    }
    if (ids.length === 0) {
      // empty array -> show nothing (filter by impossible ID)
      binding.filter(new Filter("ID", FilterOperator.EQ, "___NO_MATCH___"));
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
