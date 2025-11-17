sap.ui.define(
    ["sap/m/MessageBox", "sap/m/MessageToast"],
    function (MessageBox, MessageToast) {
        "use strict";

        return {
            rebuildEmbeddings: function () {
                var oController = this;

                MessageBox.confirm("This will rebuild embeddings for all books. Continue?", {
                    title: "Rebuild Embeddings",
                    actions: [MessageBox.Action.OK, MessageBox.Action.CANCEL],
                    onClose: function (sAction) {
                        if (sAction !== MessageBox.Action.OK) {
                            return;
                        }
                            oController.editFlow.invokeAction("AdminService.rebuildEmbeddings", {
                                model: oController.editFlow.getView().getModel()
                            })
                            .then(() => {
                                this.refresh();
                            })
                            .catch(function (error) {
                            var sMessage = error && error.message ? error.message : "Failed to rebuild embeddings.";
                            MessageBox.error(sMessage);
                        });
                    }
                });
            }
        };
    }
);
