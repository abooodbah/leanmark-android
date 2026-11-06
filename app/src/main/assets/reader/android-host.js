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
  // else an open sheet.
  function goBack() {
    var viewer = document.getElementById("figureViewer");
    if (viewer && !viewer.hidden) {
      pressKey("Escape");
    } else if (settingsOpen()) {
      document.getElementById("settingsClose").click();
    } else if (outlineSheet) {
      outlineSheet.setOpen(false);
    }
  }

  function settingsOpen() {
    var panel = document.getElementById("settingsPanel");
    return !!panel && !panel.hidden;
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

  // Work started by the user less than five seconds ago, else nothing. The
  // page can be busy for over a second between the key and the change on a
  // large document, as when it lays the document out again.
  function inputStart() {
    return performance.now() - lastInput < 5000 ? lastInput : 0;
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
      // Read before the page handles the message: a zoom lays the whole
      // document out again before receive returns.
      var start = performance.now();
      var userStart = inputStart();
      window.LeanMarkHost.receive(data);
      if (data && data.type === "host") {
        nameTheSettingsButton();
      } else if (data && data.type === "document") {
        timeUntilDrawn("render", start);
      } else if (data && data.type === "zoom") {
        timeUntilDrawn("zoom", userStart);
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

  // When the phone switches between light and dark, the page's colours
  // change at once through CSS, and reader.js then draws every diagram again,
  // which can hold the next frame for seconds on a long document. Its
  // handler runs after the next frame instead, so the new colours are on
  // screen first. android-host.js runs before reader.js, so this wraps the
  // list reader.js gets from matchMedia.
  var realMatchMedia = window.matchMedia.bind(window);
  window.matchMedia = function (query) {
    var list = realMatchMedia(query);
    if (query !== "(prefers-color-scheme: dark)" || !list.addEventListener) {
      return list;
    }
    var add = list.addEventListener.bind(list);
    list.addEventListener = function (type, listener, options) {
      if (type !== "change") {
        return add(type, listener, options);
      }
      return add(type, function (event) {
        requestAnimationFrame(function () {
          setTimeout(function () {
            listener.call(list, event);
          }, 0);
        });
      }, options);
    };
    return list;
  };

  // A light or dark switch of the phone, seen by the page: timed to the
  // first frame in the new colours. Diagrams are drawn again after that, as
  // they are when Android recreates the activity, so they are not waited for.
  var colourScheme = realMatchMedia("(prefers-color-scheme: dark)");
  var onScheme = function () {
    var start = performance.now();
    requestAnimationFrame(function () {
      requestAnimationFrame(function () {
        send("timing|scheme|" + Math.round(performance.now() - start));
      });
      // And until the diagrams are drawn again: reader.js's handler runs in
      // a task queued from this frame, so look after it.
      setTimeout(function () {
        setTimeout(function () {
          timeUntilDrawn("scheme-diagrams", start);
        }, 0);
      }, 0);
    });
  };
  if (colourScheme.addEventListener) {
    colourScheme.addEventListener("change", onScheme);
  } else {
    colourScheme.addListener(onScheme);
  }

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
    outlineSheet = setUpOutlineSheet();
    reportOpen("figureViewer", "viewer");
    reportOpen("settingsPanel", "settings");
    reportOpen("outlineSheet", "outline");
  });

  // "On this page" on a phone. The desktop shows the outline beside the
  // document only from 1180 px wide, so a phone shows none. Here a round
  // button in the bottom corner opens a copy of the outline's entries as a
  // sheet. The copy follows the outline's current entry (aria-current) and
  // keeps it in view, and a tap on an entry is handed to the outline's own
  // link, so the page moves as it does on the desktop. Only one sheet is
  // open at a time.
  var outlineSheet = null;
  var svgNamespace = "http://www.w3.org/2000/svg";

  function icon(path) {
    var svg = document.createElementNS(svgNamespace, "svg");
    svg.setAttribute("viewBox", "0 0 16 16");
    svg.setAttribute("aria-hidden", "true");
    var shape = document.createElementNS(svgNamespace, "path");
    shape.setAttribute("d", path);
    svg.appendChild(shape);
    return svg;
  }

  function setUpOutlineSheet() {
    var outline = document.getElementById("outline");
    var list = document.getElementById("outlineList");
    var pane = document.getElementById("readerPane");
    var panel = document.getElementById("settingsPanel");
    if (!outline || !list || !pane) {
      return null;
    }
    var button = document.createElement("button");
    button.id = "outlineButton";
    button.type = "button";
    button.className = "outline-button";
    button.setAttribute("aria-label", "On this page");
    button.setAttribute("aria-haspopup", "dialog");
    button.setAttribute("aria-expanded", "false");
    button.setAttribute("aria-controls", "outlineSheet");
    button.appendChild(icon("M2 4h12M2 8h12M2 12h8"));

    var sheet = document.createElement("nav");
    sheet.id = "outlineSheet";
    sheet.className = "outline outline-sheet";
    sheet.setAttribute("aria-labelledby", "outlineSheetTitle");
    sheet.hidden = true;
    var header = document.createElement("div");
    header.className = "outline-sheet-header";
    var title = document.createElement("span");
    title.id = "outlineSheetTitle";
    title.className = "outline-label";
    title.textContent = "On this page";
    var close = document.createElement("button");
    close.type = "button";
    close.className = "viewer-button";
    close.setAttribute("aria-label", "Close");
    close.appendChild(icon("M4 4l8 8M12 4l-8 8"));
    header.appendChild(title);
    header.appendChild(close);
    var copy = document.createElement("ol");
    sheet.appendChild(header);
    sheet.appendChild(copy);
    document.body.appendChild(button);
    document.body.appendChild(sheet);

    // A third of the way down, as the desktop keeps its outline entry.
    function keepInView(link, always) {
      var top = link.offsetTop;
      var visible = top >= sheet.scrollTop &&
        top + link.offsetHeight <= sheet.scrollTop + sheet.clientHeight;
      if (always || !visible) {
        sheet.scrollTop = Math.max(0, top - sheet.clientHeight / 3);
      }
    }

    function syncCurrent(always) {
      var originals = list.querySelectorAll("a");
      var copies = copy.querySelectorAll("a");
      var current = null;
      for (var index = 0; index < copies.length; index += 1) {
        var value = originals[index] ? originals[index].getAttribute("aria-current") : null;
        if (value) {
          copies[index].setAttribute("aria-current", value);
          current = copies[index];
        } else {
          copies[index].removeAttribute("aria-current");
        }
      }
      if (current) {
        keepInView(current, always);
      }
    }

    function setOpen(open) {
      if (open === !sheet.hidden) {
        return;
      }
      if (open) {
        if (settingsOpen()) {
          document.getElementById("settingsClose").click();
        }
        var fresh = list.cloneNode(true);
        copy.replaceChildren.apply(copy, Array.prototype.slice.call(fresh.childNodes));
      }
      sheet.hidden = !open;
      button.setAttribute("aria-expanded", open ? "true" : "false");
      if (open) {
        syncCurrent(true);
      }
    }

    function update() {
      button.hidden = pane.hidden || outline.hidden;
      if (button.hidden) {
        setOpen(false);
      }
    }

    button.addEventListener("click", function () {
      setOpen(sheet.hidden);
    });
    close.addEventListener("click", function () {
      setOpen(false);
    });
    copy.addEventListener("click", function (event) {
      var link = event.target.closest ? event.target.closest("a") : null;
      if (!link) {
        return;
      }
      event.preventDefault();
      var index = Array.prototype.indexOf.call(copy.querySelectorAll("a"), link);
      var original = list.querySelectorAll("a")[index];
      setOpen(false);
      if (original) {
        original.click();
      }
    });
    new MutationObserver(function () {
      if (!sheet.hidden) {
        syncCurrent(false);
      }
    }).observe(list, { subtree: true, attributes: true, attributeFilter: ["aria-current"] });
    new MutationObserver(update).observe(outline, { attributes: true, attributeFilter: ["hidden"] });
    new MutationObserver(update).observe(pane, { attributes: true, attributeFilter: ["hidden"] });
    if (panel) {
      new MutationObserver(function () {
        if (!panel.hidden) {
          setOpen(false);
        }
      }).observe(panel, { attributes: true, attributeFilter: ["hidden"] });
    }
    update();
    return { setOpen: setOpen };
  }

  // The button and the sheet show only where the desktop shows no outline.
  // Entries are larger than on the desktop, for a finger.
  var outlineStyle = document.createElement("style");
  outlineStyle.textContent =
    ".outline-button, .outline-sheet { display: none; }" +
    ":root:not([data-layout='wide']) .outline-button:not([hidden]) {" +
    " display: flex; position: fixed; z-index: 25; right: 16px; bottom: 16px;" +
    " width: 48px; height: 48px; align-items: center; justify-content: center;" +
    " padding: 0; border: 1px solid var(--border); border-radius: 50%;" +
    " background: var(--surface); color: var(--text-primary);" +
    " box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2); cursor: pointer; }" +
    ".outline-button svg { width: 20px; height: 20px; fill: none;" +
    " stroke: currentColor; stroke-width: 1.6; stroke-linecap: round; }" +
    ":root:not([data-layout='wide']) .outline-sheet:not([hidden]) {" +
    " display: block; position: fixed; z-index: 30; top: auto; right: 0; bottom: 0;" +
    " left: 0; max-height: 60vh; overflow-y: auto; padding: 12px 16px 24px;" +
    " background: var(--surface); border-top: 1px solid var(--border);" +
    " border-radius: 16px 16px 0 0; box-shadow: 0 -8px 24px rgba(0, 0, 0, 0.2); }" +
    ".outline-sheet-header { position: sticky; z-index: 1; top: -12px;" +
    " display: flex; align-items: center; justify-content: space-between;" +
    " margin: -12px 0 0; padding: 12px 0 8px; background: var(--surface); }" +
    ".outline-sheet-header .outline-label { margin: 0; }" +
    ".outline-sheet a { padding-top: 11px; padding-bottom: 11px; font-size: 15px; }";
  document.head.appendChild(outlineStyle);
}());
