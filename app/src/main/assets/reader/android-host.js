// Connects the desktop reader page (reader.js, unchanged) to the Android app.
//
// reader.js posts to its host through window.webkit.messageHandlers.leanmark,
// as on the desktop WebKit hosts, and receives through LeanMarkHost.receive.
// Here both directions travel over a MessagePort that the app hands to the
// page once it has loaded. Messages posted before that wait in a queue.
(function () {
  "use strict";

  var port = null;
  var queue = [];

  // reader.js uses Element.replaceChildren, which WebView has from version 86.
  // Older WebView builds (69 on the oldest Android 8 images) get this
  // equivalent, so the page works there too.
  if (!Element.prototype.replaceChildren) {
    Element.prototype.replaceChildren = function () {
      while (this.firstChild) {
        this.removeChild(this.firstChild);
      }
      this.append.apply(this, arguments);
    };
  }

  function send(message) {
    var text = String(message);
    if (port) {
      port.postMessage(text);
    } else {
      queue.push(text);
    }
  }

  window.webkit = Object.freeze({
    messageHandlers: Object.freeze({
      leanmark: Object.freeze({ postMessage: send })
    })
  });

  function pressKey(key) {
    document.dispatchEvent(new KeyboardEvent("keydown", {
      key: key,
      bubbles: true,
      cancelable: true
    }));
  }

  // The back gesture closes an open figure viewer, the way Escape does, or
  // else the settings sheet.
  function goBack() {
    var viewer = document.getElementById("figureViewer");
    var close = document.getElementById("settingsClose");
    if (viewer && !viewer.hidden) {
      pressKey("Escape");
    } else if (close && !document.getElementById("settingsPanel").hidden) {
      close.click();
    }
  }

  // Timings for the budgets job (scripts/measure.sh): how long the page takes
  // for its own work, from the user's key or tap, or from the app's message,
  // until the result is on screen. Only the durations leave the page; the
  // app logs them under LeanMarkTiming.
  var lastInput = -Infinity;
  var findStart = 0;

  ["keydown", "click", "change", "input"].forEach(function (type) {
    document.addEventListener(type, function (event) {
      lastInput = performance.now();
      if (type === "input" && event.target && event.target.id === "findInput") {
        findStart = lastInput;
      }
    }, true);
  });

  // Work started by the user less than a second ago, else nothing.
  function inputStart() {
    return performance.now() - lastInput < 1000 ? lastInput : 0;
  }

  // Reports once diagrams are drawn and two more frames have been produced.
  function timeUntilDrawn(name, start) {
    if (!start) {
      return;
    }
    function check() {
      if (document.documentElement.dataset.renderState === "diagrams") {
        requestAnimationFrame(check);
        return;
      }
      requestAnimationFrame(function () {
        requestAnimationFrame(function () {
          send("timing|" + name + "|" + Math.round(performance.now() - start));
        });
      });
    }
    requestAnimationFrame(check);
  }

  function receive(event) {
    var data;
    try {
      data = JSON.parse(event.data);
    } catch (_error) {
      return;
    }
    if (data && data.type === "android-back") {
      goBack();
    } else if (data && data.type === "android-viewer-zoom") {
      // A pinch while a figure is expanded zooms the figure, as + and - do.
      pressKey(data.direction > 0 ? "+" : "-");
    } else if (window.LeanMarkHost) {
      var start = performance.now();
      window.LeanMarkHost.receive(data);
      if (data && data.type === "host") {
        nameTheSettingsButton();
      } else if (data && data.type === "document") {
        timeUntilDrawn("render", start);
      } else if (data && data.type === "zoom") {
        timeUntilDrawn("zoom", inputStart());
      }
    }
  }

  document.addEventListener("DOMContentLoaded", function () {
    send("timing|page-dom|" + Math.round(performance.now()));
    // The app confirms a theme by sending it back, which sets the same value
    // again; only real changes are timed.
    new MutationObserver(function (records) {
      records.forEach(function (record) {
        var name = record.attributeName === "data-theme" ? "theme" : "width";
        if (document.documentElement.getAttribute(record.attributeName) !== record.oldValue) {
          timeUntilDrawn(name, inputStart());
        }
      });
    }).observe(document.documentElement, {
      attributes: true,
      attributeOldValue: true,
      attributeFilter: ["data-theme", "data-width"]
    });
    var count = document.getElementById("findCount");
    if (count) {
      new MutationObserver(function () {
        if (findStart) {
          timeUntilDrawn("find", findStart);
          findStart = 0;
        }
      }).observe(count, { childList: true, characterData: true, subtree: true });
    }
  });

  window.addEventListener("load", function () {
    send("timing|page-load|" + Math.round(performance.now()));
  });

  window.addEventListener("resize", function () {
    timeUntilDrawn("resize", performance.now());
  });

  // The app posts exactly one port, right after the page loads. The page has
  // no frames and runs no document scripts, so nothing else can post here.
  window.addEventListener("message", function (event) {
    if (port || event.data !== "leanmark-port" || !event.ports ||
        event.ports.length !== 1) {
      return;
    }
    port = event.ports[0];
    port.onmessage = receive;
    queue.splice(0).forEach(function (message) {
      port.postMessage(message);
    });
  });

  // reader.js marks each finished render on the root element. The app uses the
  // first one to report when the document is on screen.
  new MutationObserver(function () {
    var state = document.documentElement.dataset.renderState;
    if (state) {
      send("render|" + state);
    }
  }).observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["data-render-state"]
  });

  // reader.css hides the theme button below 760 px, where desktop windows
  // rarely go. On a phone that is the only place to choose a theme, so the
  // button stays visible. An inline style wins over the stylesheet's rule.
  document.addEventListener("DOMContentLoaded", function () {
    var themeButton = document.getElementById("themeButton");
    if (themeButton) {
      themeButton.style.display = "inline-flex";
    }
  });

  // The settings panel lists keyboard shortcuts on the desktop. Phones have
  // no keyboard, so the Android panel holds only theme, text width and zoom,
  // and the button says so. A hardware keyboard's shortcuts still work.
  function nameTheSettingsButton() {
    var button = document.getElementById("settingsButton");
    if (button) {
      button.title = "Settings";
      button.setAttribute("aria-label", "Settings");
    }
  }
  document.addEventListener("DOMContentLoaded", nameTheSettingsButton);

  // Below 560 px the desktop stylesheet hides the file name but keeps the
  // find field at its natural width and an empty 52 px match count, so the
  // buttons ran past the right edge of a phone. These rules give the find
  // field the room that is left, and keep every control in one row on
  // screen down to 320 px: the theme button shows only its dot, which names
  // the theme for screen readers through the button's label.
  //
  // On a phone the settings panel becomes a sheet at the bottom of the
  // screen instead of a full-height panel that covers the document, and the
  // document keeps its width behind it. Wider screens keep the desktop panel
  // beside the document.
  var phoneStyle = document.createElement("style");
  phoneStyle.textContent =
    ".settings-panel > h3.settings-label, #shortcutList { display: none; }" +
    "@media (max-width: 560px) {" +
    " .topbar { padding-right: 12px; padding-left: 12px; }" +
    " .identity { display: none; }" +
    " .toolbar { flex: 1 1 auto; min-width: 0; flex-wrap: nowrap; gap: 4px; }" +
    " .search-field { flex: 1 1 0; min-width: 96px; }" +
    " #findCount:empty { min-width: 0; padding-right: 0; }" +
    " .toolbar .button { flex: 0 0 auto; padding-right: 10px; padding-left: 10px; }" +
    " .toolbar #themeButton { width: 40px; padding: 0; gap: 0; }" +
    " #themeLabel { display: none; }" +
    " .theme-button .theme-swatch { width: 14px; height: 14px; }" +
    " .settings-panel { top: auto; right: 0; bottom: 0; left: 0; width: auto;" +
    "  max-height: 75vh; padding-top: 20px; border-top: 1px solid var(--border);" +
    "  border-left: 0; border-radius: 16px 16px 0 0;" +
    "  box-shadow: 0 -8px 24px rgba(0, 0, 0, 0.2); }" +
    " .settings-panel::before { content: ''; position: absolute; top: 8px; left: 50%;" +
    "  width: 32px; height: 4px; margin-left: -16px; border-radius: 2px;" +
    "  background: var(--border); }" +
    " .settings-panel fieldset.settings-group { display: flex; flex-wrap: wrap; gap: 0 20px; }" +
    " .settings-panel .settings-group legend { width: 100%; }" +
    " .settings-panel .settings-choice { min-height: 44px; }" +
    " :root[data-settings-open] .content-grid," +
    " :root[data-settings-open] .state-content { margin-right: 0; }" +
    "}" +
    "@media (max-width: 360px) {" +
    " .topbar { padding-right: 8px; padding-left: 8px; }" +
    "}";
  document.head.appendChild(phoneStyle);

  // The app needs to know when the figure viewer or the settings sheet is
  // open, so that back closes it instead of leaving the app.
  function reportOpen(id, name) {
    var element = document.getElementById(id);
    if (!element) {
      return;
    }
    new MutationObserver(function () {
      send(name + (element.hidden ? "|closed" : "|open"));
    }).observe(element, { attributes: true, attributeFilter: ["hidden"] });
  }
  document.addEventListener("DOMContentLoaded", function () {
    reportOpen("figureViewer", "viewer");
    reportOpen("settingsPanel", "settings");
  });
}());
