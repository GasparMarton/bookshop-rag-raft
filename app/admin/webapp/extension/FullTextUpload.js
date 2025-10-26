sap.ui.define([
    "sap/m/MessageBox",
    "sap/m/MessageToast"
], function (MessageBox, MessageToast) {
    "use strict";

    /**
     * Creates the fragment controller for the Full Text upload dialog.
     * Responsible for reading a local .txt file and updating the bound Book's fullText property.
     * @param {sap.fe.core.ExtensionAPI} oExtensionAPI FE extension API instance.
     * @returns {object} Fragment controller implementation.
     */
    function _createDialogController(oExtensionAPI) {
        var oDialog; // sap.m.Dialog instance
        var oFile;   // Last selected File handle

        function _setOkEnabled(bEnabled) {
            if (oDialog) {
                var oBegin = oDialog.getBeginButton();
                oBegin && oBegin.setEnabled(bEnabled);
            }
        }

        function _setBusy(bBusy) {
            if (oDialog) {
                oDialog.setBusy(bBusy);
            }
        }

        function _close() {
            if (oDialog) {
                oDialog.close();
            }
        }

        function _showError(sMessage) {
            MessageBox.error(sMessage || "Upload failed");
        }

        function _byId(sLocalId) {
            // Fragment ID defined when loading fragment: fullTextUploadDialog
            return sap.ui.core.Fragment.byId("fullTextUploadDialog", sLocalId);
        }

        function _reset() {
            oFile = undefined;
            var oUploader = _byId("fullTextUploader");
            if (oUploader) {
                oUploader.clear();
            }
            _setOkEnabled(false);
            _setBusy(false);
        }

        function _readFileContents(oFile) {
            return new Promise(function (resolve, reject) {
                if (!oFile) {
                    return reject(new Error("No file selected"));
                }
                var reader = new FileReader();
                reader.onload = function (e) { resolve(e.target.result); };
                reader.onerror = function () { reject(new Error("Failed to read file")); };
                reader.readAsText(oFile, "UTF-8");
            });
        }

        function _updateFullTextOnContext(sText) {
            // Retrieve the binding context of the Object Page header (Book entity)
            var oBindingContext = oExtensionAPI.getBindingContext();
            if (!oBindingContext) {
                throw new Error("No binding context available to update fullText");
            }
            // Directly set property in context
            oBindingContext.setProperty("fullText", sText);
        }

        return {
            onBeforeOpen: function (oEvent) {
                oDialog = oEvent.getSource();
                oExtensionAPI.addDependent(oDialog);
                _reset();
            },
            onAfterOpen: function () {},
            onAfterClose: function () {
                oExtensionAPI.removeDependent(oDialog);
                oDialog.destroy();
                oDialog = undefined;
            },
            onFileChange: function (oEvent) {
                var aFiles = oEvent.getParameter("files");
                oFile = aFiles && aFiles[0];
                if (oFile) {
                    if (oFile.type && oFile.type !== "text/plain") {
                        _showError("Unsupported MIME type: " + oFile.type);
                        _reset();
                        return;
                    }
                    // Basic size guard (e.g., 2MB) to prevent accidental huge uploads
                    if (oFile.size > 2 * 1024 * 1024) { // 2MB
                        _showError("File too large (max 2MB)");
                        _reset();
                        return;
                    }
                    _setOkEnabled(true);
                } else {
                    _setOkEnabled(false);
                }
            },
            onOk: function () {
                _setBusy(true);
                _readFileContents(oFile)
                    .then(function (sText) {
                        // Optional normalization: trim and replace Windows line endings
                        var sNormalized = sText.replace(/\r\n/g, "\n");
                        _updateFullTextOnContext(sNormalized);
                        MessageToast.show("Full text loaded");
                        // Submit changes (group 'default' is auto submit; force refresh)
                        oExtensionAPI.refresh();
                        _close();
                    })
                    .catch(function (err) {
                        _showError(err && err.message);
                    })
                    .finally(function () { _setBusy(false); });
            },
            onCancel: function () { _close(); }
        };
    }

    return {
        /**
         * Entry point called by manifest action to show dialog.
         * @param {sap.ui.model.Context} oBindingContext Current context (Book) provided by FE.
         */
        showUploadDialog: function (oBindingContext) {
            this.loadFragment({
                id: "fullTextUploadDialog",
                name: "admin.extension.FullTextUpload", // XML fragment file name w/o suffix
                controller: _createDialogController(this)
            }).then(function (oDialog) { oDialog.open(); });
        }
    };
});
